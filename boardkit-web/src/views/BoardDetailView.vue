<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getBoardDetail } from '@/api/boardApi'
import { ApiError } from '@/api/http'
import { getPosts } from '@/api/postApi'
import BasePagination from '@/components/ui/BasePagination.vue'
import { getAuthSession } from '@/features/auth/services/authSession'
import BoardCategoryList from '@/features/board/components/BoardCategoryList.vue'
import BoardHeader from '@/features/board/components/BoardHeader.vue'
import type {
  BoardDetailResponse,
} from '@/features/board/types/board'
import PostList from '@/features/post/components/PostList.vue'
import type {
  PostPageResponse,
} from '@/features/post/types/post'

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
    if (currentRequest !== boardRequestSequence) {
      return
    }

    if (error instanceof ApiError && error.status === 404) {
      boardErrorMessage.value =
          '게시판을 찾을 수 없습니다.'
    } else if (error instanceof ApiError && error.status === 403) {
      boardErrorMessage.value =
          '게시판을 조회할 권한이 없습니다.'
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
) {
  const currentRequest = ++postsRequestSequence

  postsLoading.value = true
  postsErrorMessage.value = ''
  postPage.value = null

  try {
    const response = await getPosts(slug, page, 10)

    if (currentRequest !== postsRequestSequence) {
      return
    }

    const normalizedPage =
        response.totalPages === 0
            ? 1
            : Math.min(page, response.totalPages)

    if (page !== normalizedPage) {
      await router.replace({
        name: 'board-detail',
        params: {
          boardSlug: slug,
        },
        query: {
          ...route.query,
          page: String(normalizedPage),
        },
      })

      return
    }

    postPage.value = response
  } catch (error) {
    if (currentRequest !== postsRequestSequence) {
      return
    }

    if (error instanceof ApiError && error.status === 404) {
      postsErrorMessage.value =
          '게시판을 찾을 수 없습니다.'
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
    params: {
      boardSlug: boardSlug.value,
    },
    query: {
      ...route.query,
      page: String(page),
    },
  })
}

watch(boardSlug, loadBoard, {
  immediate: true,
})

watch(
    [boardSlug, requestedPage],
    ([slug, page]) => loadPosts(slug, page),
    {
      immediate: true,
    },
)
</script>

<template>
  <div class="board-page">
    <p v-if="boardLoading" class="state-message">
      게시판을 불러오는 중입니다.
    </p>

    <p
        v-else-if="boardErrorMessage"
        class="state-message state-message--error"
        role="alert"
    >
      {{ boardErrorMessage }}
    </p>

    <template v-else-if="board">
      <BoardHeader
          :name="board.name"
          :description="board.description"
      />

      <BoardCategoryList
          v-if="board.policy.categoriesEnabled"
          :categories="board.categories"
      />

      <section class="post-area">
        <div class="post-area__header">
          <div>
            <h2>게시글</h2>

            <span
                v-if="postPage"
                class="post-count"
            >
              총 {{ postPage.totalElements }}개
            </span>
          </div>

          <RouterLink
              v-if="canWrite"
              :to="{
              name: 'post-create',
              params: {
                boardSlug: board.slug,
              },
            }"
              class="write-link"
          >
            글쓰기
          </RouterLink>
        </div>

        <p
            v-if="postsLoading"
            class="state-message"
        >
          게시글을 불러오는 중입니다.
        </p>

        <p
            v-else-if="postsErrorMessage"
            class="state-message state-message--error"
            role="alert"
        >
          {{ postsErrorMessage }}
        </p>

        <template v-else-if="postPage">
          <PostList :posts="postPage.posts" />

          <BasePagination
              :page="postPage.page"
              :total-pages="postPage.totalPages"
              @change="changePage"
          />
        </template>
      </section>
    </template>
  </div>
</template>

<style scoped>
.board-page {
  display: grid;
  gap: 1.5rem;
}

.state-message {
  margin: 0;
  padding: 1rem;
  border-radius: 0.75rem;
  color: #475569;
  background: #f1f5f9;
}

.state-message--error {
  color: #991b1b;
  background: #fee2e2;
}

.post-area {
  display: grid;
  gap: 1.5rem;
  min-height: 16rem;
  padding: 1.5rem;
  border: 1px solid #e2e8f0;
  border-radius: 1rem;
  background: #fff;
}

.post-area__header {
  display: flex;
  gap: 1rem;
  align-items: center;
  justify-content: space-between;
}

.post-area__header > div {
  display: flex;
  gap: 0.65rem;
  align-items: baseline;
}

.post-area h2 {
  margin: 0;
  color: #0f172a;
  font-size: 1.1rem;
}

.post-count {
  color: #64748b;
  font-size: 0.8rem;
}

.write-link {
  padding: 0.65rem 1rem;
  border-radius: 0.65rem;
  color: #fff;
  background: #2563eb;
  font-size: 0.875rem;
  font-weight: 700;
  text-decoration: none;
}

.write-link:hover {
  background: #1d4ed8;
}
</style>