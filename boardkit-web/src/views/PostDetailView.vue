<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { getBoardDetail } from '@/api/boardApi'
import { ApiError } from '@/api/http'
import { getPostDetail } from '@/api/postApi'
import BoardHeader from '@/features/board/components/BoardHeader.vue'
import type {
  BoardDetailResponse,
} from '@/features/board/types/board'
import SecretPostPasswordForm from '@/features/post/components/SecretPostPasswordForm.vue'
import type {
  PostDetailResponse,
} from '@/features/post/types/post'

const route = useRoute()

const board = ref<BoardDetailResponse | null>(null)
const post = ref<PostDetailResponse | null>(null)

const loading = ref(false)
const passwordSubmitting = ref(false)
const passwordRequired = ref(false)

const errorMessage = ref('')
const passwordErrorMessage = ref('')

const boardSlug = computed(
    () => String(route.params.boardSlug ?? ''),
)

const postId = computed(() => {
  const parsed = Number(route.params.postId)

  return Number.isSafeInteger(parsed) && parsed > 0
      ? parsed
      : null
})

let requestSequence = 0

async function loadPage(
    slug: string,
    id: number | null,
) {
  const currentRequest = ++requestSequence

  board.value = null
  post.value = null
  passwordRequired.value = false
  errorMessage.value = ''
  passwordErrorMessage.value = ''

  if (id === null) {
    errorMessage.value =
        '올바르지 않은 게시글 번호입니다.'
    return
  }

  loading.value = true
  let boardLoaded = false

  try {
    const boardResponse =
        await getBoardDetail(slug)

    if (currentRequest !== requestSequence) {
      return
    }

    board.value = boardResponse
    boardLoaded = true

    const postResponse =
        await getPostDetail(slug, id)

    if (currentRequest === requestSequence) {
      post.value = postResponse
    }
  } catch (error) {
    if (currentRequest !== requestSequence) {
      return
    }

    if (
        error instanceof ApiError &&
        error.status === 403 &&
        boardLoaded
    ) {
      passwordRequired.value = true
    } else if (
        error instanceof ApiError &&
        error.status === 403
    ) {
      errorMessage.value =
          '게시판을 조회할 권한이 없습니다.'
    } else if (
        error instanceof ApiError &&
        error.status === 404
    ) {
      errorMessage.value =
          boardLoaded
              ? '게시글을 찾을 수 없습니다.'
              : '게시판을 찾을 수 없습니다.'
    } else {
      errorMessage.value =
          error instanceof Error
              ? error.message
              : '게시글을 불러오지 못했습니다.'
    }
  } finally {
    if (currentRequest === requestSequence) {
      loading.value = false
    }
  }
}

async function submitPassword(password: string) {
  if (postId.value === null) {
    return
  }

  const currentRequest = ++requestSequence

  passwordSubmitting.value = true
  passwordErrorMessage.value = ''

  try {
    const response = await getPostDetail(
        boardSlug.value,
        postId.value,
        password,
    )

    if (currentRequest === requestSequence) {
      post.value = response
      passwordRequired.value = false
    }
  } catch (error) {
    if (currentRequest !== requestSequence) {
      return
    }

    if (
        error instanceof ApiError &&
        error.status === 403
    ) {
      passwordErrorMessage.value =
          '비밀번호가 올바르지 않습니다.'
    } else {
      passwordErrorMessage.value =
          error instanceof Error
              ? error.message
              : '게시글을 열지 못했습니다.'
    }
  } finally {
    if (currentRequest === requestSequence) {
      passwordSubmitting.value = false
    }
  }
}

function formatDate(value: string): string {
  return value
      .replace('T', ' ')
      .slice(0, 16)
}

watch(
    [boardSlug, postId],
    ([slug, id]) => loadPage(slug, id),
    {
      immediate: true,
    },
)
</script>

<template>
  <div class="post-detail-page">
    <p
        v-if="loading && !board"
        class="state-message"
    >
      게시글을 불러오는 중입니다.
    </p>

    <p
        v-else-if="errorMessage && !board"
        class="state-message state-message--error"
        role="alert"
    >
      {{ errorMessage }}
    </p>

    <template v-else-if="board">
      <BoardHeader
          :name="board.name"
          :description="board.description"
      />

      <p
          v-if="loading"
          class="state-message"
      >
        게시글을 불러오는 중입니다.
      </p>

      <SecretPostPasswordForm
          v-else-if="passwordRequired"
          :submitting="passwordSubmitting"
          :error-message="passwordErrorMessage"
          @submit="submitPassword"
      />

      <p
          v-else-if="errorMessage"
          class="state-message state-message--error"
          role="alert"
      >
        {{ errorMessage }}
      </p>

      <article
          v-else-if="post"
          class="post-detail"
      >
        <header class="post-detail__header">
          <div class="post-detail__badges">
            <span
                v-if="post.pinned"
                class="badge"
            >
              고정
            </span>

            <span
                v-if="post.secret"
                class="badge badge--secret"
            >
              비밀글
            </span>

            <span v-if="post.categoryName">
              {{ post.categoryName }}
            </span>
          </div>

          <h1>{{ post.title }}</h1>

          <dl class="post-detail__meta">
            <div>
              <dt>작성자</dt>
              <dd>{{ post.authorDisplayName }}</dd>
            </div>

            <div>
              <dt>작성일</dt>
              <dd>{{ formatDate(post.createdAt) }}</dd>
            </div>

            <div>
              <dt>조회</dt>
              <dd>{{ post.viewCount }}</dd>
            </div>
          </dl>
        </header>

        <div class="post-detail__content">
          {{ post.content }}
        </div>

        <p
            v-if="post.commentsLocked"
            class="locked-message"
        >
          이 게시글은 댓글 작성이 잠겨 있습니다.
        </p>

        <RouterLink
            :to="{
            name: 'board-detail',
            params: {
              boardSlug,
            },
            query: {
              page: route.query.page,
            },
          }"
            class="back-link"
        >
          목록으로
        </RouterLink>
      </article>
    </template>
  </div>
</template>

<style scoped>
.post-detail-page {
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

.post-detail {
  display: grid;
  gap: 1.5rem;
  padding: 1.5rem;
  border: 1px solid #e2e8f0;
  border-radius: 1rem;
  background: #fff;
}

.post-detail__header {
  display: grid;
  gap: 0.8rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid #e2e8f0;
}

.post-detail__header h1 {
  margin: 0;
  color: #0f172a;
  font-size: 1.6rem;
}

.post-detail__badges {
  display: flex;
  gap: 0.4rem;
  align-items: center;
  color: #64748b;
  font-size: 0.8rem;
}

.badge {
  padding: 0.2rem 0.45rem;
  border-radius: 0.35rem;
  color: #1d4ed8;
  background: #dbeafe;
  font-weight: 700;
}

.badge--secret {
  color: #7c3aed;
  background: #ede9fe;
}

.post-detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  margin: 0;
}

.post-detail__meta div {
  display: flex;
  gap: 0.35rem;
}

.post-detail__meta dt {
  color: #94a3b8;
}

.post-detail__meta dd {
  margin: 0;
  color: #475569;
}

.post-detail__content {
  min-height: 12rem;
  color: #334155;
  line-height: 1.75;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.locked-message {
  margin: 0;
  padding: 0.8rem 1rem;
  border-radius: 0.65rem;
  color: #92400e;
  background: #fef3c7;
}

.back-link {
  justify-self: start;
  color: #2563eb;
  font-weight: 700;
  text-decoration: none;
}
</style>