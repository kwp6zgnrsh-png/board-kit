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
    :class="variant === 'header' ? 'flex min-w-0 flex-1 items-center gap-1 overflow-x-auto [scrollbar-width:none]' : 'grid gap-1'">
    <RouterLink :to="{ name: 'home' }"
      class="flex shrink-0 items-center gap-2.5 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-blue-600"
      :class="[
        variant === 'header' ? 'h-15 border-b-2 px-4' : 'rounded-md px-3 py-3',
        route.name === 'home'
          ? variant === 'header' ? 'border-blue-600 text-blue-600' : 'bg-blue-50 text-blue-600'
          : variant === 'header' ? 'border-transparent text-slate-600 hover:text-blue-600' : 'text-slate-600 hover:bg-slate-100',
      ]">
      <CommunityIcon name="home" />홈
    </RouterLink>
    <RouterLink v-for="board in boards" :key="board.id"
      :to="{ name: 'board-detail', params: { boardSlug: board.slug } }"
      class="flex min-w-0 shrink-0 items-center gap-2.5 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-blue-600"
      :class="[
        variant === 'header' ? 'h-15 border-b-2 px-4' : 'rounded-md px-3 py-3',
        route.params.boardSlug === board.slug
          ? variant === 'header' ? 'border-blue-600 text-blue-600' : 'bg-blue-50 text-blue-600'
          : variant === 'header' ? 'border-transparent text-slate-600 hover:text-blue-600' : 'text-slate-600 hover:bg-slate-100',
      ]"
      :aria-current="route.params.boardSlug === board.slug ? 'page' : undefined">
      <CommunityIcon name="message" /><span :class="variant === 'sidebar' ? 'break-words' : 'whitespace-nowrap'">{{ board.name }}</span>
    </RouterLink>
    <span v-if="loading && !boards.length" class="px-3 py-3 text-xs text-slate-400">게시판 불러오는 중…</span>
  </nav>
</template>
