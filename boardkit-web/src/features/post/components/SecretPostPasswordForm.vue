<script setup lang="ts">
import { ref } from 'vue'

import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'

withDefaults(
    defineProps<{
      submitting?: boolean
      errorMessage?: string
    }>(),
    {
      submitting: false,
      errorMessage: '',
    },
)

const emit = defineEmits<{
  submit: [password: string]
}>()

const password = ref('')
const validationMessage = ref('')

function submit() {
  validationMessage.value = ''

  if (!password.value.trim()) {
    validationMessage.value =
        '비밀글 비밀번호를 입력하세요.'
    return
  }

  emit('submit', password.value)
}
</script>

<template>
  <form
      class="grid w-full max-w-120 gap-5 surface-panel p-5 sm:p-8 "
      @submit.prevent="submit"
  >
    <div>
      <h2 class="m-0 text-2xl font-medium text-zinc-900">비밀글입니다</h2>
      <p class="mt-2 mb-0 text-zinc-600">게시글 비밀번호를 입력하세요.</p>
    </div>

    <BaseInput
        id="post-secret-password"
        v-model="password"
        label="비밀번호"
        type="password"
        required
    />

    <p
        v-if="validationMessage || errorMessage"
        class="m-0 rounded-none border border-red-200 bg-rose-50 px-4 py-3 font-medium text-red-900"
        role="alert"
    >
      {{ validationMessage || errorMessage }}
    </p>

    <BaseButton
        type="submit"
        :disabled="submitting"
    >
      {{ submitting ? '확인 중...' : '게시글 열기' }}
    </BaseButton>
  </form>
</template>
