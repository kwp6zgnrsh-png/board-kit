<script setup lang="ts">
import { computed } from 'vue'

import { getBoardTheme } from '@/features/board/services/boardTheme'
import type { HomeBoardPreview } from '@/features/home/types/home'

const props = defineProps<{
  preview: HomeBoardPreview
  index: number
}>()

const theme = computed(() => getBoardTheme(props.preview.board.slug))
const themeStyle = computed(() => ({
  '--board-accent': theme.value.accent,
}))

const icon = computed(() => ['💬', '❓', '💡', '🖼️', '📰', '📚', '💼', '📢'][props.index % 8])

function formatDate(value: string): string {
  const date = value.slice(0, 10).split('-')

  return date.length === 3
    ? `${date[1]}-${date[2]}`
    : value
}
</script>

<template>
  <article :style="themeStyle" class="min-w-0 overflow-hidden rounded-lg border-[3px] border-slate-950 bg-white">
    <header class="flex min-h-20 items-center gap-3 border-b-[3px] border-slate-950 bg-[var(--board-accent)] px-4 py-3">
      <span class="grid h-11 w-11 shrink-0 place-items-center rounded border-[3px] border-slate-950 bg-white text-xl" aria-hidden="true">
        {{ icon }}
      </span>

      <div class="min-w-0 flex-1">
        <h2 class="m-0 truncate text-lg font-black text-slate-950">{{ preview.board.name }}</h2>
        <p class="m-0 mt-0.5 truncate text-xs font-bold text-slate-700">
          {{ preview.board.description || '새로운 이야기를 나눠보세요.' }}
        </p>
      </div>

      <RouterLink
        :to="{ name: 'board-detail', params: { boardSlug: preview.board.slug } }"
        class="inline-flex h-9 shrink-0 items-center justify-center rounded border-2 border-slate-950 bg-white px-3 text-xs font-black leading-none text-slate-950 no-underline shadow-[2px_2px_0_#0f172a] transition hover:-translate-y-0.5"
      >
        더보기 →
      </RouterLink>
    </header>

    <ol v-if="preview.postPage.posts.length > 0" class="divide-y divide-slate-300">
      <li v-for="post in preview.postPage.posts" :key="post.id">
        <RouterLink
          :to="{
            name: 'post-detail',
            params: { boardSlug: preview.board.slug, postId: post.id },
          }"
          class="grid grid-cols-[28px_minmax(0,1fr)_58px] items-center gap-2 px-3 py-2 text-slate-950 no-underline transition hover:bg-slate-100"
        >
          <span class="text-center text-[10px] font-black text-slate-500">{{ post.id }}</span>
          <span class="flex min-w-0 items-center gap-1.5">
            <span v-if="post.secret" class="shrink-0 text-[10px]" aria-label="비밀글">🔒</span>
            <span class="truncate text-xs font-bold">{{ post.title }}</span>
          </span>
          <span class="text-right text-[10px] font-bold text-slate-500">{{ formatDate(post.createdAt) }}</span>
        </RouterLink>
      </li>
    </ol>

    <p v-else class="m-0 px-4 py-10 text-center text-xs font-bold text-slate-400">
      첫 번째 글을 기다리고 있어요.
    </p>

    <footer class="flex items-center justify-between border-t border-slate-300 bg-slate-50 px-4 py-2 text-[10px] font-bold text-slate-500">
      <span>전체 {{ preview.postPage.totalElements.toLocaleString() }}개</span>
      <span>최신 {{ preview.postPage.posts.length }}개 표시</span>
    </footer>
  </article>
</template>
