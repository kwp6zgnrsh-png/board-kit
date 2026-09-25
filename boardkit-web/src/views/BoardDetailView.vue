<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getBoardDetail } from '@/api/boardApi'
import { ApiError } from '@/api/http'
import { getPosts } from '@/api/postApi'
import BasePagination from '@/components/ui/BasePagination.vue'
import { getAuthSession } from '@/features/auth/services/authSession'
import BoardHero from '@/features/board/components/BoardHero.vue'
import BoardPolicyPanel from '@/features/board/components/BoardPolicyPanel.vue'
import type { BoardDetailResponse } from '@/features/board/types/board'
import PostList from '@/features/post/components/PostList.vue'
import type { PostPageResponse } from '@/features/post/types/post'

const route = useRoute()
const router = useRouter()

const board = ref<BoardDetailResponse | null>(null)
const postPage = ref<PostPageResponse | null>(null)
const boardLoading = ref(false)
const postsLoading = ref(false)
const boardErrorMessage = ref('')
const postsErrorMessage = ref('')
const session = getAuthSession()

const boardSlug = computed(
  () => String(route.params.boardSlug ?? ''),
)

const requestedPage = computed(() => {
  const queryValue = Array.isArray(route.query.page)
    ? route.query.page[0]
    : route.query.page
  const parsedPage = Number(queryValue)

  return Number.isInteger(parsedPage) && parsedPage > 0
    ? parsedPage
    : 1
})

const canWrite = computed(() => {
  if (!board.value || !session) {
    return false
  }

  return (
    board.value.policy.writePermission === 'MEMBER' ||
    session.role === 'ADMIN'
  )
})

let boardRequestSequence = 0
let postsRequestSequence = 0

async function loadBoard(slug: string) {
  const currentRequest = ++boardRequestSequence

  boardLoading.value = true
  boardErrorMessage.value = ''
  board.value = null

  try {
    const response = await getBoardDetail(slug)

    if (currentRequest === boardRequestSequence) {
      board.value = response
    }
  } catch (error) {
    if (currentRequest !== boardRequestSequence) return

    if (error instanceof ApiError && error.status === 404) {
      boardErrorMessage.value = '게시판을 찾을 수 없습니다.'
    } else if (error instanceof ApiError && error.status === 403) {
      boardErrorMessage.value = '게시판을 조회할 권한이 없습니다.'
    } else {
      boardErrorMessage.value =
        error instanceof Error
          ? error.message
          : '게시판을 불러오지 못했습니다.'
    }
  } finally {
    if (currentRequest === boardRequestSequence) {
      boardLoading.value = false
    }
  }
}

async function loadPosts(slug: string, page: number) {
  const currentRequest = ++postsRequestSequence

  postsLoading.value = true
  postsErrorMessage.value = ''
  postPage.value = null

  try {
    const response = await getPosts(slug, page, 10)

    if (currentRequest !== postsRequestSequence) return

    const normalizedPage =
      response.totalPages === 0
        ? 1
        : Math.min(page, response.totalPages)

    if (page !== normalizedPage) {
      await router.replace({
        name: 'board-detail',
        params: { boardSlug: slug },
        query: {
          ...route.query,
          page: String(normalizedPage),
        },
      })
      return
    }

    postPage.value = response
  } catch (error) {
    if (currentRequest !== postsRequestSequence) return

    if (error instanceof ApiError && error.status === 404) {
      postsErrorMessage.value = '게시판을 찾을 수 없습니다.'
    } else if (error instanceof ApiError && error.status === 403) {
      postsErrorMessage.value = '게시글 목록을 조회할 권한이 없습니다.'
    } else {
      postsErrorMessage.value =
        error instanceof Error
          ? error.message
          : '게시글 목록을 불러오지 못했습니다.'
    }
  } finally {
    if (currentRequest === postsRequestSequence) {
      postsLoading.value = false
    }
  }
}

function changePage(page: number) {
  router.push({
    name: 'board-detail',
    params: { boardSlug: boardSlug.value },
    query: {
      ...route.query,
      page: String(page),
    },
  })
}

watch(boardSlug, loadBoard, { immediate: true })

watch(
  [boardSlug, requestedPage],
  ([slug, page]) => loadPosts(slug, page),
  { immediate: true },
)
</script>

<template>
  <div class="grid gap-5 lg:gap-6">
    <div
      v-if="boardLoading"
      class="rounded-lg border-[3px] border-slate-950 bg-white px-5 py-16 text-center font-black text-slate-600"
    >
      게시판을 불러오는 중입니다.
    </div>

    <div
      v-else-if="boardErrorMessage"
      class="rounded-lg border-[3px] border-red-950 bg-red-100 px-5 py-12 text-center font-black text-red-900"
      role="alert"
    >
      {{ boardErrorMessage }}
    </div>

    <template v-else-if="board">
      <BoardHero
        :name="board.name"
        :slug="board.slug"
        :description="board.description"
      />

      <div class="grid items-start gap-5 lg:grid-cols-[280px_minmax(0,1fr)]">
        <BoardPolicyPanel :board="board" />

        <section class="min-w-0 overflow-hidden rounded-lg border-[3px] border-slate-950 bg-white">
          <header class="flex flex-wrap items-center justify-between gap-4 px-4 py-4 sm:px-5">
            <div class="flex items-baseline gap-2">
              <span class="text-sm font-black text-slate-950">전체</span>
              <strong v-if="postPage" class="text-xl font-black text-blue-600">
                {{ postPage.totalElements.toLocaleString() }}
              </strong>
              <span class="text-sm font-black text-slate-950">개의 글</span>
            </div>

            <div class="flex flex-wrap items-center gap-2">
              <RouterLink
                  v-if="canWrite"
                  :to="{
                    name: 'post-create',
                    params: { boardSlug: board.slug },
                  }"
                  class="inline-flex items-center gap-2 rounded border-[3px] border-slate-950 bg-blue-500 px-4 py-2.5 text-sm font-black text-white no-underline shadow-[2px_2px_0_#0f172a] transition hover:-translate-y-0.5 hover:bg-blue-600"
              >
                <span aria-hidden="true">✎</span>
                글쓰기
              </RouterLink>
            </div>
          </header>

          <p v-if="postsLoading" class="m-4 rounded bg-slate-100 px-4 py-14 text-center text-sm font-bold text-slate-500">
            게시글을 불러오는 중입니다.
          </p>

          <p
            v-else-if="postsErrorMessage"
            class="m-4 rounded bg-red-100 px-4 py-12 text-center text-sm font-black text-red-900"
            role="alert"
          >
            {{ postsErrorMessage }}
          </p>

          <template v-else-if="postPage">
            <PostList
              :board-slug="board.slug"
              :page="postPage.page"
              :posts="postPage.posts"
            />

            <BasePagination
              :page="postPage.page"
              :total-pages="postPage.totalPages"
              @change="changePage"
            />
          </template>
        </section>
      </div>
    </template>
  </div>
</template>
