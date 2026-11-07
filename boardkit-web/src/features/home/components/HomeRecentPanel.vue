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
  <section class="min-w-0" aria-labelledby="recent-heading">
    <header class="mb-4 flex items-baseline justify-between gap-4"><h2 id="recent-heading" class="text-lg font-medium tracking-tight text-zinc-800">최근 게시글</h2></header>
    <ol v-if="posts.length" class="divide-y divide-border border-y border-border">
      <li v-for="{ board, post } in posts" :key="`${board.id}-${post.id}`">
        <PostSummaryLink
            :board-slug="board.slug"
            :post="post"
            class="flex min-w-0 items-center gap-3 px-1 py-4 text-sm text-zinc-700 hover:bg-muted/60 hover:text-primary"
        >
          <span class="hidden w-24 shrink-0 truncate text-xs text-zinc-400 sm:block">{{ board.name }}</span>
          <span class="truncate">{{ post.title }}</span>
          <span v-if="post.commentCount" class="shrink-0 text-xs text-zinc-500">[{{ post.commentCount }}]</span>
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
