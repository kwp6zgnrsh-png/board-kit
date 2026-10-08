<script setup lang="ts">
import BoardSettingsForm from '@/features/board/components/BoardSettingsForm.vue'
</script>

<template>
  <div class="grid gap-6">
    <header class="flex items-start justify-between gap-4">
      <div>
        <RouterLink :to="{ name: 'admin-dashboard' }" class="text-xs text-slate-500 hover:text-blue-600">게시판 관리 ›</RouterLink>
        <h1 class="mt-2 text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">새 게시판 만들기</h1>
        <p class="mt-2 text-sm leading-6 text-slate-500">게시판 정보와 정책을 설정하면 바로 사용할 수 있습니다.</p>
      </div>
      <span class="shrink-0 rounded-md bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-600">관리자</span>
    </header>
    <BoardSettingsForm />
  </div>
</template>
