<script setup lang="ts">
import {computed} from 'vue'

const props = defineProps<{
  page: number
  totalPages: number | null
  hasNext?: boolean
  availableThroughPage?: number
}>()

const emit = defineEmits<{
  change: [page: number]
}>()

const lastAvailablePage = computed(() => {
  if (props.totalPages !== null) {
    return props.totalPages
  }

  return Math.max(
      props.page,
      props.availableThroughPage
      ?? props.page + (props.hasNext ? 1 : 0),
  )
})

const canGoNext = computed(() =>
    props.totalPages === null
        ? props.hasNext === true
        : props.page < props.totalPages,
)

const visiblePages = computed(() => {
  const visibleCount = 5
  const lastPage = lastAvailablePage.value

  if (lastPage <= 0) return []

  let start = Math.max(
      1,
      props.page - Math.floor(visibleCount / 2),
  )
  const end = Math.min(lastPage, start + visibleCount - 1)
  start = Math.max(1, end - visibleCount + 1)

  return Array.from(
      {length: end - start + 1},
      (_, index) => start + index,
  )
})

function changePage(page: number) {
  if (
      page < 1 ||
      page > lastAvailablePage.value ||
      page === props.page
  ) {
    return
  }

  emit('change', page)
}
</script>

<template>
  <nav
      v-if="page > 1 || canGoNext"
      class="flex flex-wrap justify-center gap-1 py-5"
      aria-label="게시글 페이지"
  >
    <button
        type="button"
        class="inline-flex h-8 items-center justify-center rounded-none border border-zinc-200 bg-white px-2 text-xs text-zinc-600 hover:bg-zinc-50 focus-visible:outline-2 focus-visible:outline-ring disabled:opacity-40 sm:px-3"
        :disabled="page <= 1"
        @click="changePage(page - 1)"
    >
      ‹ 이전
    </button>

    <button
        v-for="pageNumber in visiblePages"
        :key="pageNumber"
        type="button"
        class="grid size-8 place-items-center rounded-none border text-xs font-medium focus-visible:outline-2 focus-visible:outline-ring"
        :class="
        pageNumber === page
          ? 'border-primary bg-primary text-white'
          : 'border-zinc-200 bg-white text-zinc-600 hover:bg-zinc-50'
      "
        :aria-current="pageNumber === page ? 'page' : undefined"
        @click="changePage(pageNumber)"
    >
      {{ pageNumber }}
    </button>

    <button
        type="button"
        class="inline-flex h-8 items-center justify-center rounded-none border border-zinc-200 bg-white px-2 text-xs text-zinc-600 hover:bg-zinc-50 focus-visible:outline-2 focus-visible:outline-ring disabled:opacity-40 sm:px-3"
        :disabled="!canGoNext"
        @click="changePage(page + 1)"
    >
      다음 ›
    </button>
  </nav>
</template>