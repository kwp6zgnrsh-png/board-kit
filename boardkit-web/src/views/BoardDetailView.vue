<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { getBoardDetail } from '@/api/boardApi'
import { ApiError } from '@/api/http'
import BoardCategoryList from '@/features/board/components/BoardCategoryList.vue'
import BoardHeader from '@/features/board/components/BoardHeader.vue'
import type { BoardDetailResponse } from '@/features/board/types/board'

const route = useRoute()

const board = ref<BoardDetailResponse | null>(null)
const loading = ref(false)
const errorMessage = ref('')

const boardSlug = computed(() => String(route.params.boardSlug ?? ''))

let requestSequence = 0

async function loadBoard(slug: string) {
  const currentRequest = ++requestSequence

  loading.value = true
  errorMessage.value = ''
  board.value = null

  try {
    const response = await getBoardDetail(slug)

    if (currentRequest === requestSequence) {
      board.value = response
    }
  } catch (error) {
    if (currentRequest !== requestSequence) {
      return
    }

    if (error instanceof ApiError && error.status === 404) {
      errorMessage.value = '게시판을 찾을 수 없습니다.'
    } else if (error instanceof ApiError && error.status === 403) {
      errorMessage.value = '게시판을 조회할 권한이 없습니다.'
    } else {
      errorMessage.value =
          error instanceof Error
              ? error.message
              : '게시판을 불러오지 못했습니다.'
    }
  } finally {
    if (currentRequest === requestSequence) {
      loading.value = false
    }
  }
}

watch(boardSlug, loadBoard, {
  immediate: true,
})
</script>

<template>
  <div class="board-page">
    <p v-if="loading" class="state-message">
      게시판을 불러오는 중입니다.
    </p>

    <p
        v-else-if="errorMessage"
        class="state-message state-message--error"
        role="alert"
    >
      {{ errorMessage }}
    </p>

    <template v-else-if="board">
      <BoardHeader
          :name="board.name"
          :description="board.description"
      />

      <BoardCategoryList
          v-if="board.policy.categoriesEnabled"
          :categories="board.categories"
      />

      <section class="post-area">
        <h2>게시글</h2>
      </section>
    </template>
  </div>
</template>

<style scoped>
.board-page {
  display: grid;
  gap: 1.5rem;
}

.state-message {
  margin: 0;
  padding: 1rem;
  border-radius: 0.75rem;
  color: #475569;
  background: #f1f5f9;
}

.state-message--error {
  color: #991b1b;
  background: #fee2e2;
}

.post-area {
  min-height: 16rem;
  padding: 1.5rem;
  border: 1px solid #e2e8f0;
  border-radius: 1rem;
  background: #fff;
}

.post-area h2 {
  margin: 0;
  color: #0f172a;
  font-size: 1.1rem;
}
</style>