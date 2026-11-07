<script setup lang="ts">
import BoardSettingsForm from '@/features/board/components/BoardSettingsForm.vue'
</script>

<template>
  <div class="grid gap-8">
    <header class="page-heading">
      <div>
        <RouterLink :to="{ name: 'admin-dashboard' }" class="text-xs text-zinc-500 hover:text-primary">게시판 관리 ›</RouterLink>
        <h1 class="page-title">새 게시판 만들기</h1>
        <p class="mt-2 text-sm leading-6 text-zinc-500">게시판 정보와 정책을 설정하면 바로 사용할 수 있습니다.</p>
      </div>
      <span class="shrink-0 rounded-none bg-accent px-2.5 py-1 text-xs font-medium text-primary">관리자</span>
    </header>
    <BoardSettingsForm />
  </div>
</template>
