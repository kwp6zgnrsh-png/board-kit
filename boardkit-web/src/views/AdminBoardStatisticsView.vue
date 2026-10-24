<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {RouterLink, useRoute, useRouter} from 'vue-router'

import {getAdminBoardStatistics} from '@/api/adminBoardStatisticsApi'
import {ApiError} from '@/api/http'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import {getAuthSession} from '@/features/auth/services/authSession'
import BoardStatisticsSummary from '@/features/board/components/BoardStatisticsSummary.vue'
import type {
  BoardActivityResponse,
  BoardStatisticsDays,
  BoardStatisticsResponse,
} from '@/features/board/types/boardStatistics'
import BoardActivityChart from '@/features/board/components/BoardActivityChart.vue'
import BoardActivityTable from '@/features/board/components/BoardActivityTable.vue'

const route = useRoute()
const router = useRouter()

const result = ref<BoardStatisticsResponse | null>(null)
const boards = ref<BoardActivityResponse[]>([])
const loading = ref(false)
const errorMessage = ref('')
let requestSequence = 0

const periodOptions = [
  {value: '7', label: '최근 7일'},
  {value: '30', label: '최근 30일'},
  {value: '90', label: '최근 90일'},
] as const

const adminId = computed(() => {
  const session = getAuthSession()
  return session?.role === 'ADMIN' ? session.memberId : null
})

const selectedDays = computed<BoardStatisticsDays>(() => {
  if (route.query.days === '7') return 7
  if (route.query.days === '90') return 90
  return 30
})

const selectedBoardId = computed<number | null>(() => {
  const value = route.query.boardId
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return null

  const id = Number(value)
  return Number.isSafeInteger(id) ? id : null
})

const boardOptions = computed(() => {
  const options = [
    {value: '', label: '전체 게시판'},
    ...boards.value.map((board) => ({
      value: String(board.boardId),
      label: board.boardName,
    })),
  ]

  const id = selectedBoardId.value
  if (id !== null && !boards.value.some((board) => board.boardId === id)) {
    options.push({value: String(id), label: '선택한 게시판'})
  }

  return options
})

const selectedBoardName = computed(() => {
  if (selectedBoardId.value === null) return '전체 게시판'

  return boards.value.find(
      (board) => board.boardId === selectedBoardId.value,
  )?.boardName ?? '선택한 게시판'
})

async function loadStatistics() {
  const sequence = ++requestSequence
  const memberId = adminId.value
  const boardId = selectedBoardId.value
  const days = selectedDays.value

  result.value = null
  errorMessage.value = ''
  loading.value = false

  if (memberId === null) {
    boards.value = []
    errorMessage.value = '관리자 계정으로 로그인해 주세요.'
    return
  }

  loading.value = true

  try {
    const response = await getAdminBoardStatistics(
        boardId === null ? {days} : {boardId, days},
    )

    if (sequence !== requestSequence || adminId.value !== memberId) return

    boards.value = response.boardActivities
    result.value = response
  } catch (error) {
    if (sequence !== requestSequence || adminId.value !== memberId) return

    if (error instanceof ApiError && error.status === 401) {
      errorMessage.value = '다시 로그인해 주세요.'
    } else if (error instanceof ApiError && error.status === 403) {
      errorMessage.value = '관리자 권한이 필요합니다.'
    } else if (error instanceof ApiError && error.status === 404) {
      errorMessage.value = '통계를 조회할 수 없는 게시판입니다.'
    } else {
      errorMessage.value = '통계를 불러오지 못했습니다. 다시 시도해 주세요.'
    }
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

function changeBoard(value: string) {
  if (value !== '') {
    const id = Number(value)
    if (!Number.isSafeInteger(id) || id <= 0) return
  }

  if (value === String(selectedBoardId.value ?? '')) return

  void router.push({
    query: {
      ...route.query,
      boardId: value === '' ? undefined : value,
    },
  })
}

function changeDays(value: string) {
  if (value !== '7' && value !== '30' && value !== '90') return
  if (value === String(selectedDays.value)) return

  void router.push({
    query: {
      ...route.query,
      days: value === '30' ? undefined : value,
    },
  })
}

watch(
    [selectedBoardId, selectedDays, adminId],
    () => {
      void loadStatistics()
    },
    {immediate: true, flush: 'sync'},
)

onBeforeUnmount(() => {
  requestSequence++
})
</script>

<template>
  <div class="grid gap-6">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <p class="text-xs font-medium text-primary">관리자</p>
        <h1 class="mt-1 text-2xl font-semibold tracking-tight text-zinc-900">
          게시판 활동 통계
        </h1>
        <p class="mt-2 text-sm text-zinc-500">
          게시판별 현황과 작성 활동을 확인하세요.
        </p>
      </div>

      <RouterLink
          :to="{ name: 'admin-dashboard' }"
          class="rounded-md border border-zinc-200 px-3 py-2 text-xs font-medium text-zinc-700 hover:bg-zinc-50"
      >
        관리자 메뉴
      </RouterLink>
    </header>

    <section
        aria-label="통계 조회 조건"
        class="flex flex-wrap items-end gap-4 rounded-xl border border-border bg-card p-5 shadow-xs"
    >
      <div class="w-full sm:w-56">
        <BaseSelect
            id="statistics-board"
            label="게시판"
            :model-value="String(selectedBoardId ?? '')"
            :options="boardOptions"
            @update:model-value="changeBoard"
        />
      </div>

      <div class="w-full sm:w-40">
        <BaseSelect
            id="statistics-period"
            label="조회 기간"
            :model-value="String(selectedDays)"
            :options="periodOptions"
            @update:model-value="changeDays"
        />
      </div>

      <BaseButton
          variant="secondary"
          :disabled="loading || adminId === null"
          @click="loadStatistics"
      >
        새로고침
      </BaseButton>

      <p class="text-xs text-zinc-500 sm:ml-auto">
        한국 시각 기준
      </p>
    </section>

    <section aria-label="통계 조회 결과" :aria-busy="loading">
      <div
          v-if="errorMessage"
          class="rounded-lg border border-red-200 bg-red-50 p-5"
      >
        <p role="alert" class="text-sm text-red-700">
          {{ errorMessage }}
        </p>

        <BaseButton
            v-if="selectedBoardId !== null"
            variant="secondary"
            size="sm"
            class="mt-3"
            @click="changeBoard('')"
        >
          전체 게시판으로 조회
        </BaseButton>
      </div>

      <p
          v-else-if="loading"
          role="status"
          class="py-16 text-center text-sm text-zinc-500"
      >
        통계를 불러오는 중입니다.
      </p>

      <div v-else-if="result" class="grid gap-4">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <h2 class="text-lg font-semibold text-zinc-900">
            {{ selectedBoardName }}
          </h2>
          <p class="text-sm text-zinc-500">
            작성 활동 조회 기간: {{ result.startDate }} ~ {{ result.endDate }}
          </p>
        </div>

        <BoardStatisticsSummary :summary="result.summary"/>

        <p class="text-xs leading-5 text-zinc-500">
          현재 수는 삭제되지 않은 내용 기준이며,
          오늘 작성 수에는 이후 삭제한 내용도 포함됩니다.
        </p>
        <BoardActivityChart
            :activities="result.dailyActivities"
        />

        <BoardActivityTable
            :activities="result.boardActivities"
            :selected-board-id="result.boardId"
        />
      </div>
    </section>
  </div>
</template>