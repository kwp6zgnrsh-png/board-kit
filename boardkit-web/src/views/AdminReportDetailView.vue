<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {RouterLink, useRoute} from 'vue-router'

import {ApiError} from '@/api/http'
import {getAdminReport, moderateReportTarget, reviewReport} from '@/api/reportApi'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseTextarea from '@/components/ui/BaseTextarea.vue'
import {getAuthSession} from '@/features/auth/services/authSession'
import type {
  AdminReportDetailResponse,
  ReportReason,
  ReportReviewStatus,
  ReportStatus,
  ReportTargetAction,
} from '@/features/report/types/report'

const route = useRoute()

const report = ref<AdminReportDetailResponse | null>(null)
const reviewNote = ref('')
const loading = ref(false)
const submitting = ref(false)
const needsRefresh = ref(false)
const loadError = ref('')
const actionError = ref('')
const resultMessage = ref('')

let contextSequence = 0
let loadSequence = 0

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

const adminId = computed(() => {
  const session = getAuthSession()
  return session?.role === 'ADMIN' ? session.memberId : null
})

const reportId = computed(() => {
  const value = route.params.reportId
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return null

  const id = Number(value)
  return Number.isSafeInteger(id) ? id : null
})

const listLocation = computed(() => ({
  name: 'admin-reports',
  query: {
    filter: route.query.filter,
    page: route.query.page,
  },
}))

const busy = computed(() => loading.value || submitting.value)

const canReview = computed(() =>
    adminId.value !== null &&
    report.value?.status === 'PENDING' &&
    !busy.value &&
    !needsRefresh.value,
)

const targetDeleted = computed(() => {
  const target = report.value
  if (!target) return false

  return target.postDeleted ||
      (target.commentId !== null && target.commentDeleted)
})

const targetBlinded = computed(() => {
  const target = report.value
  if (!target) return false

  return target.commentId === null
      ? target.postBlinded
      : target.commentBlinded
})

const canModerate = computed(() => {
  const target = report.value

  return adminId.value !== null &&
      target !== null &&
      !targetDeleted.value &&
      Number.isSafeInteger(target.targetVersion) &&
      target.targetVersion >= 0 &&
      !busy.value &&
      !needsRefresh.value
})

async function loadReport() {
  const context = contextSequence
  const sequence = ++loadSequence
  const id = reportId.value

  report.value = null
  loadError.value = ''
  needsRefresh.value = true
  loading.value = false

  if (adminId.value === null) {
    loadError.value = '관리자 계정으로 로그인해 주세요.'
    return
  }

  if (id === null) {
    loadError.value = '올바르지 않은 신고 번호입니다.'
    return
  }

  loading.value = true

  try {
    const response = await getAdminReport(id)

    if (context !== contextSequence || sequence !== loadSequence) return

    report.value = response
    needsRefresh.value = false
  } catch (error) {
    if (context !== contextSequence || sequence !== loadSequence) return

    if (error instanceof ApiError && error.status === 401) {
      loadError.value = '다시 로그인해 주세요.'
    } else if (error instanceof ApiError && error.status === 403) {
      loadError.value = '관리자 권한이 필요합니다.'
    } else if (error instanceof ApiError && error.status === 404) {
      loadError.value = '신고를 찾을 수 없습니다.'
    } else {
      loadError.value = '신고 정보를 불러오지 못했습니다. 다시 조회해 주세요.'
    }
  } finally {
    if (context === contextSequence && sequence === loadSequence) {
      loading.value = false
    }
  }
}

function refreshReport() {
  if (busy.value) return

  actionError.value = ''
  void loadReport()
}

async function submitReview(status: ReportReviewStatus) {
  const target = report.value
  if (!canReview.value || !target) return

  const note = reviewNote.value

  actionError.value = ''
  resultMessage.value = ''

  if (!note.trim()) {
    actionError.value = '처리 메모를 입력해 주세요.'
    return
  }

  if (note.length > 2000) {
    actionError.value = '처리 메모는 2,000자 이하여야 합니다.'
    return
  }

  const context = contextSequence
  submitting.value = true

  try {
    await reviewReport(target.id, {
      status,
      reviewNote: note,
    })

    if (context !== contextSequence) return

    needsRefresh.value = true
    reviewNote.value = ''
    resultMessage.value = status === 'ACCEPTED'
        ? '신고를 인정 처리했습니다.'
        : '신고를 반려 처리했습니다.'

    await loadReport()
  } catch (error) {
    if (context !== contextSequence) return

    if (error instanceof ApiError && error.status === 400) {
      actionError.value = '처리 결과와 메모를 확인해 주세요.'
      return
    }

    needsRefresh.value = true

    if (error instanceof ApiError && error.status === 401) {
      actionError.value = '다시 로그인해 주세요.'
    } else if (error instanceof ApiError && error.status === 403) {
      actionError.value = '관리자 권한을 확인한 뒤 다시 조회해 주세요.'
    } else if (error instanceof ApiError && error.status === 404) {
      actionError.value = '신고를 찾을 수 없습니다. 다시 조회해 주세요.'
    } else if (error instanceof ApiError && error.status === 409) {
      actionError.value = '이미 처리된 신고입니다. 다시 조회해 최신 결과를 확인해 주세요.'
    } else {
      actionError.value = '처리 결과를 확인하지 못했습니다. 다시 조회해 상태를 확인해 주세요.'
    }
  } finally {
    if (context === contextSequence) submitting.value = false
  }
}

async function submitTargetAction(action: ReportTargetAction) {
  const target = report.value
  if (!target || !canModerate.value) return

  if (
      (action === 'BLIND' && targetBlinded.value) ||
      (action === 'UNBLIND' && !targetBlinded.value)
  ) {
    return
  }

  const targetLabel = target.commentId === null ? '게시글' : '댓글'

  if (
      action === 'DELETE' &&
      !window.confirm(`${targetLabel}을 삭제할까요?`)
  ) {
    return
  }

  const context = contextSequence
  submitting.value = true
  actionError.value = ''
  resultMessage.value = ''

  try {
    await moderateReportTarget(target.id, {
      action,
      version: target.targetVersion,
    })

    if (context !== contextSequence) return

    needsRefresh.value = true

    const messages: Record<ReportTargetAction, string> = {
      BLIND: '블라인드 처리했습니다.',
      UNBLIND: '블라인드를 해제했습니다.',
      DELETE: '삭제 처리했습니다.',
    }
    resultMessage.value = messages[action]

    await loadReport()
  } catch (error) {
    if (context !== contextSequence) return

    needsRefresh.value = true

    const messages: Partial<Record<number, string>> = {
      400: '대상 정보를 다시 조회한 뒤 시도해 주세요.',
      401: '다시 로그인해 주세요.',
      403: '요청 권한을 확인한 뒤 다시 조회해 주세요.',
      404: '신고 또는 대상을 찾을 수 없습니다. 다시 조회해 주세요.',
      409: '대상 상태가 변경되었습니다. 다시 조회해 최신 상태를 확인해 주세요.',
    }
    const fallback =
        '조치 결과를 확인하지 못했습니다. 다시 조회해 상태를 확인해 주세요.'

    actionError.value = error instanceof ApiError
        ? messages[error.status] ?? fallback
        : fallback
  } finally {
    if (context === contextSequence) {
      submitting.value = false
    }
  }
}

function formatDate(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}

watch(
    [reportId, adminId],
    () => {
      contextSequence++
      submitting.value = false
      reviewNote.value = ''
      actionError.value = ''
      resultMessage.value = ''
      void loadReport()
    },
    {immediate: true, flush: 'sync'},
)

onBeforeUnmount(() => {
  contextSequence++
  loadSequence++
})
</script>

<template>
  <div class="grid gap-5" :aria-busy="busy">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <p class="text-xs font-medium text-blue-600">관리자</p>
        <h1 class="mt-1 text-2xl font-semibold tracking-tight text-slate-900">
          신고 상세
        </h1>
      </div>

      <div class="flex items-center gap-2">
        <BaseButton
            variant="secondary"
            size="sm"
            :disabled="busy || adminId === null"
            @click="refreshReport"
        >
          다시 조회
        </BaseButton>

        <RouterLink
            :to="listLocation"
            class="inline-flex h-8 items-center rounded-md border border-slate-200 bg-white px-3 text-xs font-medium text-slate-700 hover:bg-slate-50"
        >
          목록으로
        </RouterLink>
      </div>
    </header>

    <p
        v-if="resultMessage"
        role="status"
        class="rounded-md border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700"
    >
      {{ resultMessage }}
    </p>

    <p
        v-if="actionError"
        role="alert"
        class="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
    >
      {{ actionError }}
    </p>

    <p
        v-if="loadError"
        role="alert"
        class="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
    >
      {{ loadError }}
    </p>

    <p v-else-if="loading" class="py-12 text-center text-sm text-slate-500">
      신고 정보를 불러오는 중입니다.
    </p>

    <template v-else-if="report">
      <section class="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <header class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-5 py-4">
          <h2 class="font-semibold text-slate-900">
            신고 #{{ report.id }}
          </h2>
          <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-700">
            {{ statusLabels[report.status] }}
          </span>
        </header>

        <div class="grid gap-5 p-5">
          <dl class="grid gap-4 text-sm sm:grid-cols-2">
            <div>
              <dt class="text-slate-500">신고 대상</dt>
              <dd class="mt-1 font-medium text-slate-800">
                {{ report.commentId === null ? '게시글' : '댓글' }}
              </dd>
            </div>
            <div>
              <dt class="text-slate-500">게시판</dt>
              <dd class="mt-1 text-slate-800">{{ report.boardName }}</dd>
            </div>
            <div>
              <dt class="text-slate-500">신고자</dt>
              <dd class="mt-1 text-slate-800">{{ report.reporterDisplayName }}</dd>
            </div>
            <div>
              <dt class="text-slate-500">신고일</dt>
              <dd class="mt-1 text-slate-800">{{ formatDate(report.createdAt) }}</dd>
            </div>
            <div>
              <dt class="text-slate-500">신고 사유</dt>
              <dd class="mt-1 text-slate-800">{{ reasonLabels[report.reason] }}</dd>
            </div>
          </dl>

          <div>
            <h3 class="text-sm font-medium text-slate-700">상세 사유</h3>
            <p class="mt-2 whitespace-pre-wrap break-all rounded-md bg-slate-50 p-4 text-sm text-slate-700">
              {{ report.detail || '입력된 상세 사유가 없습니다.' }}
            </p>
          </div>
        </div>
      </section>

      <section class="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <header class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-5 py-4">
          <h2 class="font-semibold text-slate-900">현재 원문</h2>

          <RouterLink
              v-if="report.boardStatus === 'ACTIVE' && !report.postDeleted"
              :to="{
              name: 'post-detail',
              params: {
                boardSlug: report.boardSlug,
                postId: report.postId,
              },
            }"
              class="text-xs font-medium text-blue-600 hover:underline"
          >
            게시글 열기
          </RouterLink>
        </header>

        <div class="grid gap-4 p-5">
          <h3 class="break-all text-lg font-semibold text-slate-900">
            {{ report.postTitle }}
          </h3>

          <div class="flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-500">
            <span>대상 작성자 {{ report.authorDisplayName }}</span>
            <span>작성 {{ formatDate(report.contentCreatedAt) }}</span>
            <span
                v-if="report.contentUpdatedAt &&
                report.contentUpdatedAt !== report.contentCreatedAt"
            >
              수정 {{ formatDate(report.contentUpdatedAt) }}
            </span>
          </div>

          <div class="flex flex-wrap gap-2 text-xs">
            <span
                v-if="report.postBlinded"
                class="rounded bg-amber-50 px-2 py-1 text-amber-700"
            >
            게시글 블라인드
            </span>

            <span
                v-if="report.commentId !== null && report.commentBlinded"
                class="rounded bg-amber-50 px-2 py-1 text-amber-700"
            >
            댓글 블라인드
            </span>
            <span
                v-if="report.boardStatus === 'ARCHIVED'"
                class="rounded bg-slate-100 px-2 py-1 text-slate-600"
            >
              보관된 게시판
            </span>
            <span
                v-if="report.postDeleted"
                class="rounded bg-red-50 px-2 py-1 text-red-600"
            >
              게시글 삭제됨
            </span>
            <span
                v-if="report.commentDeleted"
                class="rounded bg-red-50 px-2 py-1 text-red-600"
            >
              댓글 삭제됨
            </span>
          </div>

          <p class="text-xs leading-5 text-slate-500">
            현재 저장된 텍스트이며 신고 당시 내용과 다를 수 있습니다.
            이 화면에서는 본문 이미지와 첨부파일을 표시하지 않습니다.
          </p>

          <div
              class="min-h-32 whitespace-pre-wrap break-all rounded-md border border-slate-200 bg-slate-50 p-4 text-sm leading-7 text-slate-800">
            {{ report.content || '텍스트 본문이 없습니다.' }}
          </div>
        </div>
      </section>

      <section class="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <header
            class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-5 py-4"
        >
          <h2 class="font-semibold text-slate-900">대상 관리</h2>

          <span
              class="rounded-full px-3 py-1 text-xs font-medium"
              :class="targetDeleted
        ? 'bg-slate-100 text-slate-600'
        : targetBlinded
          ? 'bg-amber-50 text-amber-700'
          : 'bg-green-50 text-green-700'"
          >
      {{ targetDeleted ? '삭제됨' : targetBlinded ? '블라인드' : '정상' }}
    </span>
        </header>

        <div class="grid gap-4 p-5">
          <p class="text-sm leading-6 text-slate-600">
            신고 대상 {{ report.commentId === null ? '게시글' : '댓글' }}에 적용합니다.
            이 조치는 신고의 인정·반려 결과를 변경하지 않습니다.
          </p>

          <p
              v-if="targetDeleted"
              class="rounded-md bg-slate-50 px-4 py-3 text-sm text-slate-500"
          >
            삭제된 대상은 이력만 조회할 수 있습니다.
          </p>

          <template v-else>
            <fieldset
                :disabled="!canModerate"
                class="flex min-w-0 flex-wrap justify-end gap-2"
            >
              <BaseButton
                  v-if="!targetBlinded"
                  variant="secondary"
                  size="sm"
                  :disabled="!canModerate"
                  @click="submitTargetAction('BLIND')"
              >
                블라인드
              </BaseButton>

              <BaseButton
                  v-else
                  variant="secondary"
                  size="sm"
                  :disabled="!canModerate"
                  @click="submitTargetAction('UNBLIND')"
              >
                블라인드 해제
              </BaseButton>

              <BaseButton
                  variant="danger"
                  size="sm"
                  :disabled="!canModerate"
                  @click="submitTargetAction('DELETE')"
              >
                삭제
              </BaseButton>
            </fieldset>

            <p v-if="needsRefresh" class="text-xs text-amber-700">
              다시 조회해 최신 상태를 확인한 뒤 조치해 주세요.
            </p>
          </template>
        </div>
      </section>

      <section class="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <header class="border-b border-slate-200 px-5 py-4">
          <h2 class="font-semibold text-slate-900">처리 결과</h2>
        </header>
        <div class="grid gap-4 p-5">
          <template v-if="report.status === 'PENDING'">
            <p class="text-sm text-slate-500">
              인정·반려 모두 처리 메모가 필요합니다.
              인정 건수가 기준에 도달하면 대상이 자동 블라인드됩니다.
              인정 처리는 게시글 삭제나 회원 제재를 자동으로 수행하지 않습니다.
            </p>

            <fieldset :disabled="!canReview" class="grid min-w-0 gap-4">
              <BaseTextarea
                  id="report-review-note"
                  v-model="reviewNote"
                  label="처리 메모"
                  placeholder="판단 사유를 입력해 주세요."
                  :max-length="2000"
                  :rows="5"
              />

              <div class="flex justify-end gap-2">
                <BaseButton
                    variant="secondary"
                    size="sm"
                    :disabled="!canReview"
                    @click="submitReview('REJECTED')"
                >
                  반려 처리
                </BaseButton>
                <BaseButton
                    size="sm"
                    :disabled="!canReview"
                    @click="submitReview('ACCEPTED')"
                >
                  인정 처리
                </BaseButton>
              </div>
            </fieldset>

            <p v-if="submitting" role="status" class="text-sm text-slate-500">
              처리 중입니다.
            </p>
          </template>

          <template v-else>
            <div class="flex flex-wrap gap-x-4 gap-y-1 text-sm text-slate-600">
              <span>처리자 {{ report.reviewerDisplayName ?? '-' }}</span>
              <span>처리일 {{ formatDate(report.reviewedAt) }}</span>
            </div>
            <p class="whitespace-pre-wrap break-all rounded-md bg-slate-50 p-4 text-sm leading-6 text-slate-700">
              {{ report.reviewNote ?? '-' }}
            </p>
          </template>
        </div>
      </section>
    </template>
  </div>
</template>