<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import { createMember } from '@/api/memberApi'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import type { MemberCreateRequest } from '@/features/member/types/member'

const LOGIN_ID_PATTERN = /^[a-z0-9_]{4,20}$/
const PASSWORD_PATTERN = /^[!-~]{12,64}$/

const router = useRouter()

const form = reactive({
  loginId: '',
  displayName: '',
  password: '',
  passwordConfirm: '',
})

const submitting = ref(false)
const errorMessage = ref('')

function createRequest(): MemberCreateRequest | null {
  const loginId = form.loginId.trim()
  const displayName = form.displayName.trim()

  if (!LOGIN_ID_PATTERN.test(loginId)) {
    errorMessage.value = '아이디는 영문 소문자, 숫자, 밑줄로 4자 이상 20자 이하이어야 합니다.'
    return null
  }

  if (displayName.length === 0 || displayName.length > 10) {
    errorMessage.value = '닉네임은 앞뒤 공백을 제외하고 1자 이상 10자 이하이어야 합니다.'
    return null
  }

  if (!PASSWORD_PATTERN.test(form.password)) {
    errorMessage.value = '비밀번호는 공백 없이 영문, 숫자, 특수문자로 12자 이상 64자 이하이어야 합니다.'
    return null
  }

  if (form.password !== form.passwordConfirm) {
    errorMessage.value = '비밀번호와 비밀번호 확인이 일치하지 않습니다.'
    return null
  }

  return {
    loginId,
    password: form.password,
    passwordConfirm: form.passwordConfirm,
    displayName,
  }
}

async function submit() {
  errorMessage.value = ''

  const request = createRequest()

  if (!request) {
    return
  }

  submitting.value = true

  try {
    await createMember(request)

    await router.replace({
      name: 'login',
      query: {
        registered: 'true',
      },
    })
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      errorMessage.value = '이미 사용 중인 아이디 또는 닉네임입니다.'
    } else if (error instanceof ApiError && error.status === 400) {
      errorMessage.value = error.message || '회원가입 정보를 확인해주세요.'
    } else {
      errorMessage.value =
          error instanceof Error ? error.message : '회원가입에 실패했습니다.'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form class="grid w-full max-w-md gap-5 rounded-xl border border-border bg-card p-6 sm:p-8 shadow-xs" @submit.prevent="submit">
    <BaseInput
        id="member-login-id"
        v-model="form.loginId"
        label="아이디"
        placeholder="영문 소문자, 숫자, 밑줄 4~20자"
        hint="로그인할 때 사용하는 아이디입니다."
        required
    />

    <BaseInput
        id="member-display-name"
        v-model="form.displayName"
        label="닉네임"
        placeholder="10자 이하"
        hint="게시글과 댓글에 표시됩니다."
        required
    />

    <BaseInput
        id="member-password"
        v-model="form.password"
        label="비밀번호"
        type="password"
        placeholder="12~64자"
        hint="공백 없이 영문, 숫자, 특수문자를 사용할 수 있습니다."
        required
    />

    <BaseInput
        id="member-password-confirm"
        v-model="form.passwordConfirm"
        label="비밀번호 확인"
        type="password"
        placeholder="비밀번호를 다시 입력하세요"
        required
    />

    <p
        v-if="errorMessage"
        class="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700"
        role="alert"
    >
      {{ errorMessage }}
    </p>

    <BaseButton
        type="submit"
        :disabled="submitting"
    >
      {{ submitting ? '가입 중...' : '회원가입' }}
    </BaseButton>
  </form>
</template>
