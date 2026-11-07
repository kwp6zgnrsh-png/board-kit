<script setup lang="ts">
import {onBeforeUnmount, reactive, ref} from 'vue'

import {ApiError} from '@/api/http'
import {updateMemberPassword} from '@/api/memberApi'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'

const emit = defineEmits<{
  changed: []
}>()

const PASSWORD_PATTERN = /^[!-~]{12,64}$/

const form = reactive({
  currentPassword: '',
  newPassword: '',
  newPasswordConfirm: '',
})

const submitting = ref(false)
const errorMessage = ref('')
let active = true

function validateForm(): boolean {
  if (!form.currentPassword) {
    errorMessage.value = '현재 비밀번호를 입력해 주세요.'
    return false
  }

  if (!PASSWORD_PATTERN.test(form.newPassword)) {
    errorMessage.value =
        '새 비밀번호는 공백 없이 영문, 숫자, 특수문자로 12자 이상 64자 이하이어야 합니다.'
    return false
  }

  if (form.newPassword !== form.newPasswordConfirm) {
    errorMessage.value = '새 비밀번호와 비밀번호 확인이 일치하지 않습니다.'
    return false
  }

  if (form.currentPassword === form.newPassword) {
    errorMessage.value = '새 비밀번호는 현재 비밀번호와 달라야 합니다.'
    return false
  }

  return true
}

async function submit() {
  if (submitting.value) return

  errorMessage.value = ''
  if (!validateForm()) return

  submitting.value = true

  try {
    await updateMemberPassword({...form})
    if (!active) return

    clearPasswords()
    emit('changed')
  } catch (error) {
    if (!active) return

    if (error instanceof ApiError) {
      switch (error.status) {
        case 400:
          errorMessage.value =
              '현재 비밀번호를 확인해 주세요. 새 비밀번호는 현재 비밀번호와 달라야 합니다.'
          break
        case 401:
          errorMessage.value = '다시 로그인해 주세요.'
          break
        case 409:
          errorMessage.value =
              '회원 정보가 변경되었습니다. 다시 로그인한 후 시도해 주세요.'
          break
        default:
          errorMessage.value = error.message
      }
    } else {
      errorMessage.value =
          '변경 결과를 확인하지 못했습니다. 다시 로그인해 변경 여부를 확인해 주세요.'
    }
  } finally {
    if (active) submitting.value = false
  }
}

function clearPasswords() {
  form.currentPassword = ''
  form.newPassword = ''
  form.newPasswordConfirm = ''
}

onBeforeUnmount(() => {
  active = false
  clearPasswords()
})
</script>

<template>
  <section class="overflow-hidden surface-panel">
    <header class="border-b border-zinc-200 px-5 py-4">
      <h2 class="text-base font-medium text-zinc-900">비밀번호 변경</h2>
      <p class="mt-1 text-sm text-zinc-500">
        변경하면 모든 기기에서 로그아웃되며, 다시 로그인해야 합니다.
      </p>
    </header>

    <form class="grid gap-4 p-5" :aria-busy="submitting" @submit.prevent="submit">
      <div class="grid max-w-sm gap-4">
        <BaseInput
            id="profile-current-password"
            v-model="form.currentPassword"
            label="현재 비밀번호"
            type="password"
            :disabled="submitting"
            required
        />

        <BaseInput
            id="profile-new-password"
            v-model="form.newPassword"
            label="새 비밀번호"
            type="password"
            hint="공백 없이 영문, 숫자, 특수문자 12~64자"
            :disabled="submitting"
            required
        />

        <BaseInput
            id="profile-new-password-confirm"
            v-model="form.newPasswordConfirm"
            label="새 비밀번호 확인"
            type="password"
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

      <div class="flex justify-end">
        <BaseButton type="submit" :disabled="submitting">
          {{ submitting ? '변경 중...' : '비밀번호 변경' }}
        </BaseButton>
      </div>
    </form>
  </section>
</template>