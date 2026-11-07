<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import { updateMemberDisplayName } from '@/api/memberApi'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import type { MemberProfileResponse } from '@/features/member/types/member'

const props = defineProps<{
  profile: MemberProfileResponse
}>()

const emit = defineEmits<{
  updated: []
}>()

const displayName = ref(props.profile.displayName)
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
let active = true

watch(
    () => props.profile.displayName,
    (value) => {
      displayName.value = value
    },
)

async function submit() {
  if (submitting.value) return

  errorMessage.value = ''
  successMessage.value = ''

  const normalizedName = displayName.value.trim()

  if (normalizedName.length === 0 || normalizedName.length > 10) {
    errorMessage.value = '닉네임은 앞뒤 공백을 제외하고 1자 이상 10자 이하이어야 합니다.'
    return
  }

  if (normalizedName === props.profile.displayName) {
    errorMessage.value = '현재 닉네임과 같습니다.'
    return
  }

  submitting.value = true

  try {
    await updateMemberDisplayName({ displayName: normalizedName })
    if (!active) return

    successMessage.value = '닉네임이 변경되었습니다.'
    emit('updated')
  } catch (error) {
    if (!active) return

    if (error instanceof ApiError) {
      if (error.status === 409) {
        errorMessage.value = '이미 사용 중인 닉네임입니다.'
      } else if (error.status === 400) {
        errorMessage.value = '닉네임은 앞뒤 공백을 제외하고 1자 이상 10자 이하이어야 합니다.'
      } else if (error.status === 401) {
        errorMessage.value = '다시 로그인해 주세요.'
      } else {
        errorMessage.value = error.message
      }
    } else {
      errorMessage.value = '변경 결과를 확인하지 못했습니다. 새로고침해 확인해 주세요.'
    }
  } finally {
    if (active) submitting.value = false
  }
}

onBeforeUnmount(() => {
  active = false
})
</script>

<template>
  <section class="overflow-hidden surface-panel">
    <header class="border-b border-zinc-200 px-5 py-4">
      <h2 class="text-base font-medium text-zinc-900">닉네임 변경</h2>
    </header>

    <form class="grid gap-4 p-5" :aria-busy="submitting" @submit.prevent="submit">
      <div class="max-w-sm">
        <BaseInput
            id="profile-display-name"
            v-model="displayName"
            label="새 닉네임"
            placeholder="10자 이하"
            hint="앞뒤 공백을 제외하며, 다른 회원과 중복될 수 없습니다."
            :disabled="submitting"
            required
        />
      </div>

      <p
          v-if="errorMessage"
          role="alert"
          class="rounded-none border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
      >
        {{ errorMessage }}
      </p>

      <p
          v-if="successMessage"
          role="status"
          class="rounded-none border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700"
      >
        {{ successMessage }}
      </p>

      <div class="flex justify-end">
        <BaseButton type="submit" :disabled="submitting">
          {{ submitting ? '변경 중...' : '닉네임 변경' }}
        </BaseButton>
      </div>
    </form>
  </section>
</template>