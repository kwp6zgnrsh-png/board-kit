<script setup lang="ts">
import {ref, useId} from 'vue'

import BaseButton from '@/components/ui/BaseButton.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import BaseTextarea from '@/components/ui/BaseTextarea.vue'
import type {
  ReportCreateRequest,
  ReportReason,
} from '@/features/report/types/report'

const props = withDefaults(defineProps<{
  submitting?: boolean
  errorMessage?: string
}>(), {
  submitting: false,
  errorMessage: '',
})

const emit = defineEmits<{
  submit: [request: ReportCreateRequest]
  cancel: []
}>()

const formId = useId()
const reason = ref<ReportReason>('SPAM')
const detail = ref('')
const validationError = ref('')

const reasonOptions = [
  {value: 'SPAM', label: '스팸·광고'},
  {value: 'ABUSE', label: '욕설·비방'},
  {value: 'OTHER', label: '기타'},
] as const

function changeReason(value: string) {
  if (value !== 'SPAM' && value !== 'ABUSE' && value !== 'OTHER') return

  reason.value = value
  validationError.value = ''
}

function submit() {
  if (props.submitting) return

  validationError.value = ''

  if (reason.value === 'OTHER' && !detail.value.trim()) {
    validationError.value = '기타 사유를 선택하면 상세 사유를 입력해 주세요.'
    return
  }

  if (detail.value.length > 2000) {
    validationError.value = '상세 사유는 2,000자 이하여야 합니다.'
    return
  }

  emit('submit', {
    reason: reason.value,
    detail: detail.value === '' ? null : detail.value,
  })
}
</script>

<template>
  <form
      class="grid gap-4 rounded-lg border border-zinc-200 bg-zinc-50 p-4"
      :aria-busy="submitting"
      :aria-labelledby="`${formId}-heading`"
      @submit.prevent="submit"
  >
    <div>
      <h3
          :id="`${formId}-heading`"
          class="text-sm font-semibold text-zinc-900"
      >
        신고하기
      </h3>
      <p class="mt-1 text-xs leading-5 text-zinc-500">
        신고 사유를 선택해 주세요. 관리자가 내용을 확인합니다.
      </p>
    </div>

    <fieldset
        class="m-0 grid min-w-0 gap-4 border-0 p-0"
        :disabled="submitting"
    >
      <BaseSelect
          :id="`${formId}-reason`"
          label="신고 사유"
          :model-value="reason"
          :options="reasonOptions"
          @update:model-value="changeReason"
      />

      <BaseTextarea
          :id="`${formId}-detail`"
          v-model="detail"
          :label="reason === 'OTHER' ? '상세 사유 (필수)' : '상세 사유 (선택)'"
          placeholder="문제가 되는 내용을 설명해 주세요."
          :rows="4"
          :max-length="2000"
      />
    </fieldset>

    <p
        v-if="validationError || errorMessage"
        role="alert"
        class="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
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
        취소
      </BaseButton>

      <BaseButton
          type="submit"
          size="sm"
          :disabled="submitting"
      >
        {{ submitting ? '등록 중...' : '신고 등록' }}
      </BaseButton>
    </div>
  </form>
</template>