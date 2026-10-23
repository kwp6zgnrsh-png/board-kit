<script setup lang="ts">
import { computed } from 'vue'
import type { BoardStatisticsSummaryResponse } from '@/features/board/types/boardStatistics'

const props = defineProps<{
  summary: BoardStatisticsSummaryResponse
}>()

const cards = computed(() => [
  {
    key: 'currentPostCount',
    label: '현재 게시글',
    value: props.summary.currentPostCount,
  },
  {
    key: 'currentCommentCount',
    label: '현재 댓글',
    value: props.summary.currentCommentCount,
  },
  {
    key: 'todayPostCount',
    label: '오늘 작성된 게시글',
    value: props.summary.todayPostCount,
  },
  {
    key: 'todayCommentCount',
    label: '오늘 작성된 댓글',
    value: props.summary.todayCommentCount,
  },
])
</script>

<template>
  <dl class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
    <div
        v-for="card in cards"
        :key="card.key"
        class="rounded-lg border border-slate-200 bg-white p-5"
    >
      <dt class="text-sm font-medium text-slate-500">
        {{ card.label }}
      </dt>
      <dd class="mt-3 text-3xl font-semibold tabular-nums tracking-tight text-slate-900">
        {{ card.value.toLocaleString('ko-KR') }}
        <span class="ml-1 text-sm font-normal text-slate-500">개</span>
      </dd>
    </div>
  </dl>
</template>