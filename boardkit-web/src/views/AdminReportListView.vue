<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {RouterLink, useRoute, useRouter} from 'vue-router'

import {ApiError} from '@/api/http'
import {getAdminReports} from '@/api/reportApi'
import BaseButton from '@/components/ui/BaseButton.vue'
import BasePagination from '@/components/ui/BasePagination.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import {getAuthSession} from '@/features/auth/services/authSession'
import type {
  AdminReportPageResponse,
  ReportReason,
  ReportStatus,
  ReportStatusFilter,
} from '@/features/report/types/report'

const route = useRoute()
const router = useRouter()

const result = ref<AdminReportPageResponse | null>(null)
const loading = ref(false)
const errorMessage = ref('')
let requestSequence = 0

const filterOptions = [
  {value: 'PENDING', label: '대기'},
  {value: 'ACCEPTED', label: '인정'},
  {value: 'REJECTED', label: '반려'},
  {value: 'ALL', label: '전체'},
] as const

const reasonLabels: Record<ReportReason, string> = {
  SPAM: '광고·도배',
  ABUSE: '욕설·비방',
  OTHER: '기타',
}

const statusLabels: Record<ReportStatus, string> = {
  PENDING: '대기',
  ACCEPTED: '인정',
  REJECTED: '반려',
}

const statusClasses: Record<ReportStatus, string> = {
  PENDING: 'bg-amber-50 text-amber-700',
  ACCEPTED: 'bg-blue-50 text-blue-700',
  REJECTED: 'bg-slate-100 text-slate-600',
}

const adminId = computed(() => {
  const session = getAuthSession()
  return session?.role === 'ADMIN' ? session.memberId : null
})

const selectedFilter = computed<ReportStatusFilter>(() => {
  const value = route.query.filter

  return value === 'ALL' || value === 'ACCEPTED' || value === 'REJECTED'
      ? value
      : 'PENDING'
})

const requestedPage = computed(() => {
  const value = route.query.page
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return 1

  const page = Number(value)
  return Number.isSafeInteger(page) && page <= 2_147_483_647 ? page : 1
})

async function loadReports(
    filter: ReportStatusFilter,
    page: number,
    memberId: number | null,
) {
  const sequence = ++requestSequence
  result.value = null
  errorMessage.value = ''
  loading.value = false

  if (memberId === null) {
    errorMessage.value = '관리자 계정으로 로그인해 주세요.'
    return
  }

  loading.value = true

  try {
    const response = await getAdminReports({filter, page, size: 10})

    if (sequence !== requestSequence || adminId.value !== memberId) return

    const lastPage = Math.max(1, response.totalPages)

    if (page > lastPage) {
      await router.replace({
        query: {
          ...route.query,
          page: lastPage === 1 ? undefined : String(lastPage),
        },
      })
      return
    }

    result.value = response
  } catch (error) {
    if (sequence !== requestSequence || adminId.value !== memberId) return

    if (error instanceof ApiError && error.status === 401) {
      errorMessage.value = '다시 로그인해 주세요.'
    } else if (error instanceof ApiError && error.status === 403) {
      errorMessage.value = '관리자 권한이 필요합니다.'
    } else {
      errorMessage.value = '신고 목록을 불러오지 못했습니다.'
    }
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

function changeFilter(value: string) {
  if (
      value !== 'PENDING' &&
      value !== 'ACCEPTED' &&
      value !== 'REJECTED' &&
      value !== 'ALL'
  ) return

  if (value === selectedFilter.value) return

  void router.push({
    query: {
      ...route.query,
      filter: value === 'PENDING' ? undefined : value,
      page: undefined,
    },
  })
}

function changePage(page: number) {
  if (
      loading.value ||
      !result.value ||
      page < 1 ||
      page > result.value.totalPages ||
      page === requestedPage.value
  ) return

  void router.push({
    query: {
      ...route.query,
      page: page === 1 ? undefined : String(page),
    },
  })
}

function reloadReports() {
  if (loading.value) return
  void loadReports(selectedFilter.value, requestedPage.value, adminId.value)
}

watch(
    [selectedFilter, requestedPage, adminId],
    ([filter, page, memberId]) => {
      void loadReports(filter, page, memberId)
    },
    {immediate: true},
)

onBeforeUnmount(() => {
  requestSequence++
})
</script>

<template>
  <div class="grid gap-6">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <p class="text-xs font-medium text-blue-600">관리자</p>
        <h1 class="mt-1 text-2xl font-semibold tracking-tight text-slate-900">
          신고 처리
        </h1>
        <p class="mt-2 text-sm text-slate-500">
          게시글과 댓글 신고를 확인합니다.
        </p>
      </div>

      <RouterLink
          :to="{ name: 'admin-dashboard' }"
          class="rounded-md border border-slate-200 px-3 py-2 text-xs font-medium text-slate-700 hover:bg-slate-50"
      >
        관리자 메뉴
      </RouterLink>
    </header>

    <section
        class="overflow-hidden rounded-lg border border-slate-200 bg-white"
        :aria-busy="loading"
    >
      <header class="flex flex-wrap items-end justify-between gap-4 border-b border-slate-200 px-5 py-4">
        <div class="w-40">
          <BaseSelect
              id="report-status-filter"
              label="처리 상태"
              :model-value="selectedFilter"
              :options="filterOptions"
              @update:model-value="changeFilter"
          />
        </div>

        <div class="flex items-center gap-3">
          <p v-if="result" class="text-sm text-slate-500">
            전체 {{ result.totalElements.toLocaleString() }}개
          </p>
          <BaseButton
              variant="secondary"
              size="sm"
              :disabled="loading || adminId === null"
              @click="reloadReports"
          >
            새로고침
          </BaseButton>
        </div>
      </header>

      <p
          v-if="errorMessage"
          role="alert"
          class="m-5 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
      >
        {{ errorMessage }}
      </p>

      <p v-else-if="loading" class="px-5 py-12 text-center text-sm text-slate-500">
        목록을 불러오는 중입니다.
      </p>

      <template v-else-if="result">
        <p
            v-if="result.reports.length === 0"
            class="px-5 py-12 text-center text-sm text-slate-500"
        >
          조건에 해당하는 신고가 없습니다.
        </p>

        <template v-else>
          <div class="overflow-x-auto">
            <table class="w-full min-w-[960px] table-fixed text-sm">
              <caption class="sr-only">관리자 신고 목록</caption>

              <thead class="border-b border-slate-200 bg-slate-50 text-slate-600">
              <tr>
                <th scope="col" class="w-20 px-3 py-3 text-center font-medium">번호</th>
                <th scope="col" class="px-4 py-3 text-left font-medium">신고 대상</th>
                <th scope="col" class="w-28 px-3 py-3 text-left font-medium">게시판</th>
                <th scope="col" class="w-28 px-3 py-3 text-left font-medium">신고자</th>
                <th scope="col" class="w-24 px-3 py-3 text-left font-medium">사유</th>
                <th scope="col" class="w-20 px-3 py-3 text-center font-medium">상태</th>
                <th scope="col" class="w-36 px-3 py-3 text-left font-medium">신고일</th>
              </tr>
              </thead>

              <tbody class="divide-y divide-slate-100">
              <tr
                  v-for="report in result.reports"
                  :key="report.id"
                  class="hover:bg-slate-50"
              >
                <td class="px-3 py-3 text-center text-slate-500">
                  {{ report.id }}
                </td>

                <td class="px-4 py-3">
                  <RouterLink
                      :to="{
                        name: 'admin-report-detail',
                        params: { reportId: report.id },
                        query: {
                          filter: route.query.filter,
                          page: route.query.page,
                        },
                      }"
                      class="block truncate font-medium text-slate-800 hover:text-blue-600 hover:underline"
                  >
                    {{ report.postTitle }}
                  </RouterLink>
                  <div class="mt-1 flex flex-wrap gap-2 text-xs text-slate-500">
                      <span>
                        {{ report.commentId === null ? '게시글 신고' : '댓글 신고' }}
                      </span>
                    <span v-if="report.postDeleted" class="text-red-600">
                        게시글 삭제됨
                      </span>
                    <span v-if="report.commentDeleted" class="text-red-600">
                        댓글 삭제됨
                      </span>
                  </div>
                </td>

                <td class="px-3 py-3 text-slate-600">
                  <span class="block truncate">{{ report.boardName }}</span>
                  <span
                      v-if="report.boardStatus === 'ARCHIVED'"
                      class="mt-1 inline-block text-xs text-slate-500"
                  >
                      보관된 게시판
                    </span>
                </td>

                <td class="truncate px-3 py-3 text-slate-600">
                  {{ report.reporterDisplayName }}
                </td>

                <td class="px-3 py-3 text-slate-600">
                  {{ reasonLabels[report.reason] }}
                </td>

                <td class="px-3 py-3 text-center">
                    <span
                        class="inline-flex rounded-full px-2 py-1 text-xs font-medium"
                        :class="statusClasses[report.status]"
                    >
                      {{ statusLabels[report.status] }}
                    </span>
                </td>

                <td class="px-3 py-3 text-xs text-slate-500">
                  {{ report.createdAt.replace('T', ' ').slice(0, 16) }}
                </td>
              </tr>
              </tbody>
            </table>
          </div>

          <BasePagination
              aria-label="신고 목록 페이지"
              :page="result.page"
              :total-pages="result.totalPages"
              @change="changePage"
          />
        </template>
      </template>
    </section>
  </div>
</template>