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
      class="password-form"
      @submit.prevent="submit"
  >
    <div>
      <h2>비밀글입니다</h2>
      <p>게시글 비밀번호를 입력하세요.</p>
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
        class="error-message"
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

<style scoped>
.password-form {
  display: grid;
  gap: 1rem;
  width: min(420px, 100%);
  padding: 1.5rem;
  border: 1px solid #e2e8f0;
  border-radius: 1rem;
  background: #fff;
}

.password-form h2,
.password-form p {
  margin: 0;
}

.password-form h2 {
  color: #0f172a;
}

.password-form > div p {
  margin-top: 0.4rem;
  color: #64748b;
}

.error-message {
  color: #dc2626;
}
</style>