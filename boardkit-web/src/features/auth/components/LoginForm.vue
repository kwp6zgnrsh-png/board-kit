<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { login } from '@/api/authApi'
import { ApiError } from '@/api/http'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import { saveAuthSession } from '@/features/auth/services/authSession'

const router = useRouter()
const route = useRoute()

const form = reactive({
  loginId: '',
  password: '',
})

const submitting = ref(false)
const errorMessage = ref('')

async function submit() {
  submitting.value = true
  errorMessage.value = ''

  try {
    const response = await login({
      loginId: form.loginId.trim(),
      password: form.password,
    })

    saveAuthSession(response)

    const redirect =
        typeof route.query.redirect === 'string' &&
        route.query.redirect.startsWith('/')
            ? route.query.redirect
            : '/'

    await router.replace(redirect)
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      errorMessage.value = '아이디 또는 비밀번호가 올바르지 않습니다.'
    } else {
      errorMessage.value =
          error instanceof Error ? error.message : '로그인에 실패했습니다.'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form class="login-form" @submit.prevent="submit">
    <BaseInput
        id="login-id"
        v-model="form.loginId"
        label="아이디"
        required
    />

    <BaseInput
        id="login-password"
        v-model="form.password"
        label="비밀번호"
        type="password"
        required
    />

    <p v-if="errorMessage" class="error-message" role="alert">
      {{ errorMessage }}
    </p>

    <BaseButton type="submit" :disabled="submitting">
      {{ submitting ? '로그인 중...' : '로그인' }}
    </BaseButton>
  </form>
</template>

<style scoped>
.login-form {
  display: grid;
  gap: 1rem;
  width: min(420px, 100%);
  padding: 1.5rem;
  border: 3px solid #0f172a;
  border-radius: 0.5rem;
  background: #fff;
}

.error-message {
  margin: 0;
  padding: 0.75rem 0.9rem;
  border: 2px solid #7f1d1d;
  border-radius: 0.35rem;
  color: #7f1d1d;
  background: #fee2e2;
  font-size: 0.8rem;
  font-weight: 800;
}

.login-form :deep(.field__label) {
  color: #0f172a;
  font-size: 0.8rem;
  font-weight: 900;
}

.login-form :deep(.field__control) {
  min-height: 2.75rem;
  border: 2px solid #0f172a;
  border-radius: 0.35rem;
  color: #0f172a;
  background: #f8fafc;
  font-size: 0.9rem;
  font-weight: 700;
}

.login-form :deep(.field__control:focus) {
  border-color: #0f172a;
  outline: 3px solid #f9a8d4;
  background: #fff;
}

.login-form :deep(.button.button--primary) {
  min-height: 2.8rem;
  border: 3px solid #0f172a;
  border-radius: 0.35rem;
  color: #0f172a;
  background: #f9a8d4;
  box-shadow: 2px 2px 0 #0f172a;
  font-size: 0.9rem;
  font-weight: 900;
  transition: transform 150ms ease, background 150ms ease;
}

.login-form :deep(.button.button--primary:hover:not(:disabled)) {
  transform: translateY(-2px);
  background: #f472b6;
}

@media (max-width: 640px) {
  .login-form {
    padding: 1.1rem;
  }
}
</style>
