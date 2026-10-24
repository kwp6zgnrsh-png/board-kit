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

  let start = Math.max(
    1,
    props.page - Math.floor(visibleCount / 2),
  )
  const end = Math.min(
    props.totalPages,
    start + visibleCount - 1,
  )

  start = Math.max(1, end - visibleCount + 1)

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
  <nav v-if="totalPages > 1" class="flex flex-wrap justify-center gap-1 py-5" aria-label="게시글 페이지">
    <button type="button" class="inline-flex h-8 items-center justify-center rounded-md border border-zinc-200 bg-white px-2 text-xs text-zinc-600 hover:bg-zinc-50 focus-visible:outline-2 focus-visible:outline-ring disabled:opacity-40 sm:h-8 sm:px-3"
      :disabled="page <= 1" @click="changePage(page - 1)">‹ 이전</button>
    <button v-for="pageNumber in visiblePages" :key="pageNumber" type="button"
      class="grid size-8 place-items-center rounded-md border text-xs font-medium focus-visible:outline-2 focus-visible:outline-ring sm:size-8"
      :class="pageNumber === page ? 'border-primary bg-primary text-white' : 'border-zinc-200 bg-white text-zinc-600 hover:bg-zinc-50'"
      :aria-current="pageNumber === page ? 'page' : undefined" @click="changePage(pageNumber)">{{ pageNumber }}</button>
    <button type="button" class="inline-flex h-8 items-center justify-center rounded-md border border-zinc-200 bg-white px-2 text-xs text-zinc-600 hover:bg-zinc-50 focus-visible:outline-2 focus-visible:outline-ring disabled:opacity-40 sm:h-8 sm:px-3"
      :disabled="page >= totalPages" @click="changePage(page + 1)">다음 ›</button>
  </nav>
</template>
