<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  page: number
  totalPages: number
}>()

const emit = defineEmits<{
  change: [page: number]
}>()

const visiblePages = computed(() => {
  const visibleCount = 5

  if (props.totalPages <= 0) {
    return []
  }

  let start = Math.max(1, props.page - Math.floor(visibleCount / 2),)

  let end = Math.min(props.totalPages, start + visibleCount - 1,)

  start = Math.max(1, end - visibleCount + 1,)

  return Array.from(
      { length: end - start + 1 },
      (_, index) => start + index,
  )
})

function changePage(page: number) {
  if (
      page < 1 ||
      page > props.totalPages ||
      page === props.page
  ) {
    return
  }

  emit('change', page)
}
</script>

<template>
  <nav
      v-if="totalPages > 1"
      class="pagination"
      aria-label="게시글 페이지"
  >
    <button
        type="button"
        :disabled="page <= 1"
        @click="changePage(page - 1)"
    >
      이전
    </button>

    <button
        v-for="pageNumber in visiblePages"
        :key="pageNumber"
        type="button"
        :class="{
        'pagination__button--active':
          pageNumber === page,
      }"
        :aria-current="
        pageNumber === page ? 'page' : undefined
      "
        @click="changePage(pageNumber)"
    >
      {{ pageNumber }}
    </button>

    <button
        type="button"
        :disabled="page >= totalPages"
        @click="changePage(page + 1)"
    >
      다음
    </button>
  </nav>
</template>

<style scoped>
.pagination {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  justify-content: center;
}

.pagination button {
  min-width: 2.4rem;
  min-height: 2.4rem;
  padding: 0.4rem 0.65rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.5rem;
  color: #475569;
  background: #fff;
  cursor: pointer;
}

.pagination button:hover:not(:disabled) {
  border-color: #2563eb;
  color: #2563eb;
}

.pagination button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.pagination .pagination__button--active {
  border-color: #2563eb;
  color: #fff;
  background: #2563eb;
}
</style>