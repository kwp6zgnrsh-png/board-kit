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
        class="rounded-none border border-zinc-200 bg-zinc-50 px-3 py-2 text-sm text-zinc-600"
    >
      {{ category.name }}
    </span>
  </nav>
</template>
