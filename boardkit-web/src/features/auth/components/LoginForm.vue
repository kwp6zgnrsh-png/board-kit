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
  <form class="grid w-full gap-6" @submit.prevent="submit">
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

    <p v-if="errorMessage" class="rounded-none border border-red-200 bg-red-50 p-3 text-sm text-red-700" role="alert">
      {{ errorMessage }}
    </p>

    <BaseButton type="submit" :disabled="submitting">
      {{ submitting ? '로그인 중...' : '로그인' }}
    </BaseButton>
  </form>
</template>
