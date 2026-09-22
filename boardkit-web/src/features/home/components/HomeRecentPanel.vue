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
  <aside class="overflow-hidden rounded-lg border-[3px] border-slate-950 bg-sky-200">
    <header class="flex items-center justify-between px-3 py-2">
      <div>
        <p class="m-0 text-[9px] font-black tracking-[0.18em] text-slate-600">WHAT'S NEW</p>
        <h2 class="m-0 text-base font-black text-slate-950">지금 새 글 🔥</h2>
      </div>
      <span class="text-xl" aria-hidden="true">↗</span>
    </header>

    <ol v-if="posts.length > 0" class="grid gap-0.5 px-2 pb-2">
      <li v-for="({ board, post }, index) in posts" :key="`${board.id}-${post.id}`">
        <RouterLink
          :to="{
            name: 'post-detail',
            params: { boardSlug: board.slug, postId: post.id },
          }"
          class="grid grid-cols-[22px_minmax(0,1fr)_auto] items-center gap-1.5 rounded border-2 border-slate-950 bg-white px-1.5 py-0.5 text-slate-950 no-underline transition hover:-translate-y-0.5 hover:bg-yellow-100"
        >
          <strong
            class="grid h-5 w-5 place-items-center border-2 border-slate-950 text-[9px] leading-none"
            :class="index === 0 ? 'bg-yellow-300' : index < 3 ? 'bg-emerald-300' : 'bg-white'"
          >
            {{ index + 1 }}
          </strong>
          <span class="min-w-0">
            <span class="block truncate text-[11px] font-black leading-tight">{{ post.title }}</span>
            <span class="hidden">{{ board.name }}</span>
          </span>
          <span class="text-[9px] font-bold text-slate-500">{{ formatDate(post.createdAt) }}</span>
        </RouterLink>
      </li>
    </ol>

    <p v-else class="mx-4 mb-4 rounded border-2 border-dashed border-slate-500 bg-white/70 px-3 py-8 text-center text-xs font-bold text-slate-500">
      아직 등록된 글이 없습니다.
    </p>
  </aside>
</template>
