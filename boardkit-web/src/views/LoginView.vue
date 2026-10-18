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
  <section class="mx-auto grid w-full max-w-md justify-items-center gap-6 py-8 sm:py-12">
    <header class="text-center">
      <h1 class="text-2xl font-bold tracking-tight text-slate-900">로그인</h1>
      <p class="mt-2 text-sm text-slate-500">로그인하고 커뮤니티에 참여하세요.</p>
    </header>
    <p v-if="registrationCompleted"
       class="w-full rounded-md border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-700" role="status">
      회원가입이 완료되었습니다. 로그인해주세요.</p>
    <p
        v-if="passwordChanged"
        role="status"
        class="w-full rounded-md border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-700"
    >
      비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요.
    </p>
    <LoginForm/>
    <p class="text-sm text-slate-500">아직 계정이 없나요?
      <RouterLink :to="{ name: 'member-create' }" class="ml-1 font-medium text-blue-600 hover:underline">회원가입
      </RouterLink>
    </p>
  </section>
</template>
