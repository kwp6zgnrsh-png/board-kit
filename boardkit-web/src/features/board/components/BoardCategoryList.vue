<script setup lang="ts">
import type { BoardCategoryResponse } from '@/features/board/types/board'

defineProps<{
  categories: BoardCategoryResponse[]
}>()
</script>

<template>
  <nav
      v-if="categories.length > 0"
      class="category-list"
      aria-label="게시판 카테고리"
  >
    <span
        v-for="category in categories"
        :key="category.id"
        class="category"
    >
      {{ category.name }}
    </span>
  </nav>
</template>

<style scoped>
.category-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.category {
  padding: 0.45rem 0.75rem;
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  color: #475569;
  background: #fff;
  font-size: 0.875rem;
}
</style>