<script setup lang="ts">
import MemberCreateForm from '@/features/member/components/MemberCreateForm.vue'
</script>

<template>
  <section class="mx-auto grid w-full max-w-md gap-8 py-4 sm:py-8">
    <header class="grid gap-3 border-b border-border pb-7">
      <h1 class="page-title">회원가입</h1>
      <p class="text-sm leading-7 text-zinc-500">가입 정보를 입력하세요.</p>
    </header>
    <MemberCreateForm />
    <p class="border-t border-border pt-6 text-sm text-zinc-500">이미 계정이 있나요? <RouterLink :to="{ name: 'login' }" class="ml-1 font-medium text-primary hover:underline">로그인</RouterLink></p>
  </section>
</template>
