<script setup lang="ts">
import { computed, useId } from 'vue'
import { Line } from 'vue-chartjs'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Tooltip,
  Legend,
  type ChartData,
  type ChartOptions,
} from 'chart.js'

import type { BoardDailyActivityResponse } from '@/features/board/types/boardStatistics'

ChartJS.register(
    CategoryScale,
    LinearScale,
    PointElement,
    LineElement,
    Tooltip,
    Legend,
)

const props = defineProps<{
  activities: BoardDailyActivityResponse[]
}>()

const tableId = useId()

const totals = computed(() => ({
  postCount: props.activities.reduce((sum, row) => sum + row.postCount, 0),
  commentCount: props.activities.reduce((sum, row) => sum + row.commentCount, 0),
}))

const chartData = computed<ChartData<'line', number[], string>>(() => ({
  labels: props.activities.map((row) => row.date.slice(5).replace('-', '/')),
  datasets: [
    {
      label: '게시글',
      data: props.activities.map((row) => row.postCount),
      borderColor: '#2563eb',
      backgroundColor: '#2563eb',
      borderWidth: 2,
      pointRadius: props.activities.length > 30 ? 0 : 2,
      tension: 0,
    },
    {
      label: '댓글',
      data: props.activities.map((row) => row.commentCount),
      borderColor: '#059669',
      backgroundColor: '#059669',
      borderWidth: 2,
      borderDash: [5, 4],
      pointRadius: props.activities.length > 30 ? 0 : 2,
      tension: 0,
    },
  ],
}))

const chartOptions: ChartOptions<'line'> = {
  responsive: true,
  maintainAspectRatio: false,
  animation: false,
  locale: 'ko-KR',
  interaction: {
    mode: 'index',
    intersect: false,
  },
  elements: {
    point: {
      hoverRadius: 4,
      hitRadius: 8,
    },
  },
  plugins: {
    legend: {
      position: 'bottom',
      labels: {
        usePointStyle: true,
        padding: 20,
      },
    },
    tooltip: {
      callbacks: {
        title(items) {
          const index = items[0]?.dataIndex
          return index === undefined
              ? ''
              : props.activities[index]?.date ?? ''
        },
      },
    },
  },
  scales: {
    x: {
      grid: {
        display: false,
      },
      ticks: {
        maxTicksLimit: 8,
        maxRotation: 0,
      },
    },
    y: {
      beginAtZero: true,
      suggestedMax: 1,
      ticks: {
        precision: 0,
      },
      title: {
        display: true,
        text: '작성 수 (개)',
      },
    },
  },
}
</script>

<template>
  <section class="min-w-0 rounded-lg border border-slate-200 bg-white p-5">
    <header class="flex flex-wrap items-start justify-between gap-3">
      <div>
        <h3 class="text-base font-semibold text-slate-900">
          일별 작성 추이
        </h3>
        <p class="mt-1 text-xs text-slate-500">
          선택한 게시판과 기간의 게시글·댓글 작성 수입니다.
        </p>
      </div>

      <div class="flex flex-wrap gap-3 text-sm">
        <span class="text-blue-600">
          게시글 {{ totals.postCount.toLocaleString('ko-KR') }}개
        </span>
        <span class="text-emerald-600">
          댓글 {{ totals.commentCount.toLocaleString('ko-KR') }}개
        </span>
      </div>
    </header>

    <p
        v-if="totals.postCount === 0 && totals.commentCount === 0"
        class="mt-4 rounded-md bg-slate-50 px-3 py-2 text-sm text-slate-500"
    >
      선택한 기간에 작성된 게시글과 댓글이 없습니다.
    </p>

    <div class="relative mt-5 h-72 sm:h-80">
      <Line
          :data="chartData"
          :options="chartOptions"
          :aria-describedby="tableId"
          aria-label="날짜별 게시글과 댓글 작성 수"
          role="img"
      />
    </div>

    <details class="mt-4 border-t border-slate-100 pt-4">
      <summary class="cursor-pointer text-sm font-medium text-slate-600">
        날짜별 수치 보기
      </summary>

      <div class="mt-3 max-h-80 overflow-auto">
        <table :id="tableId" class="w-full text-right text-sm">
          <caption class="sr-only">날짜별 게시글·댓글 작성 수</caption>
          <thead class="bg-slate-50 text-slate-600">
          <tr>
            <th scope="col" class="px-3 py-2 text-left font-medium">날짜</th>
            <th scope="col" class="px-3 py-2 font-medium">게시글</th>
            <th scope="col" class="px-3 py-2 font-medium">댓글</th>
          </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
          <tr v-for="row in activities" :key="row.date">
            <th scope="row" class="px-3 py-2 text-left font-normal text-slate-600">
              {{ row.date }}
            </th>
            <td class="px-3 py-2 tabular-nums text-slate-800">
              {{ row.postCount.toLocaleString('ko-KR') }}
            </td>
            <td class="px-3 py-2 tabular-nums text-slate-800">
              {{ row.commentCount.toLocaleString('ko-KR') }}
            </td>
          </tr>
          </tbody>
        </table>
      </div>
    </details>
  </section>
</template>