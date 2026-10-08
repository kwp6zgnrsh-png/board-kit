<script setup lang="ts">
import CommunityIcon from '@/components/ui/CommunityIcon.vue'
import type { HomeBoardPreview } from '@/features/home/types/home'

defineProps<{
  preview: HomeBoardPreview
  index: number
}>()

function formatDate(value: string): string {
  const date = value.slice(0, 10).split('-')

  return date.length === 3
    ? `${date[1]}-${date[2]}`
    : value
}
</script>

<template>
  <article class="min-w-0 overflow-hidden rounded-md border border-slate-200 bg-white">
    <header class="flex items-center justify-between gap-3 border-b border-slate-200 px-4 py-4">
      <div class="min-w-0">
        <RouterLink :to="{ name: 'board-detail', params: { boardSlug: preview.board.slug } }" class="flex items-center gap-2 font-semibold text-slate-900 hover:text-blue-600">
          <CommunityIcon name="message" class="size-4 text-blue-600" /><h2 class="truncate text-base">{{ preview.board.name }}</h2>
        </RouterLink>
        <p v-if="preview.board.description" class="mt-1 truncate text-xs text-slate-500">{{ preview.board.description }}</p>
      </div>
      <RouterLink :to="{ name: 'board-detail', params: { boardSlug: preview.board.slug } }"
        :aria-label="`${preview.board.name} 더보기`" class="shrink-0 text-xs text-slate-500 hover:text-blue-600">더보기 →</RouterLink>
    </header>
    <ol v-if="preview.postPage.posts.length" class="divide-y divide-slate-100">
      <li v-for="post in preview.postPage.posts" :key="post.id">
        <RouterLink :to="{ name: 'post-detail', params: { boardSlug: preview.board.slug, postId: post.id } }"
          class="flex min-w-0 items-center gap-2 px-4 py-2.5 text-sm text-slate-700 transition-colors hover:bg-blue-50/50 hover:text-blue-600">
          <CommunityIcon v-if="post.secret" name="lock" class="size-3.5 text-slate-400" aria-label="비밀글" role="img" :aria-hidden="false" />
          <span class="truncate">{{ post.title }}</span>
          <span v-if="post.commentCount" class="shrink-0 text-xs text-red-500">[{{ post.commentCount }}]</span>
          <time :datetime="post.createdAt" class="ml-auto shrink-0 text-xs tabular-nums text-slate-400">{{ formatDate(post.createdAt) }}</time>
        </RouterLink>
      </li>
    </ol>
    <p v-else class="px-4 py-12 text-center text-sm text-slate-400">첫 번째 글을 기다리고 있어요.</p>
    <footer class="border-t border-slate-100 px-4 py-2.5 text-xs text-slate-400">전체 {{ preview.postPage.totalElements.toLocaleString() }}개 · 최신 {{ preview.postPage.posts.length }}개 표시</footer>
  </article>
</template>
