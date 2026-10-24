<script setup lang="ts">
import type { HomeRecentPost } from '@/features/home/types/home'
import PostSummaryLink from '@/features/post/components/PostSummaryLink.vue'

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
  <section class="overflow-hidden rounded-xl border border-border bg-card shadow-xs" aria-labelledby="recent-heading">
    <header class="border-b border-zinc-200 bg-zinc-50 px-4 py-3"><h2 id="recent-heading" class="text-sm font-semibold text-zinc-800">최근 올라온 글</h2></header>
    <ol v-if="posts.length" class="divide-y divide-zinc-100">
      <li v-for="{ board, post } in posts" :key="`${board.id}-${post.id}`">
        <PostSummaryLink
            :board-slug="board.slug"
            :post="post"
            class="flex min-w-0 items-center gap-3 px-4 py-2.5 text-sm text-zinc-700 hover:bg-muted/60 hover:text-primary"
        >
          <span class="hidden w-24 shrink-0 truncate text-xs text-zinc-400 sm:block">{{ board.name }}</span>
          <span class="truncate">{{ post.title }}</span>
          <span v-if="post.commentCount" class="shrink-0 text-xs text-red-500">[{{ post.commentCount }}]</span>
          <time :datetime="post.createdAt" class="ml-auto shrink-0 text-xs text-zinc-400">{{ formatDate(post.createdAt) }}</time>
          <template #blocked>
            <span class="hidden w-24 shrink-0 truncate text-xs text-zinc-400 sm:block">
              {{ board.name }}
            </span>
            <span class="truncate text-zinc-500">
              블라인드된 게시글입니다.
            </span>
            <time
                :datetime="post.createdAt"
                class="ml-auto shrink-0 text-xs text-zinc-400"
            >
              {{ formatDate(post.createdAt) }}
            </time>
          </template>
        </PostSummaryLink>
      </li>
    </ol>
    <p v-else class="py-8 text-center text-sm text-zinc-400">아직 등록된 글이 없습니다.</p>
  </section>
</template>
