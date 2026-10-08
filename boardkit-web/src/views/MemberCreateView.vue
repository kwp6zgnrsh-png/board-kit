<script setup lang="ts">
import MemberCreateForm from '@/features/member/components/MemberCreateForm.vue'
</script>

<template>
  <section class="mx-auto grid w-full max-w-md justify-items-center gap-6 py-6 sm:py-10">
    <header class="text-center">
      <h1 class="text-2xl font-bold tracking-tight text-slate-900">회원가입</h1>
      <p class="mt-2 text-sm text-slate-500">새로운 계정을 만들고 커뮤니티에 참여하세요.</p>
    </header>
    <MemberCreateForm />
    <p class="text-sm text-slate-500">이미 계정이 있나요? <RouterLink :to="{ name: 'login' }" class="ml-1 font-medium text-blue-600 hover:underline">로그인</RouterLink></p>
  </section>
</template>
