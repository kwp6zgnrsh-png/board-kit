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
      class="grid w-full max-w-120 gap-5 rounded-lg border-[3px] border-slate-950 bg-white p-5 shadow-[2px_3px_0_#0f172a] sm:p-8 [&_.field\_\_label]:font-black! [&_.field\_\_label]:text-slate-950! [&_.field\_\_control]:rounded-sm! [&_.field\_\_control]:border-2! [&_.field\_\_control]:border-slate-950! [&_.field\_\_control:focus]:border-blue-600!"
      @submit.prevent="submit"
  >
    <div>
      <h2 class="m-0 text-2xl font-black text-slate-950">비밀글입니다</h2>
      <p class="mt-2 mb-0 text-slate-600">게시글 비밀번호를 입력하세요.</p>
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
        class="m-0 rounded border-2 border-red-900 bg-rose-50 px-4 py-3 font-bold text-red-900"
        role="alert"
    >
      {{ validationMessage || errorMessage }}
    </p>

    <BaseButton
        type="submit"
        class="rounded! border-2! border-slate-950! bg-blue-500! font-black! shadow-[2px_2px_0_#0f172a] transition hover:-translate-y-0.5 hover:bg-blue-600!"
        :disabled="submitting"
    >
      {{ submitting ? '확인 중...' : '게시글 열기' }}
    </BaseButton>
  </form>
</template>
