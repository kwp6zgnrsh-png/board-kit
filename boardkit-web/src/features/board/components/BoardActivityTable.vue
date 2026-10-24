<script setup lang="ts">
import {computed} from 'vue'
import type {BoardActivityResponse} from '@/features/board/types/boardStatistics'

const props = defineProps<{
  activities: BoardActivityResponse[]
  selectedBoardId: number | null
}>()

const totals = computed(() => ({
  postCount: props.activities.reduce((sum, row) => sum + row.postCount, 0),
  commentCount: props.activities.reduce((sum, row) => sum + row.commentCount, 0),
}))
</script>

<template>
  <section class="min-w-0 overflow-hidden rounded-xl border border-border bg-card shadow-xs">
    <header class="border-b border-zinc-200 px-5 py-4">
      <h3 class="text-base font-semibold text-zinc-900">
        게시판별 작성 수
      </h3>
      <p class="mt-1 text-xs text-zinc-500">
        선택한 기간의 전체 게시판을 비교합니다. 보관된 게시판도 포함됩니다.
      </p>
    </header>

    <div class="overflow-x-auto">
      <table class="w-full min-w-[420px] text-sm">
        <caption class="sr-only">게시판별 기간 내 게시글·댓글 작성 수</caption>
        <thead class="bg-zinc-50 text-zinc-600">
        <tr>
          <th scope="col" class="px-5 py-3 text-left font-medium">게시판</th>
          <th scope="col" class="px-5 py-3 text-right font-medium">게시글</th>
          <th scope="col" class="px-5 py-3 text-right font-medium">댓글</th>
        </tr>
        </thead>

        <tbody class="divide-y divide-zinc-100">
        <tr v-if="activities.length === 0">
          <td colspan="3" class="px-5 py-10 text-center text-zinc-500">
            통계 대상 게시판이 없습니다.
          </td>
        </tr>

        <tr
            v-for="row in activities"
            :key="row.boardId"
            :class="row.boardId === selectedBoardId
              ? 'bg-accent'
              : 'hover:bg-zinc-50'"
        >
          <th scope="row" class="px-5 py-3 text-left font-medium text-zinc-800">
            {{ row.boardName }}
            <span
                v-if="row.boardId === selectedBoardId"
                class="ml-2 rounded bg-zinc-200/70 px-1.5 py-0.5 text-xs text-primary"
            >
                선택
              </span>
          </th>
          <td class="px-5 py-3 text-right tabular-nums text-zinc-700">
            {{ row.postCount.toLocaleString('ko-KR') }}
          </td>
          <td class="px-5 py-3 text-right tabular-nums text-zinc-700">
            {{ row.commentCount.toLocaleString('ko-KR') }}
          </td>
        </tr>
        </tbody>

        <tfoot
            v-if="activities.length > 0"
            class="border-t border-zinc-200 bg-zinc-50 font-semibold text-zinc-900"
        >
        <tr>
          <th scope="row" class="px-5 py-3 text-left">전체 합계</th>
          <td class="px-5 py-3 text-right tabular-nums">
            {{ totals.postCount.toLocaleString('ko-KR') }}
          </td>
          <td class="px-5 py-3 text-right tabular-nums">
            {{ totals.commentCount.toLocaleString('ko-KR') }}
          </td>
        </tr>
        </tfoot>
      </table>
    </div>
  </section>
</template>