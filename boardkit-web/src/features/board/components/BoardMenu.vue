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
  <nav class="board-menu" aria-label="게시판 메뉴">
    <span v-if="loading" class="board-menu__state">
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
        class="board-menu__link"
    >
      {{ board.name }}
    </RouterLink>
  </nav>
</template>

<style scoped>
.board-menu {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
  align-items: center;
  margin-left: auto;
}

.board-menu__link {
  padding: 0.45rem 0.7rem;
  border-radius: 0.5rem;
  color: #475569;
  font-size: 0.875rem;
  font-weight: 700;
  text-decoration: none;
}

.board-menu__link:hover {
  color: #1d4ed8;
  background: #eff6ff;
}

.board-menu__link.router-link-active {
  color: #fff;
  background: #2563eb;
}

.board-menu__state {
  color: #94a3b8;
  font-size: 0.8rem;
}
</style>