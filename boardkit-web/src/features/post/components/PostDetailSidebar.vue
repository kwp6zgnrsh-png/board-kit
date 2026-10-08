<script setup lang="ts">
import { useRoute } from 'vue-router'

defineProps<{
  boardName: string
  boardSlug: string
  authorDisplayName: string
}>()

const route = useRoute()
</script>

<template>
  <aside class="grid min-w-0 content-start gap-4" aria-label="게시글 부가 정보">
    <section class="rounded-md border border-slate-200 bg-white p-4">
      <h2 class="mb-4 text-xs font-medium text-slate-500">작성자</h2>
      <div class="flex min-w-0 items-center gap-3">
        <span class="grid size-10 shrink-0 place-items-center rounded-full bg-blue-50 text-base font-medium text-blue-600" aria-hidden="true">{{ authorDisplayName.slice(0, 1) }}</span>
        <strong class="min-w-0 break-words text-sm font-medium text-slate-800">{{ authorDisplayName }}</strong>
      </div>
    </section>
    <RouterLink :to="{ name: 'board-detail', params: { boardSlug }, query: route.query }"
      class="rounded-md border border-slate-200 bg-slate-50 px-4 py-3 text-xs leading-5 text-slate-600 hover:text-blue-600">{{ boardName }} 더 보기 →</RouterLink>
  </aside>
</template>
