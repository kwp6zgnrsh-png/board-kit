<script setup lang="ts">
import type { BoardCategoryResponse } from '@/features/board/types/board'

defineProps<{
  categories: BoardCategoryResponse[]
}>()
</script>

<template>
  <nav
      v-if="categories.length > 0"
      class="flex flex-wrap gap-2"
      aria-label="게시판 카테고리"
  >
    <span
        v-for="category in categories"
        :key="category.id"
        class="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-600"
    >
      {{ category.name }}
    </span>
  </nav>
</template>
