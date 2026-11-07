<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {RouterLink, useRoute, useRouter} from 'vue-router'

import {
  getAdminBlindedComments,
  getAdminBlindedPosts,
  moderateBlindedComment,
  moderateBlindedPost,
} from '@/api/adminBlindApi'
import {ApiError} from '@/api/http'
import BaseButton from '@/components/ui/BaseButton.vue'
import BasePagination from '@/components/ui/BasePagination.vue'
import {getAuthSession} from '@/features/auth/services/authSession'
import type {
  AdminBlindedPostSummaryResponse,
} from '@/features/report/types/adminBlind'

type BlindTab = 'posts' | 'comments'
type BlindAction = 'UNBLIND' | 'DELETE'

type BlindRow = AdminBlindedPostSummaryResponse & {
  commentId: number | null
  contentPreview?: string
}

const route = useRoute()
const router = useRouter()

const rows = ref<BlindRow[]>([])
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const submitting = ref(false)
const needsRefresh = ref(true)
const loadError = ref('')
const actionError = ref('')
const resultMessage = ref('')

let contextSequence = 0
let loadSequence = 0

const adminId = computed(() => {
  const session = getAuthSession()
  return session?.role === 'ADMIN' ? session.memberId : null
})

const selectedTab = computed<BlindTab>(() =>
    route.query.tab === 'comments' ? 'comments' : 'posts',
)

const requestedPage = computed(() => {
  const value = route.query.page
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return 1

  const page = Number(value)
  return Number.isSafeInteger(page) && page <= 2_147_483_647 ? page : 1
})

const busy = computed(() => loading.value || submitting.value)

const canModerate = computed(() =>
    adminId.value !== null &&
    !busy.value &&
    !needsRefresh.value,
)

async function loadTargets() {
  const context = contextSequence
  const sequence = ++loadSequence
  const tab = selectedTab.value
  const page = requestedPage.value

  rows.value = []
  totalElements.value = 0
  totalPages.value = 0
  loadError.value = ''
  needsRefresh.value = true
  loading.value = false

  if (adminId.value === null) {
    loadError.value = '관리자 계정으로 로그인해 주세요.'
    return
  }

  loading.value = true

  try {
    const response = tab === 'posts'
        ? await getAdminBlindedPosts({page, size: 10})
        : await getAdminBlindedComments({page, size: 10})

    if (context !== contextSequence || sequence !== loadSequence) return

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

    rows.value = 'posts' in response
        ? response.posts.map(post => ({
          ...post,
          commentId: null,
        }))
        : response.comments.map(comment => ({
          ...comment,
          title: comment.postTitle,
        }))

    totalElements.value = response.totalElements
    totalPages.value = response.totalPages
    needsRefresh.value = false
  } catch (error) {
    if (context !== contextSequence || sequence !== loadSequence) return

    if (error instanceof ApiError && error.status === 401) {
      loadError.value = '다시 로그인해 주세요.'
    } else if (error instanceof ApiError && error.status === 403) {
      loadError.value = '관리자 권한이 필요합니다.'
    } else {
      loadError.value = '블라인드 목록을 불러오지 못했습니다. 다시 조회해 주세요.'
    }
  } finally {
    if (context === contextSequence && sequence === loadSequence) {
      loading.value = false
    }
  }
}

function changeTab(tab: BlindTab) {
  if (busy.value || tab === selectedTab.value) return

  void router.push({
    query: {
      ...route.query,
      tab: tab === 'posts' ? undefined : tab,
      page: undefined,
    },
  })
}

function changePage(page: number) {
  if (
      busy.value ||
      page < 1 ||
      page > totalPages.value ||
      page === requestedPage.value
  ) return

  resultMessage.value = ''

  void router.push({
    query: {
      ...route.query,
      page: page === 1 ? undefined : String(page),
    },
  })
}

function refreshTargets() {
  if (busy.value) return
  actionError.value = ''
  void loadTargets()
}

async function moderate(row: BlindRow, action: BlindAction) {
  if (
      !canModerate.value ||
      !Number.isSafeInteger(row.version) ||
      row.version < 0
  ) return

  const label = row.commentId === null ? '게시글' : '댓글'

  if (action === 'DELETE' && !window.confirm(`${label}을 삭제할까요?`)) {
    return
  }

  const context = contextSequence
  submitting.value = true
  actionError.value = ''
  resultMessage.value = ''

  try {
    const request = {action, version: row.version}

    if (row.commentId === null) {
      await moderateBlindedPost(row.postId, request)
    } else {
      await moderateBlindedComment(row.postId, row.commentId, request)
    }

    if (context !== contextSequence) return

    resultMessage.value = action === 'UNBLIND'
        ? `${label}의 블라인드를 해제했습니다.`
        : `${label}을 삭제했습니다.`

    await loadTargets()
  } catch (error) {
    if (context !== contextSequence) return

    needsRefresh.value = true

    if (error instanceof ApiError && error.status === 401) {
      actionError.value = '다시 로그인해 주세요.'
    } else if (error instanceof ApiError && error.status === 403) {
      actionError.value = '관리자 권한을 확인한 뒤 다시 조회해 주세요.'
    } else if (error instanceof ApiError && error.status === 404) {
      actionError.value = '대상을 찾을 수 없습니다. 다시 조회해 주세요.'
    } else if (error instanceof ApiError && error.status === 409) {
      actionError.value = '대상 상태가 변경되었습니다. 다시 조회한 뒤 처리해 주세요.'
    } else {
      actionError.value = '처리 결과를 확인하지 못했습니다. 다시 조회해 상태를 확인해 주세요.'
    }
  } finally {
    if (context === contextSequence) submitting.value = false
  }
}

watch(
    [selectedTab, requestedPage, adminId],
    ([tab, , memberId], previous) => {
      contextSequence++
      submitting.value = false
      actionError.value = ''

      if (tab !== previous[0] || memberId !== previous[2]) {
        resultMessage.value = ''
      }

      void loadTargets()
    },
    {immediate: true, flush: 'sync'},
)

onBeforeUnmount(() => {
  contextSequence++
  loadSequence++
})
</script>

<template>
  <div class="grid gap-8">
    <header class="page-heading">
      <div>
        <p class="text-xs font-medium text-primary">관리자</p>
        <h1 class="page-title">
          블라인드 관리
        </h1>
        <p class="mt-2 text-sm text-zinc-500">
          블라인드된 게시글과 댓글을 확인하고 해제하거나 삭제합니다.
        </p>
      </div>

      <RouterLink
          :to="{name: 'admin-dashboard'}"
          class="rounded-none border border-zinc-200 px-3 py-2 text-xs font-medium text-zinc-700 hover:bg-zinc-50"
      >
        관리자 메뉴
      </RouterLink>
    </header>

    <p
        v-if="resultMessage"
        role="status"
        class="rounded-none border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700"
    >
      {{ resultMessage }}
    </p>

    <p
        v-if="actionError"
        role="alert"
        class="rounded-none border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
    >
      {{ actionError }}
    </p>

    <section
        class="overflow-hidden surface-panel"
        :aria-busy="busy"
    >
      <header class="flex flex-wrap items-center justify-between gap-3 border-b border-zinc-200 px-5 py-4">
        <nav class="flex gap-2" aria-label="블라인드 대상 종류">
          <button
              v-for="tab in (['posts', 'comments'] as const)"
              :key="tab"
              type="button"
              :disabled="busy"
              :aria-pressed="selectedTab === tab"
              class="h-9 rounded-none border px-4 text-sm font-medium disabled:opacity-50"
              :class="selectedTab === tab
                ? 'border-primary bg-primary text-white'
                : 'border-zinc-200 bg-white text-zinc-600 hover:bg-zinc-50'"
              @click="changeTab(tab)"
          >
            {{ tab === 'posts' ? '게시글' : '댓글' }}
          </button>
        </nav>

        <div class="flex items-center gap-3">
          <span v-if="!loading && !loadError" class="text-sm text-zinc-500">
            전체 {{ totalElements.toLocaleString() }}개
          </span>
          <BaseButton
              variant="secondary"
              size="sm"
              :disabled="busy || adminId === null"
              @click="refreshTargets"
          >
            새로고침
          </BaseButton>
        </div>
      </header>

      <p
          v-if="loadError"
          role="alert"
          class="m-5 rounded-none border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
      >
        {{ loadError }}
      </p>

      <p v-else-if="loading" class="px-5 py-12 text-center text-sm text-zinc-500">
        목록을 불러오는 중입니다.
      </p>

      <p v-else-if="rows.length === 0" class="px-5 py-12 text-center text-sm text-zinc-500">
        블라인드된 {{ selectedTab === 'posts' ? '게시글' : '댓글' }}이 없습니다.
      </p>

      <div v-else class="overflow-x-auto">
        <table class="w-full min-w-[960px] table-fixed text-sm">
          <caption class="sr-only">블라인드 관리 목록</caption>
          <thead class="border-b border-zinc-200 bg-zinc-50 text-zinc-600">
          <tr>
            <th scope="col" class="w-20 px-3 py-3 text-center font-medium">번호</th>
            <th scope="col" class="px-4 py-3 text-left font-medium">대상</th>
            <th scope="col" class="w-32 px-3 py-3 text-left font-medium">게시판</th>
            <th scope="col" class="w-28 px-3 py-3 text-left font-medium">작성자</th>
            <th scope="col" class="w-36 px-3 py-3 text-left font-medium">블라인드일</th>
            <th scope="col" class="w-48 px-3 py-3 text-center font-medium">관리</th>
          </tr>
          </thead>

          <tbody class="divide-y divide-zinc-100">
          <tr v-for="row in rows" :key="row.commentId ?? row.postId" class="hover:bg-zinc-50">
            <td class="px-3 py-3 text-center text-zinc-500">
              {{ row.commentId ?? row.postId }}
            </td>

            <td class="px-4 py-3">
              <p class="truncate font-medium text-zinc-800">{{ row.title }}</p>
              <p
                  v-if="row.commentId !== null"
                  class="mt-1 line-clamp-2 whitespace-pre-wrap break-words text-xs text-zinc-500"
              >
                {{ row.contentPreview }}
              </p>
              <RouterLink
                  v-if="row.boardStatus === 'ACTIVE' || row.boardStatus === 'ARCHIVED'"
                  :to="{
                    name: 'post-detail',
                    params: {boardSlug: row.boardSlug, postId: row.postId},
                  }"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="mt-2 inline-block text-xs font-medium text-primary hover:underline"
              >
                원글 보기 ↗
              </RouterLink>
            </td>

            <td class="px-3 py-3 text-zinc-600">
              <span class="block truncate">{{ row.boardName }}</span>
              <span v-if="row.boardStatus === 'ARCHIVED'" class="mt-1 block text-xs text-zinc-500">
                보관된 게시판
              </span>
            </td>

            <td class="truncate px-3 py-3 text-zinc-600">
              {{ row.authorDisplayName }}
            </td>

            <td class="px-3 py-3 text-xs text-zinc-500">
              {{ row.blindedAt.replace('T', ' ').slice(0, 16) }}
            </td>

            <td class="px-3 py-3">
              <div class="flex justify-center gap-2">
                <BaseButton
                    variant="secondary"
                    size="sm"
                    :disabled="!canModerate"
                    @click="moderate(row, 'UNBLIND')"
                >
                  해제
                </BaseButton>
                <BaseButton
                    variant="danger"
                    size="sm"
                    :disabled="!canModerate"
                    @click="moderate(row, 'DELETE')"
                >
                  삭제
                </BaseButton>
              </div>
            </td>
          </tr>
          </tbody>
        </table>
      </div>

      <BasePagination
          v-if="!busy && !loadError && totalPages > 1"
          aria-label="블라인드 목록 페이지"
          :page="requestedPage"
          :total-pages="totalPages"
          @change="changePage"
      />
    </section>
  </div>
</template>