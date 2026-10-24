<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'
import CommunityIcon from '@/components/ui/CommunityIcon.vue'
import type { BoardSummaryResponse } from '@/features/board/types/board'

withDefaults(defineProps<{
  boards: BoardSummaryResponse[]
  loading: boolean
  variant?: 'header' | 'sidebar'
}>(), { variant: 'header' })

const route = useRoute()
</script>

<template>
  <nav :aria-label="variant === 'header' ? '상단 게시판 메뉴' : '전체 게시판'"
    :class="variant === 'header' ? 'flex min-w-0 flex-1 items-center gap-1 overflow-x-auto [scrollbar-width:none]' : 'grid gap-0.5'">
    <RouterLink :to="{ name: 'home' }"
      class="flex shrink-0 items-center gap-2.5 text-[13px] transition-colors focus-visible:outline-2 focus-visible:outline-ring"
      :class="[
        variant === 'header' ? 'h-15 border-b-2 px-4' : 'min-h-9 rounded-md px-3 py-2',
        route.name === 'home'
          ? variant === 'header' ? 'border-primary text-primary' : 'bg-zinc-200/70 font-medium text-foreground'
          : variant === 'header' ? 'border-transparent text-zinc-600 hover:text-primary' : 'text-zinc-600 hover:bg-zinc-100',
      ]">
      <CommunityIcon name="home" />홈
    </RouterLink>
    <RouterLink v-for="board in boards" :key="board.id"
      :to="{ name: 'board-detail', params: { boardSlug: board.slug } }"
      class="flex min-w-0 shrink-0 items-center gap-2.5 text-[13px] transition-colors focus-visible:outline-2 focus-visible:outline-ring"
      :class="[
        variant === 'header' ? 'h-15 border-b-2 px-4' : 'min-h-9 rounded-md px-3 py-2',
        route.params.boardSlug === board.slug
          ? variant === 'header' ? 'border-primary text-primary' : 'bg-zinc-200/70 font-medium text-foreground'
          : variant === 'header' ? 'border-transparent text-zinc-600 hover:text-primary' : 'text-zinc-600 hover:bg-zinc-100',
      ]"
      :aria-current="route.params.boardSlug === board.slug ? 'page' : undefined">
      <CommunityIcon name="message" /><span :class="variant === 'sidebar' ? 'break-words' : 'whitespace-nowrap'">{{ board.name }}</span>
    </RouterLink>
    <span v-if="loading && !boards.length" class="px-3 py-3 text-xs text-zinc-400">게시판 불러오는 중…</span>
  </nav>
</template>
