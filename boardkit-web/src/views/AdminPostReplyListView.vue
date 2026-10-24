<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {RouterLink, useRoute, useRouter} from 'vue-router'

import {ApiError} from '@/api/http'
import {getAdminPostReplyTargets} from '@/api/postReplyApi'
import BaseButton from '@/components/ui/BaseButton.vue'
import BasePagination from '@/components/ui/BasePagination.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import {getAuthSession} from '@/features/auth/services/authSession'
import type {
  AdminPostReplyPageResponse,
  PostReplyFilter,
} from '@/features/post/types/postReply'

const route = useRoute()
const router = useRouter()

const result = ref<AdminPostReplyPageResponse | null>(null)
const loading = ref(false)
const errorMessage = ref('')
let requestSequence = 0

const filterOptions = [
  {value: 'UNANSWERED', label: '미답변'},
  {value: 'ANSWERED', label: '답변 있음'},
  {value: 'ALL', label: '전체'},
] as const

const adminId = computed(() => {
  const session = getAuthSession()
  return session?.role === 'ADMIN' ? session.memberId : null
})

const selectedFilter = computed<PostReplyFilter>(() => {
  const value = route.query.filter
  return value === 'ALL' || value === 'ANSWERED' ? value : 'UNANSWERED'
})

const requestedPage = computed(() => {
  const value = route.query.page
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return 1

  const page = Number(value)
  return Number.isSafeInteger(page) && page <= 2_147_483_647 ? page : 1
})

async function loadTargets(
    filter: PostReplyFilter,
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
    const response = await getAdminPostReplyTargets({filter, page, size: 10})

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
      errorMessage.value = '공식 답변 관리 목록을 불러오지 못했습니다.'
    }
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

function changeFilter(value: string) {
  if (
      value !== 'ALL' &&
      value !== 'UNANSWERED' &&
      value !== 'ANSWERED'
  ) return

  if (value === selectedFilter.value) return

  void router.push({
    query: {
      ...route.query,
      filter: value === 'UNANSWERED' ? undefined : value,
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

function reloadTargets() {
  if (loading.value) return
  void loadTargets(selectedFilter.value, requestedPage.value, adminId.value)
}

watch(
    [selectedFilter, requestedPage, adminId],
    ([filter, page, memberId]) => {
      void loadTargets(filter, page, memberId)
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
        <p class="text-xs font-medium text-primary">관리자</p>
        <h1 class="mt-1 text-2xl font-semibold tracking-tight text-zinc-900">
          공식 답변 관리
        </h1>
        <p class="mt-2 text-sm text-zinc-500">
          게시글을 선택해 공식 답변을 작성하거나 관리하세요.
        </p>
      </div>

      <RouterLink
          :to="{name: 'admin-dashboard'}"
          class="rounded-md border border-zinc-200 px-3 py-2 text-xs font-medium text-zinc-700 hover:bg-zinc-50"
      >
        관리자 메뉴
      </RouterLink>
    </header>

    <section
        class="overflow-hidden rounded-xl border border-border bg-card shadow-xs"
        :aria-busy="loading"
    >
      <header class="flex flex-wrap items-end justify-between gap-4 border-b border-zinc-200 px-5 py-4">
        <div class="w-40">
          <BaseSelect
              id="reply-filter"
              label="답변 유무"
              :model-value="selectedFilter"
              :options="filterOptions"
              @update:model-value="changeFilter"
          />
        </div>

        <div class="flex items-center gap-3">
          <p v-if="result" class="text-sm text-zinc-500">
            전체 {{ result.totalElements.toLocaleString() }}개
          </p>
          <BaseButton
              variant="secondary"
              size="sm"
              :disabled="loading || adminId === null"
              @click="reloadTargets"
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

      <p v-else-if="loading" class="px-5 py-12 text-center text-sm text-zinc-500">
        목록을 불러오는 중입니다.
      </p>

      <template v-else-if="result">
        <p
            v-if="result.posts.length === 0"
            class="px-5 py-12 text-center text-sm text-zinc-500"
        >
          조건에 해당하는 게시글이 없습니다.
        </p>

        <template v-else>
          <div class="overflow-x-auto">
            <table class="w-full min-w-[760px] table-fixed text-sm">
              <caption class="sr-only">관리자 공식 답변 대상 게시글</caption>

              <thead class="border-b border-zinc-200 bg-zinc-50 text-zinc-600">
              <tr>
                <th scope="col" class="px-5 py-3 text-left font-medium">제목</th>
                <th scope="col" class="w-32 px-3 py-3 text-left font-medium">게시판</th>
                <th scope="col" class="w-28 px-3 py-3 text-left font-medium">작성자</th>
                <th scope="col" class="w-36 px-3 py-3 text-left font-medium">게시글 작성일</th>
                <th scope="col" class="w-28 px-3 py-3 text-center font-medium">답변</th>
              </tr>
              </thead>

              <tbody class="divide-y divide-zinc-100">
              <tr
                  v-for="post in result.posts"
                  :key="post.postId"
                  class="hover:bg-zinc-50"
              >
                <td class="px-5 py-3">
                  <RouterLink
                      :to="{
                        name: 'post-detail',
                        params: {
                          boardSlug: post.boardSlug,
                          postId: post.postId,
                        },
                      }"
                      class="block truncate font-medium text-zinc-800 hover:text-primary hover:underline"
                  >
                      <span v-if="post.secret" class="mr-1 text-xs text-zinc-500">
                        [비밀]
                      </span>
                    {{ post.title }}
                  </RouterLink>
                </td>

                <td class="px-3 py-3 text-zinc-600">
                  <span class="block truncate">{{ post.boardName }}</span>
                  <span
                      v-if="!post.officialReplyEnabled"
                      class="mt-1 inline-block rounded bg-zinc-100 px-1.5 py-0.5 text-xs text-zinc-500"
                  >
                      조회 전용
                    </span>
                </td>

                <td class="truncate px-3 py-3 text-zinc-600">
                  {{ post.authorDisplayName }}
                </td>

                <td class="px-3 py-3 text-xs text-zinc-500">
                  {{ post.createdAt.replace('T', ' ').slice(0, 16) }}
                </td>

                <td class="px-3 py-3 text-center">
                    <span
                        class="inline-flex rounded-full px-2 py-1 text-xs font-medium"
                        :class="post.replyId === null
                        ? 'bg-amber-50 text-amber-700'
                        : 'bg-accent text-primary'"
                    >
                      {{ post.replyId === null ? '미답변' : '답변 있음' }}
                    </span>
                </td>
              </tr>
              </tbody>
            </table>
          </div>

          <BasePagination
              aria-label="공식 답변 관리 페이지"
              :page="result.page"
              :total-pages="result.totalPages"
              @change="changePage"
          />
        </template>
      </template>
    </section>
  </div>
</template>