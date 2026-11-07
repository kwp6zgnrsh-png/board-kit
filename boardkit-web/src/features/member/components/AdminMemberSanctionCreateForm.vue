<script setup lang="ts">
import {ref, useId, watch} from 'vue'

import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCheckbox from '@/components/ui/BaseCheckbox.vue'
import BaseTextarea from '@/components/ui/BaseTextarea.vue'
import type {
  AdminMemberSummaryResponse,
  MemberSanctionCreateRequest,
} from '@/features/member/types/adminMemberSanction'

const props = withDefaults(defineProps<{
  member: AdminMemberSummaryResponse
  submitting?: boolean
  errorMessage?: string
}>(), {
  submitting: false,
  errorMessage: '',
})

const emit = defineEmits<{
  submit: [request: MemberSanctionCreateRequest]
  cancel: []
}>()

const formId = useId()
const reason = ref('')
const permanent = ref(false)
const endDateTime = ref('')
const validationError = ref('')

function submit() {
  if (props.submitting) return

  validationError.value = ''

  const normalizedReason = reason.value.trim()

  if (!normalizedReason) {
    validationError.value = '제재 사유를 입력해 주세요.'
    return
  }

  if (normalizedReason.length > 2000) {
    validationError.value = '제재 사유는 2,000자 이하여야 합니다.'
    return
  }

  let endsAt: string | null = null

  if (!permanent.value) {
    const endDate = new Date(endDateTime.value)
    const timestamp = endDate.getTime()

    if (!Number.isFinite(timestamp)) {
      validationError.value = '제재 종료 일시를 입력해 주세요.'
      return
    }

    if (timestamp <= Date.now()) {
      validationError.value = '제재 종료 일시는 현재보다 미래여야 합니다.'
      return
    }

    endsAt = endDate.toISOString()
  }

  emit('submit', {
    memberId: props.member.memberId,
    reason: normalizedReason,
    endsAt,
  })
}

watch(() => props.member.memberId, () => {
  reason.value = ''
  permanent.value = false
  endDateTime.value = ''
  validationError.value = ''
})
</script>

<template>
  <section
      class="overflow-hidden surface-panel"
      :aria-labelledby="`${formId}-heading`"
  >
    <header class="border-b border-zinc-200 px-5 py-4">
      <h2 :id="`${formId}-heading`" class="text-base font-medium text-zinc-900">
        회원 제재 등록
      </h2>
      <p class="mt-1 text-sm text-zinc-500">
        등록 즉시 제재가 시작됩니다.
      </p>
    </header>

    <form
        class="grid gap-4 p-5"
        :aria-busy="submitting"
        @submit.prevent="submit"
    >
      <div class="rounded-none border border-zinc-200 bg-zinc-50 px-4 py-3">
        <p class="font-medium text-zinc-900">
          {{ member.displayName }}
        </p>
        <p class="mt-1 break-all text-sm text-zinc-500">
          로그인 ID: {{ member.loginId }}
        </p>
      </div>

      <p
          v-if="member.sanctioned"
          class="rounded-none border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800"
      >
        제재 중인 것으로 조회된 회원입니다.
        기존 제재가 유효하면 해제 후 새로 등록할 수 있습니다.
      </p>

      <fieldset
          class="m-0 grid min-w-0 gap-4 border-0 p-0"
          :disabled="submitting"
      >
        <BaseCheckbox
            v-model="permanent"
            label="영구 제재"
            description="종료 일시 없이 제재를 유지합니다."
        />

        <label
            v-if="!permanent"
            :for="`${formId}-ends-at`"
            class="grid max-w-sm gap-2"
        >
          <span class="text-sm font-medium text-zinc-700">
            종료 일시
            <span aria-hidden="true" class="ml-0.5 text-zinc-500">*</span>
          </span>
          <input
              :id="`${formId}-ends-at`"
              v-model="endDateTime"
              type="datetime-local"
              required
              class="h-10 w-full min-w-0 rounded-none border border-zinc-200 bg-white px-3 text-sm text-zinc-800 outline-none focus:border-ring focus:ring-2 focus:ring-ring/20 disabled:bg-zinc-50 disabled:text-zinc-400"
          />
          <small class="text-xs text-zinc-500">
            현재 기기의 현지 시각을 기준으로 입력하세요.
          </small>
        </label>

        <BaseTextarea
            :id="`${formId}-reason`"
            v-model="reason"
            label="제재 사유 (필수)"
            placeholder="제재 사유를 구체적으로 입력하세요."
            :rows="4"
            :max-length="2000"
        />
      </fieldset>

      <p
          v-if="validationError || errorMessage"
          role="alert"
          class="rounded-none border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
      >
        {{ validationError || errorMessage }}
      </p>

      <div class="flex justify-end gap-2">
        <BaseButton
            variant="secondary"
            size="sm"
            :disabled="submitting"
            @click="emit('cancel')"
        >
          선택 해제
        </BaseButton>

        <BaseButton type="submit" size="sm" :disabled="submitting">
          {{ submitting ? '등록 중...' : '제재 등록' }}
        </BaseButton>
      </div>
    </form>
  </section>
</template>