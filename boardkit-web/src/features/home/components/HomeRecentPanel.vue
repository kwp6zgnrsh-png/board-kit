<script setup lang="ts">
import type { HomeRecentPost } from '@/features/home/types/home'

defineProps<{
  posts: HomeRecentPost[]
}>()

function formatDate(value: string): string {
  const date = value.slice(0, 10).split('-')

  return date.length === 3
    ? `${date[1]}-${date[2]}`
    : value
}
</script>

<template>
  <section class="overflow-hidden rounded-md border border-slate-200 bg-white" aria-labelledby="recent-heading">
    <header class="border-b border-slate-200 bg-slate-50 px-4 py-3"><h2 id="recent-heading" class="text-sm font-semibold text-slate-800">최근 올라온 글</h2></header>
    <ol v-if="posts.length" class="divide-y divide-slate-100">
      <li v-for="{ board, post } in posts" :key="`${board.id}-${post.id}`">
        <RouterLink :to="{ name: 'post-detail', params: { boardSlug: board.slug, postId: post.id } }"
          class="flex min-w-0 items-center gap-3 px-4 py-2.5 text-sm text-slate-700 hover:bg-blue-50/50 hover:text-blue-600">
          <span class="hidden w-24 shrink-0 truncate text-xs text-slate-400 sm:block">{{ board.name }}</span>
          <span class="truncate">{{ post.title }}</span>
          <span v-if="post.commentCount" class="shrink-0 text-xs text-red-500">[{{ post.commentCount }}]</span>
          <time :datetime="post.createdAt" class="ml-auto shrink-0 text-xs text-slate-400">{{ formatDate(post.createdAt) }}</time>
        </RouterLink>
      </li>
    </ol>
    <p v-else class="py-8 text-center text-sm text-slate-400">아직 등록된 글이 없습니다.</p>
  </section>
</template>
