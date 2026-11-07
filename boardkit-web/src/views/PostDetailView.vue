<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {getBoardDetail} from '@/api/boardApi'
import {ApiError} from '@/api/http'
import type {BoardDetailResponse,} from '@/features/board/types/board'
import PostCommentsPanel from '@/features/comment/components/PostCommentsPanel.vue'
import SecretPostPasswordForm from '@/features/post/components/SecretPostPasswordForm.vue'
import PostReplyPanel from '@/features/post/components/PostReplyPanel.vue'
import ReportAction from '@/features/report/components/ReportAction.vue'
import type {PostDetailResponse,} from '@/features/post/types/post'
import {
  deletePost,
  getPostDetail,
  likePost,
  unlikePost,
} from '@/api/postApi'
import {getAuthSession} from '@/features/auth/services/authSession'
import BaseButton from '@/components/ui/BaseButton.vue'
import PostContent from '@/features/post/components/PostContent.vue'
import LikeButton from '@/components/ui/LikeButton.vue'

const route = useRoute()
const router = useRouter()

const deleting = ref(false)
const deleteErrorMessage = ref('')
const likeSubmitting = ref(false)
const likeErrorMessage = ref('')
const authSession = computed(() => getAuthSession())
const board = ref<BoardDetailResponse | null>(null)
const post = ref<PostDetailResponse | null>(null)
const loading = ref(false)
const passwordSubmitting = ref(false)
const passwordRequired = ref(false)
const secretPostPassword = ref<string | undefined>(undefined)

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

async function loadPage(slug: string, id: number | null,) {
  loading.value = false
  passwordSubmitting.value = false

  const currentRequest = ++requestSequence
  likeSubmitting.value = false
  likeErrorMessage.value = ''

  board.value = null
  post.value = null
  secretPostPassword.value = undefined
  passwordRequired.value = false
  errorMessage.value = ''
  passwordErrorMessage.value = ''

  if (id === null) {
    errorMessage.value = '올바르지 않은 게시글 번호입니다.'
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
    if (currentRequest !== requestSequence) return

    if (
        error instanceof ApiError &&
        error.status === 403 &&
        error.code === 'CONTENT_BLINDED'
    ) {
      errorMessage.value =
          '블라인드된 게시글은 작성자와 관리자만 열람할 수 있습니다.'
    } else if (
        error instanceof ApiError &&
        error.status === 403 &&
        error.code === 'SECRET_POST_ACCESS_DENIED' &&
        boardLoaded
    ) {
      passwordRequired.value = true
    } else if (error instanceof ApiError && error.status === 403) {
      errorMessage.value = boardLoaded
          ? '게시글을 조회할 권한이 없습니다.'
          : '게시판을 조회할 권한이 없습니다.'
    } else if (error instanceof ApiError && error.status === 404) {
      errorMessage.value = boardLoaded
          ? '게시글을 찾을 수 없습니다.'
          : '게시판을 찾을 수 없습니다.'
    } else {
      errorMessage.value = error instanceof Error
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
  const id = postId.value
  if (id === null || passwordSubmitting.value || !passwordRequired.value) return

  const currentRequest = ++requestSequence
  passwordSubmitting.value = true
  passwordErrorMessage.value = ''

  try {
    const response = await getPostDetail(boardSlug.value, id, password)
    if (currentRequest !== requestSequence) return

    secretPostPassword.value = password
    post.value = response
    passwordRequired.value = false
  } catch (error) {
    if (currentRequest !== requestSequence) return

    if (
        error instanceof ApiError &&
        error.status === 403 &&
        error.code === 'SECRET_POST_ACCESS_DENIED'
    ) {
      passwordErrorMessage.value = '비밀번호가 올바르지 않습니다.'
    } else if (
        error instanceof ApiError &&
        (error.status === 403 || error.status === 404)
    ) {
      passwordRequired.value = false
      post.value = null
      errorMessage.value = error.code === 'CONTENT_BLINDED'
          ? '블라인드된 게시글은 작성자와 관리자만 열람할 수 있습니다.'
          : error.status === 404
              ? '게시글을 찾을 수 없습니다.'
              : '게시글을 조회할 권한이 없습니다.'
    } else {
      passwordErrorMessage.value = error instanceof Error
          ? error.message
          : '게시글을 열지 못했습니다.'
    }
  } finally {
    if (currentRequest === requestSequence) {
      passwordSubmitting.value = false
    }
  }
}

const canDelete = computed(() => {
  const session = authSession.value
  const currentPost = post.value

  if (
      !session ||
      !currentPost ||
      board.value?.status !== 'ACTIVE'
  ) {
    return false
  }

  return session.memberId === currentPost.authorId ||
      (!currentPost.blinded && session.role === 'ADMIN')
})

const canEdit = computed(() => {
  const session = authSession.value
  const currentPost = post.value
  const currentBoard = board.value

  return session !== null &&
      currentPost !== null &&
      currentBoard !== null &&
      currentBoard.status === 'ACTIVE' &&
      !currentPost.blinded &&
      session.memberId === currentPost.authorId &&
      (currentBoard.policy.writePermission === 'MEMBER' ||
          session.role === 'ADMIN')
})

const canToggleLike = computed(() =>
    authSession.value !== null &&
    post.value !== null &&
    !post.value.blinded &&
    board.value?.status === 'ACTIVE' &&
    board.value.policy.postLikesEnabled &&
    !deleting.value,
)

async function toggleLike() {
  const currentPost = post.value
  const currentBoard = board.value
  const currentSession = authSession.value

  if (
      !canToggleLike.value
      || currentPost === null
      || currentBoard === null
      || currentSession === null
      || likeSubmitting.value
  ) {
    return
  }

  const currentRequest = requestSequence
  const memberId = currentSession.memberId
  const nextLiked = !currentPost.likedByMe

  likeSubmitting.value = true
  likeErrorMessage.value = ''

  try {
    if (nextLiked) {
      await likePost(
          currentBoard.slug,
          currentPost.id,
          secretPostPassword.value,
      )
    } else {
      await unlikePost(
          currentBoard.slug,
          currentPost.id,
          secretPostPassword.value,
      )
    }

    if (
        currentRequest !== requestSequence
        || post.value !== currentPost
        || authSession.value?.memberId !== memberId
    ) {
      return
    }

    post.value = {
      ...currentPost,
      likedByMe: nextLiked,
      likeCount: Math.max(
          0,
          currentPost.likeCount + (nextLiked ? 1 : -1),
      ),
    }
  } catch (error) {
    if (currentRequest !== requestSequence) {
      return
    }

    if (error instanceof ApiError && error.code === 'CONTENT_BLINDED') {
      await loadPage(currentBoard.slug, currentPost.id)
      return
    }

    if (error instanceof ApiError && error.status === 401) {
      likeErrorMessage.value = '다시 로그인해 주세요.'
    } else if (error instanceof ApiError && error.status === 403) {
      likeErrorMessage.value =
          '좋아요를 변경할 수 없습니다. 회원 상태나 게시판 설정이 변경되었을 수 있습니다.'
    } else if (error instanceof ApiError && error.status === 404) {
      likeErrorMessage.value = '게시글을 찾을 수 없습니다.'
    } else {
      likeErrorMessage.value =
          '좋아요 변경 결과를 확인하지 못했습니다. 새로고침 후 상태를 확인해 주세요.'
    }
  } finally {
    if (currentRequest === requestSequence) {
      likeSubmitting.value = false
    }
  }
}

async function removePost() {
  const currentPost = post.value
  const id = postId.value

  if (!canDelete.value || currentPost === null || id === null || deleting.value) {
    return
  }

  if (!window.confirm('게시글을 삭제하시겠습니까?')) {
    return
  }

  deleting.value = true
  deleteErrorMessage.value = ''

  try {
    await deletePost(boardSlug.value, id, currentPost.version)

    await router.replace({
      name: 'board-detail',
      params: {boardSlug: boardSlug.value},
      query: route.query,
    })
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      deleteErrorMessage.value = '게시글이 변경되었습니다. 새로고침 후 다시 시도해주세요.'
    } else if (error instanceof ApiError && error.status === 404) {
      deleteErrorMessage.value = '이미 삭제된 게시글입니다.'
    } else {
      deleteErrorMessage.value =
          error instanceof Error ? error.message : '게시글 삭제에 실패했습니다.'
    }
  } finally {
    deleting.value = false
  }
}

function formatDate(value: string): string {
  return value
      .replace('T', ' ')
      .slice(0, 16)
}

watch(
    [
      boardSlug,
      postId,
      () => authSession.value?.memberId ?? null,
      () => authSession.value?.role ?? null,
    ],
    ([slug, id]) => {
      void loadPage(slug, id)
    },
    { immediate: true, flush: 'sync' },
)

onBeforeUnmount(() => {
  requestSequence++
})
</script>

<template>
  <div class="grid min-w-0 gap-5">
    <p
        v-if="loading && !board"
        class="m-0 rounded-none border border-zinc-200 bg-zinc-100 p-5 font-medium text-zinc-700"
    >
      게시글을 불러오는 중입니다.
    </p>

    <p
        v-else-if="errorMessage && !board"
        class="m-0 rounded-none border border-red-200 bg-rose-50 p-5 font-medium text-red-900"
        role="alert"
    >
      {{ errorMessage }}
    </p>

    <template v-else-if="board">
      <p
          v-if="board.status === 'ARCHIVED'"
          role="status"
          class="m-0 rounded-none border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800"
      >
        보관된 게시판입니다. 조회만 가능합니다.
      </p>

      <p
          v-if="loading"
          class="m-0 rounded-none border border-zinc-200 bg-zinc-100 p-5 font-medium text-zinc-700"
      >
        게시글을 불러오는 중입니다.
      </p>

      <div
          v-else-if="passwordRequired"
          class="grid min-h-[calc(100svh-18rem)] place-items-center py-6"
      >
        <SecretPostPasswordForm
            :submitting="passwordSubmitting"
            :error-message="passwordErrorMessage"
            @submit="submitPassword"
        />
      </div>

      <p
          v-else-if="errorMessage"
          class="m-0 rounded-none border border-red-200 bg-rose-50 p-5 font-medium text-red-900"
          role="alert"
      >
        {{ errorMessage }}
      </p>

      <div
          v-else-if="post"
          class="grid min-w-0 items-start gap-3"
      >
        <div class="grid min-w-0 content-start gap-3">
          <article class="grid w-full min-w-0 gap-0 bg-card">
            <header class="grid gap-5 border-b border-border pb-8 pt-4">
              <div class="flex flex-wrap items-center gap-2 text-xs font-medium text-zinc-600">
                <span class="rounded-none bg-accent px-2 py-1 font-medium text-primary">{{ board.name }}</span>
                <span v-if="post.pinned" class="rounded-none bg-zinc-100 px-2 py-1 font-medium text-zinc-600">고정</span>
                <span v-if="post.secret" class="rounded-none bg-zinc-100 px-2 py-1 font-medium text-zinc-600">비밀글</span>
                <span v-if="post.categoryName" class="rounded-none bg-zinc-100 px-2 py-1 font-medium text-zinc-600">
                  {{ post.categoryName }}
                </span>
              </div>

              <h1 class="page-title">{{
                  post.title
                }}</h1>

              <dl class="m-0 flex flex-wrap gap-x-6 gap-y-2 text-sm">
                <div class="flex gap-2">
                  <dt class="font-medium text-zinc-500">작성자</dt>
                  <dd class="m-0 font-medium text-zinc-700">{{ post.authorDisplayName }}</dd>
                </div>

                <div class="flex gap-2">
                  <dt class="font-medium text-zinc-500">작성일</dt>
                  <dd class="m-0 font-medium text-zinc-700">{{ formatDate(post.createdAt) }}</dd>
                </div>

                <div class="flex gap-2">
                  <dt class="font-medium text-zinc-500">조회</dt>
                  <dd class="m-0 font-medium text-zinc-700">{{ post.viewCount }}</dd>
                </div>
              </dl>
            </header>

            <p
                v-if="post.blinded"
                role="status"
                class="my-4 rounded-none border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-800"
            >
              블라인드된 게시글입니다.
              작성자와 관리자만 원문을 볼 수 있으며 수정과 추가 활동이 제한됩니다.
            </p>

            <PostContent
                :key="`${board.slug}:${post.id}:${post.version}`"
                :board-slug="board.slug"
                :post-id="post.id"
                :content="post.content"
                :content-document="post.contentDocument"
                :files="post.files"
                :secret-password="secretPostPassword"
            />

            <div
                class="grid justify-items-center gap-2 py-5"
                :aria-busy="likeSubmitting"
            >
              <LikeButton
                  :count="post.likeCount"
                  :liked="post.likedByMe"
                  :disabled="!canToggleLike"
                  :loading="likeSubmitting"
                  @toggle="toggleLike"
              />
              <p
                  v-if="board.policy.postLikesEnabled && !authSession"
                  class="m-0 text-xs text-zinc-500"
              >
                로그인 후 좋아요를 누를 수 있습니다.
              </p>

              <p
                  v-if="likeErrorMessage"
                  class="m-0 text-center text-sm text-red-600"
                  role="alert"
              >
                {{ likeErrorMessage }}
              </p>
            </div>

            <p
                v-if="deleteErrorMessage"
                class="m-0 rounded-none border border-red-200 bg-rose-50 p-4 font-medium text-red-900"
                role="alert"
            >
              {{ deleteErrorMessage }}
            </p>

            <footer
                class="flex flex-wrap items-center justify-end gap-3 py-3 max-sm:justify-stretch">
              <ReportAction
                  v-if="board.status === 'ACTIVE' && !post.blinded"
                  :board-slug="board.slug"
                  :post-id="post.id"
                  :author-id="post.authorId"
                  :secret-password="secretPostPassword"
                  :disabled="deleting"
                  size="md"
              />
              <div v-if="canEdit || canDelete" class="flex flex-wrap gap-3 max-sm:flex-1 max-sm:[&>*]:flex-1">
                <RouterLink
                    v-if="canEdit"
                    class="inline-flex h-9 items-center justify-center rounded-none border border-zinc-200 bg-white px-4 text-sm font-medium text-zinc-700 hover:bg-zinc-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
                    :to="{
                      name: 'post-edit',
                      params: { boardSlug, postId: post.id },
                      query: route.query,
                    }"
                >
                  수정
                </RouterLink>
                <BaseButton
                    v-if="canDelete"
                    variant="danger"
                    :disabled="deleting || likeSubmitting"
                    @click="removePost"
                >
                  {{ deleting ? '삭제 중...' : '삭제' }}
                </BaseButton>
              </div>

              <RouterLink
                  :to="{
                    name: 'board-detail',
                    params: { boardSlug },
                    query: route.query,
                  }"
                  class="inline-flex h-9 items-center justify-center rounded-none border border-primary bg-primary px-4 text-sm font-medium text-white hover:bg-primary/90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring max-sm:flex-1"
              >
                ← 목록으로
              </RouterLink>
            </footer>
          </article>

          <PostReplyPanel
              :key="`${board.slug}:${post.id}`"
              :post-blinded="post.blinded"
              :board-slug="board.slug"
              :post-id="post.id"
              :enabled="board.policy.officialReplyEnabled"
              :secret-password="secretPostPassword"
              :board-archived="board.status === 'ARCHIVED'"
          />

          <PostCommentsPanel
              :post-blinded="post.blinded"
              :board-slug="board.slug"
              :post-id="post.id"
              :comments-enabled="board.policy.commentsEnabled"
              :comment-permission="board.policy.commentPermission"
              :comment-replies-enabled="board.policy.commentRepliesEnabled"
              :locked="post.commentsLocked"
              :secret-password="secretPostPassword"
              :comment-likes-enabled="board.policy.commentLikesEnabled"
              :board-archived="board.status === 'ARCHIVED'"
          />
        </div>

      </div>
    </template>
  </div>
</template>
