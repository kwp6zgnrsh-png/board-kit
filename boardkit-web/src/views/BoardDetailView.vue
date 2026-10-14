<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'

import {getBoardDetail} from '@/api/boardApi'
import {ApiError} from '@/api/http'
import {getPosts} from '@/api/postApi'
import BasePagination from '@/components/ui/BasePagination.vue'
import CommunityIcon from '@/components/ui/CommunityIcon.vue'
import {getAuthSession} from '@/features/auth/services/authSession'
import BoardHero from '@/features/board/components/BoardHero.vue'
import BoardPolicyPanel from '@/features/board/components/BoardPolicyPanel.vue'
import type {BoardDetailResponse} from '@/features/board/types/board'
import PostList from '@/features/post/components/PostList.vue'
import PostCardList from '@/features/post/components/PostCardList.vue'
import type {PostPageResponse, PostSort} from '@/features/post/types/post'

const route = useRoute()
const router = useRouter()

const board = ref<BoardDetailResponse | null>(null)
const postPage = ref<PostPageResponse | null>(null)
const boardLoading = ref(false)
const postsLoading = ref(false)
const boardErrorMessage = ref('')
const postsErrorMessage = ref('')
const session = computed(() => getAuthSession())

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

const postSortValues: PostSort[] = [
  'LATEST',
  'OLDEST',
  'VIEWS',
]

function getQueryValue(value: unknown): string | undefined {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string'
        ? value[0]
        : undefined
  }

  return typeof value === 'string'
      ? value
      : undefined
}

const requestedKeyword = computed(
    () => getQueryValue(route.query.keyword)?.trim() ?? '',
)

const requestedCategoryId = computed(() => {
  const value = getQueryValue(route.query.categoryId)

  if (!value) {
    return undefined
  }

  const categoryId = Number(value)

  return Number.isInteger(categoryId) && categoryId > 0
      ? categoryId
      : undefined
})

const requestedSort = computed<PostSort>(() => {
  const value = getQueryValue(route.query.sort)

  return postSortValues.includes(value as PostSort)
      ? value as PostSort
      : 'LATEST'
})

const searchKeyword = ref(requestedKeyword.value)

const selectedCategoryId = computed({
  get: () => requestedCategoryId.value?.toString() ?? '',
  set: (value: string) => {
    updateListQuery({
      categoryId: value || undefined,
    })
  },
})

const selectedSort = computed<PostSort>({
  get: () => requestedSort.value,
  set: (value) => {
    updateListQuery({
      sort: value === 'LATEST'
          ? undefined
          : value,
    })
  },
})

const hasActiveFilters = computed(
    () =>
        requestedKeyword.value.length > 0 ||
        requestedCategoryId.value !== undefined ||
        requestedSort.value !== 'LATEST',
)

const canWrite = computed(() => {
  const member = session.value
  if (!board.value || !member) return false

  return (
      board.value.policy.writePermission === 'MEMBER' ||
      member.role === 'ADMIN'
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

async function loadPosts(
    slug: string,
    page: number,
    keyword: string,
    categoryId: number | undefined,
    sort: PostSort
) {
  const currentRequest = ++postsRequestSequence

  postsLoading.value = true
  postsErrorMessage.value = ''
  postPage.value = null

  try {
    const response = await getPosts(slug, {
      page,
      size: 10,
      keyword: keyword || undefined,
      categoryId,
      sort,
    })

    if (currentRequest !== postsRequestSequence) return

    const normalizedPage =
        response.totalPages === 0
            ? 1
            : Math.min(page, response.totalPages)

    if (page !== normalizedPage) {
      await router.replace({
        name: 'board-detail',
        params: {boardSlug: slug},
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

function updateListQuery(
    changes: Record<string, string | undefined>,
) {
  void router.push({
    name: 'board-detail',
    params: {
      boardSlug: boardSlug.value,
    },
    query: {
      ...route.query,
      ...changes,
      page: '1',
    },
  })
}

function applySearch() {
  const keyword = searchKeyword.value.trim()

  updateListQuery({
    keyword: keyword || undefined,
  })
}

function resetFilters() {
  searchKeyword.value = ''

  void router.push({
    name: 'board-detail',
    params: {
      boardSlug: boardSlug.value,
    },
    query: {
      page: '1',
    },
  })
}

function changePage(page: number) {
  router.push({
    name: 'board-detail',
    params: {boardSlug: boardSlug.value},
    query: {
      ...route.query,
      page: String(page),
    },
  })
}

// 게시판 정보 조회
watch(boardSlug, loadBoard, {immediate: true})

// 검색창과 URL 검색어 동기화
watch(
    requestedKeyword,
    (keyword) => {
      searchKeyword.value = keyword
    },
)

// 게시글 목록 조회
watch(
    [
      boardSlug,
      requestedPage,
      requestedKeyword,
      requestedCategoryId,
      requestedSort,
    ],
    ([slug, page, keyword, categoryId, sort]) => {
      loadPosts(
          slug,
          page,
          keyword,
          categoryId,
          sort,
      )
    },
    {immediate: true},
)
</script>

<template>
  <div class="grid min-w-0 gap-5">
    <p v-if="boardLoading" class="rounded-md bg-slate-50 px-5 py-14 text-center text-sm text-slate-500">게시판을 불러오는
      중입니다.</p>
    <p v-else-if="boardErrorMessage"
       class="rounded-md border border-red-200 bg-red-50 px-5 py-12 text-center text-sm text-red-700" role="alert">
      {{ boardErrorMessage }}</p>
    <template v-else-if="board">
      <BoardHero :name="board.name" :slug="board.slug" :description="board.description"/>
      <section class="grid min-w-0 gap-4" aria-label="게시글 목록">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <p class="text-sm text-slate-500">
            {{ hasActiveFilters ? '검색 결과' : '전체 글' }}
            <span v-if="postPage" class="tabular-nums">{{ postPage.totalElements.toLocaleString() }}개</span>
          </p>
          <RouterLink v-if="canWrite" :to="{ name: 'post-create', params: { boardSlug: board.slug } }"
                      class="inline-flex h-10 items-center justify-center gap-2 rounded-md border border-blue-600 bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600">
            <CommunityIcon name="write" class="size-4"/>
            글쓰기
          </RouterLink>
        </div>

        <div class="flex flex-wrap items-center justify-between gap-3">
          <nav v-if="board.policy.categoriesEnabled" class="flex max-w-full gap-1.5 overflow-x-auto pb-1"
               aria-label="카테고리 필터">
            <button type="button"
                    class="h-9 shrink-0 rounded-md border px-5 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-blue-600"
                    :class="!selectedCategoryId ? 'border-blue-600 bg-blue-600 text-white' : 'border-slate-200 bg-slate-50 text-slate-600 hover:bg-slate-100'"
                    :aria-pressed="!selectedCategoryId" @click="selectedCategoryId = ''">전체
            </button>
            <button v-for="category in board.categories" :key="category.id" type="button"
                    class="h-9 shrink-0 rounded-md border px-5 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-blue-600"
                    :class="selectedCategoryId === String(category.id) ? 'border-blue-600 bg-blue-600 text-white' : 'border-slate-200 bg-slate-50 text-slate-600 hover:bg-slate-100'"
                    :aria-pressed="selectedCategoryId === String(category.id)"
                    @click="selectedCategoryId = String(category.id)">{{ category.name }}
            </button>
          </nav>
          <label class="ml-auto">
            <span class="sr-only">게시글 정렬</span>
            <select v-model="selectedSort"
                    class="h-9 rounded-md border border-slate-200 bg-white px-3 text-xs text-slate-600 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100">
              <option value="LATEST">최신순</option>
              <option value="OLDEST">오래된순</option>
              <option value="VIEWS">조회순</option>
            </select>
          </label>
        </div>

        <p v-if="postsLoading" class="rounded-md border border-slate-200 py-16 text-center text-sm text-slate-500"
           role="status">게시글을 불러오는 중입니다.</p>
        <p v-else-if="postsErrorMessage"
           class="rounded-md border border-red-200 bg-red-50 py-12 text-center text-sm text-red-700" role="alert">
          {{ postsErrorMessage }}</p>
        <template v-else-if="postPage">
          <PostCardList v-if="board.displayType === 'CARD'" :board-slug="board.slug" :posts="postPage.posts"/>
          <PostList v-else :board-slug="board.slug" :posts="postPage.posts"/>
          <BasePagination :page="postPage.page" :total-pages="postPage.totalPages" @change="changePage"/>
        </template>

        <form class="mx-auto flex w-full max-w-xl flex-wrap items-center justify-center gap-2 pt-2"
              @submit.prevent="applySearch">
          <span
              class="hidden h-10 items-center rounded-md border border-slate-200 bg-white px-4 text-sm text-slate-600 sm:inline-flex">제목 + 내용</span>
          <label class="min-w-0 flex-1">
            <span class="sr-only">게시글 검색어</span>
            <input v-model="searchKeyword" type="search" maxlength="100" placeholder="검색어를 입력하세요."
                   class="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm outline-none placeholder:text-slate-400 focus:border-blue-500 focus:ring-2 focus:ring-blue-100"/>
          </label>
          <button type="submit"
                  class="h-10 rounded-md border border-blue-600 bg-blue-600 px-5 text-sm font-medium text-white hover:bg-blue-700 focus-visible:outline-2 focus-visible:outline-blue-600">
            검색
          </button>
          <button v-if="hasActiveFilters" type="button"
                  class="h-10 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-600 hover:bg-slate-50"
                  @click="resetFilters">초기화
          </button>
        </form>
      </section>
      <BoardPolicyPanel :board="board"/>
    </template>
  </div>
</template>
