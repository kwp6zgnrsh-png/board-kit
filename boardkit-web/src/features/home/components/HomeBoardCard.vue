<script setup lang="ts">
import CommunityIcon from '@/components/ui/CommunityIcon.vue'
import type { HomeBoardPreview } from '@/features/home/types/home'
import PostSummaryLink from '@/features/post/components/PostSummaryLink.vue'

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
  <article class="flex min-w-0 flex-col border border-border bg-card">
    <header class="flex items-center justify-between gap-3 border-b border-border px-5 py-6">
      <div class="flex min-w-0 flex-1 flex-wrap items-baseline gap-x-3 gap-y-1">
        <RouterLink :to="{ name: 'board-detail', params: { boardSlug: preview.board.slug } }" class="flex min-w-0 max-w-full items-center gap-2 font-semibold text-zinc-900 hover:text-primary">
          <span class="font-mono text-[10px] font-normal text-zinc-400">{{ String(index + 1).padStart(2, '0') }}</span><h2 class="truncate text-lg font-medium tracking-tight">{{ preview.board.name }}</h2>
        </RouterLink>
        <p v-if="preview.board.description" class="min-w-0 flex-1 basis-24 truncate text-xs text-zinc-500">{{ preview.board.description }}</p>
      </div>
      <RouterLink :to="{ name: 'board-detail', params: { boardSlug: preview.board.slug } }"
        :aria-label="`${preview.board.name} 더보기`" class="shrink-0 text-xs text-zinc-500 hover:text-primary">더보기 →</RouterLink>
    </header>
    <ol v-if="preview.postPage.posts.length" class="flex-1 divide-y divide-zinc-100">
      <li v-for="post in preview.postPage.posts" :key="post.id">
        <PostSummaryLink
            :board-slug="preview.board.slug"
            :post="post"
            class="flex min-w-0 items-center gap-2 px-5 py-3.5 text-sm text-zinc-700 transition-colors hover:bg-muted/60 hover:text-primary"
        >
          <CommunityIcon v-if="post.secret" name="lock" class="size-3.5 text-zinc-400" aria-label="비밀글" role="img" :aria-hidden="false" />
          <span class="truncate">{{ post.title }}</span>
          <span
              v-if="post.blinded"
              class="shrink-0 rounded-none bg-amber-50 px-1.5 py-0.5 text-[10px] text-amber-700"
          >
            블라인드
          </span>
          <span v-if="post.commentCount" class="shrink-0 text-xs text-zinc-500">[{{ post.commentCount }}]</span>
          <time :datetime="post.createdAt" class="ml-auto shrink-0 text-xs tabular-nums text-zinc-400">{{ formatDate(post.createdAt) }}</time>
          <template #blocked>
          <span class="truncate text-zinc-500">
            블라인드된 게시글입니다.
          </span>
            <time
                :datetime="post.createdAt"
                class="ml-auto shrink-0 text-xs tabular-nums text-zinc-400"
            >
              {{ formatDate(post.createdAt) }}
            </time>
          </template>
        </PostSummaryLink>
      </li>
    </ol>
    <p v-else class="px-4 py-12 text-center text-sm text-zinc-400">등록된 게시글이 없습니다.</p>
  </article>
</template>
