<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'

import LoginForm from '@/features/auth/components/LoginForm.vue'

const route = useRoute()

const registrationCompleted = computed(
    () => route.query.registered === 'true',
)

const passwordChanged = computed(
    () => route.query.passwordChanged === 'true',
)
</script>

<template>
  <section class="mx-auto grid w-full max-w-md gap-8 py-4 sm:py-8">
    <header class="grid gap-3 border-b border-border pb-7">
      <h1 class="page-title">로그인</h1>
      <p class="text-sm leading-7 text-zinc-500">계정 정보를 입력하세요.</p>
    </header>
    <p v-if="registrationCompleted"
       class="w-full rounded-none border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-700" role="status">
      회원가입이 완료되었습니다. 로그인해주세요.</p>
    <p
        v-if="passwordChanged"
        role="status"
        class="w-full rounded-none border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-700"
    >
      비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요.
    </p>
    <LoginForm/>
    <p class="border-t border-border pt-6 text-sm text-zinc-500">아직 계정이 없나요?
      <RouterLink :to="{ name: 'member-create' }" class="ml-1 font-medium text-primary hover:underline">회원가입
      </RouterLink>
    </p>
  </section>
</template>
