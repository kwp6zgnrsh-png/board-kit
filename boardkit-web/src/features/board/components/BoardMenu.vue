<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { getBoards } from '@/api/boardApi'
import type { BoardSummaryResponse } from '@/features/board/types/board'

const route = useRoute()

const boards = ref<BoardSummaryResponse[]>([])
const loading = ref(false)

let requestSequence = 0

async function loadBoards() {
  const currentRequest = ++requestSequence

  loading.value = true

  try {
    const response = await getBoards()

    if (currentRequest === requestSequence) {
      boards.value = response
    }
  } catch {
    if (currentRequest === requestSequence) {
      boards.value = []
    }
  } finally {
    if (currentRequest === requestSequence) {
      loading.value = false
    }
  }
}

watch(
    () => route.fullPath,
    () => loadBoards(),
    {
      immediate: true,
    },
)
</script>

<template>
  <nav
    class="flex min-w-0 flex-1 items-center gap-2 overflow-x-auto py-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
    aria-label="게시판 메뉴"
  >
    <span v-if="loading" class="px-3 text-xs font-bold text-slate-400">
      불러오는 중
    </span>

    <RouterLink
        v-for="board in boards"
        v-else
        :key="board.id"
        :to="{
        name: 'board-detail',
        params: {
          boardSlug: board.slug,
        },
      }"
        class="shrink-0 rounded border-2 border-slate-950 px-3 py-2 text-xs font-black no-underline shadow-[2px_2px_0_#0f172a] transition hover:-translate-y-0.5"
        :class="
          route.params.boardSlug === board.slug
            ? 'bg-blue-500 text-white'
            : 'bg-white text-slate-950 hover:bg-yellow-200'
        "
    >
      {{ board.name }}
    </RouterLink>
  </nav>
</template>
