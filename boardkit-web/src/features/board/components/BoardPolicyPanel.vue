<script setup lang="ts">
import { computed } from 'vue'

import { getBoardTheme } from '@/features/board/services/boardTheme'
import type { BoardDetailResponse } from '@/features/board/types/board'

const props = defineProps<{
  board: BoardDetailResponse
}>()

const theme = computed(() => getBoardTheme(props.board.slug))

const themeStyle = computed(() => ({
  '--board-accent': theme.value.accent,
  '--board-soft': theme.value.soft,
}))

const readPermissionLabel = computed(() => ({
  PUBLIC: '누구나 읽을 수 있어요',
  MEMBER: '회원만 읽을 수 있어요',
  ADMIN: '관리자만 읽을 수 있어요',
}[props.board.policy.readPermission]))

const writePermissionLabel = computed(() =>
  props.board.policy.writePermission === 'ADMIN'
    ? '관리자만 글을 쓸 수 있어요'
    : '회원이라면 글을 쓸 수 있어요',
)

const secretModeLabel = computed(() => ({
  OFF: '비밀글을 사용하지 않아요',
  OPTIONAL: '비밀글을 선택할 수 있어요',
  REQUIRED: '모든 글이 비밀글이에요',
}[props.board.policy.secretMode]))
</script>

<template>
  <aside :style="themeStyle" class="grid content-start gap-4">
    <section class="overflow-hidden rounded-lg border-[3px] border-slate-950 bg-white">
      <header class="flex items-center gap-3 border-b-[3px] border-slate-950 bg-[var(--board-accent)] px-4 py-3">
        <span class="grid h-10 w-10 place-items-center rounded border-[3px] border-slate-950 bg-white text-xl">💬</span>
        <div>
          <h2 class="m-0 text-lg font-black text-slate-950">게시판 안내</h2>
          <p class="m-0 text-xs font-bold text-slate-700">이 공간의 이용 정책이에요.</p>
        </div>
      </header>

      <ol class="grid gap-4 p-4">
        <li class="flex gap-3">
          <span class="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-blue-500 text-sm font-black text-white">1</span>
          <div><strong class="block text-sm text-slate-950">읽기 권한</strong><p class="mt-0.5 text-xs leading-5 text-slate-600">{{ readPermissionLabel }}</p></div>
        </li>
        <li class="flex gap-3">
          <span class="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-blue-500 text-sm font-black text-white">2</span>
          <div><strong class="block text-sm text-slate-950">글쓰기 권한</strong><p class="mt-0.5 text-xs leading-5 text-slate-600">{{ writePermissionLabel }}</p></div>
        </li>
        <li class="flex gap-3">
          <span class="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-blue-500 text-sm font-black text-white">3</span>
          <div><strong class="block text-sm text-slate-950">비밀글</strong><p class="mt-0.5 text-xs leading-5 text-slate-600">{{ secretModeLabel }}</p></div>
        </li>
        <li class="flex gap-3">
          <span class="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-blue-500 text-sm font-black text-white">4</span>
          <div><strong class="block text-sm text-slate-950">댓글</strong><p class="mt-0.5 text-xs leading-5 text-slate-600">{{ board.policy.commentsEnabled ? '댓글로 대화할 수 있어요' : '댓글을 사용하지 않아요' }}</p></div>
        </li>
      </ol>

      <p class="mx-4 mb-4 rotate-[-2deg] border-2 border-slate-950 bg-yellow-300 px-3 py-3 text-center text-xs font-black text-slate-950 shadow-[2px_2px_0_#0f172a]">
        좋은 대화가 좋은 커뮤니티를 만듭니다.
      </p>
    </section>

    <section
      v-if="board.policy.categoriesEnabled && board.categories.length > 0"
      class="overflow-hidden rounded-lg border-[3px] border-slate-950 bg-white"
    >
      <header class="border-b-[3px] border-slate-950 bg-emerald-400 px-4 py-3">
        <h2 class="m-0 text-lg font-black text-slate-950">카테고리</h2>
        <p class="m-0 text-xs font-bold text-slate-700">게시판의 주제를 살펴보세요.</p>
      </header>
      <div class="flex flex-wrap gap-2 p-4">
        <span
          v-for="category in board.categories"
          :key="category.id"
          class="rounded-full border-2 border-slate-950 bg-white px-3 py-1.5 text-xs font-black text-slate-800"
        >
          #{{ category.name }}
        </span>
      </div>
    </section>
  </aside>
</template>
