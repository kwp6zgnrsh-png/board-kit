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
  <nav
    v-if="totalPages > 1"
    class="flex flex-wrap justify-center gap-1.5 px-4 py-4"
    aria-label="게시글 페이지"
  >
    <button
      type="button"
      class="inline-flex h-8 items-center justify-center rounded border-2 border-slate-950 bg-white px-2.5 text-xs font-black text-slate-950 shadow-[2px_2px_0_#0f172a] transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-35 disabled:hover:translate-y-0"
      :disabled="page <= 1"
      @click="changePage(page - 1)"
    >
      ‹ 이전
    </button>

    <button
      v-for="pageNumber in visiblePages"
      :key="pageNumber"
      type="button"
      class="grid h-8 min-w-8 place-items-center rounded border-2 border-slate-950 px-1.5 text-xs font-black shadow-[2px_2px_0_#0f172a] transition hover:-translate-y-0.5"
      :class="
        pageNumber === page
          ? 'bg-blue-500 text-white'
          : 'bg-white text-slate-950 hover:bg-yellow-200'
      "
      :aria-current="pageNumber === page ? 'page' : undefined"
      @click="changePage(pageNumber)"
    >
      {{ pageNumber }}
    </button>

    <button
      type="button"
      class="inline-flex h-8 items-center justify-center rounded border-2 border-slate-950 bg-white px-2.5 text-xs font-black text-slate-950 shadow-[2px_2px_0_#0f172a] transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-35 disabled:hover:translate-y-0"
      :disabled="page >= totalPages"
      @click="changePage(page + 1)"
    >
      다음 ›
    </button>
  </nav>
</template>
