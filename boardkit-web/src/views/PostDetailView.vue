<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getBoardDetail } from '@/api/boardApi'
import { ApiError } from '@/api/http'
import BoardHero from '@/features/board/components/BoardHero.vue'
import type {BoardDetailResponse,} from '@/features/board/types/board'
import PostCommentsPanel from '@/features/post/components/PostCommentsPanel.vue'
import PostDetailSidebar from '@/features/post/components/PostDetailSidebar.vue'
import SecretPostPasswordForm from '@/features/post/components/SecretPostPasswordForm.vue'
import type {PostDetailResponse,} from '@/features/post/types/post'
import { deletePost, getPostDetail } from '@/api/postApi'
import { getAuthSession } from '@/features/auth/services/authSession'
import BaseButton from '@/components/ui/BaseButton.vue'

const route = useRoute()
const router = useRouter()

const deleting = ref(false)
const deleteErrorMessage = ref('')

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

async function loadPage(slug: string, id: number | null,) {
  const currentRequest = ++requestSequence

  board.value = null
  post.value = null
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
    if (currentRequest !== requestSequence) {
      return
    }

    if (error instanceof ApiError && error.status === 403 && boardLoaded) {
      passwordRequired.value = true
    } else if (error instanceof ApiError && error.status === 403) {
      errorMessage.value = '게시판을 조회할 권한이 없습니다.'
    } else if (error instanceof ApiError && error.status === 404) {
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

    if (error instanceof ApiError && error.status === 403) {
      passwordErrorMessage.value = '비밀번호가 올바르지 않습니다.'
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

const canDelete = computed(() => {
  const session = getAuthSession()
  const currentPost = post.value

  return session !== null &&
      currentPost !== null &&
      (session.memberId === currentPost.authorId ||
          session.role === 'ADMIN')
})

const canEdit = computed(() => {
  const session = getAuthSession()
  const currentPost = post.value
  const currentBoard = board.value

  return session !== null &&
      currentPost !== null &&
      currentBoard !== null &&
      session.memberId === currentPost.authorId &&
      (currentBoard.policy.writePermission === 'MEMBER' ||
          session.role === 'ADMIN')
})

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
      params: { boardSlug: boardSlug.value },
      query: route.query.page ? { page: route.query.page } : {},
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
    [boardSlug, postId],
    ([slug, id]) => loadPage(slug, id),
    {
      immediate: true,
    },
)
</script>

<template>
  <div class="grid min-w-0 gap-5">
    <p
        v-if="loading && !board"
        class="m-0 rounded-lg border-[3px] border-slate-950 bg-slate-100 p-5 font-bold text-slate-700"
    >
      게시글을 불러오는 중입니다.
    </p>

    <p
        v-else-if="errorMessage && !board"
        class="m-0 rounded-lg border-[3px] border-red-900 bg-rose-50 p-5 font-bold text-red-900"
        role="alert"
    >
      {{ errorMessage }}
    </p>

    <template v-else-if="board">
      <BoardHero
          name="POST DETAIL"
          :slug="board.slug"
          :description="`${board.name} · 게시글을 읽고 대화에 참여해 보세요.`"
      />

      <p
          v-if="loading"
          class="m-0 rounded-lg border-[3px] border-slate-950 bg-slate-100 p-5 font-bold text-slate-700"
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
          class="m-0 rounded-lg border-[3px] border-red-900 bg-rose-50 p-5 font-bold text-red-900"
          role="alert"
      >
        {{ errorMessage }}
      </p>

      <div
          v-else-if="post"
          class="grid min-w-0 items-start gap-3 lg:grid-cols-[minmax(0,1fr)_minmax(16rem,20rem)]"
      >
        <div class="grid min-w-0 content-start gap-3">
          <article class="grid min-w-0 gap-0 rounded-lg border-[3px] border-slate-950 bg-white px-4 shadow-[2px_3px_0_#0f172a] sm:px-6">
            <div class="flex min-h-14 items-center gap-3 border-b border-slate-300 py-2 text-lg font-black text-slate-950">
              <span class="grid size-9 shrink-0 place-items-center rounded border-2 border-slate-950 bg-sky-100 text-xl leading-none" aria-hidden="true">▤</span>
              <strong>{{ board.name }} 게시글</strong>
            </div>

            <header class="grid gap-3 border-b border-slate-300 py-4">
              <div class="flex flex-wrap items-center gap-2 text-sm font-extrabold text-slate-700">
                <span class="rounded border-2 border-blue-700 bg-blue-600 px-2 py-0.5 font-black text-white">{{ board.name }}</span>
                <span v-if="post.pinned" class="rounded border-2 border-slate-950 bg-yellow-300 px-2 py-0.5 font-black text-slate-950">고정</span>
                <span v-if="post.secret" class="rounded border-2 border-slate-950 bg-pink-300 px-2 py-0.5 font-black text-slate-950">비밀글</span>
                <span v-if="post.categoryName" class="rounded border-2 border-slate-950 bg-emerald-100 px-2 py-0.5 font-black text-slate-950">
                  {{ post.categoryName }}
                </span>
              </div>

              <h1 class="m-0 text-2xl leading-tight font-black break-words text-slate-950 sm:text-3xl">{{ post.title }}</h1>

              <dl class="m-0 flex flex-wrap gap-x-6 gap-y-2 text-sm">
                <div class="flex gap-2">
                  <dt class="font-bold text-slate-500">작성자</dt>
                  <dd class="m-0 font-extrabold text-slate-950">{{ post.authorDisplayName }}</dd>
                </div>

                <div class="flex gap-2">
                  <dt class="font-bold text-slate-500">작성일</dt>
                  <dd class="m-0 font-extrabold text-slate-950">{{ formatDate(post.createdAt) }}</dd>
                </div>

                <div class="flex gap-2">
                  <dt class="font-bold text-slate-500">조회</dt>
                  <dd class="m-0 font-extrabold text-slate-950">{{ post.viewCount }}</dd>
                </div>
              </dl>
            </header>

            <div class="min-h-36 py-5 leading-8 whitespace-pre-wrap text-slate-800 [overflow-wrap:anywhere] max-sm:min-h-24">
              {{ post.content }}
            </div>

            <p
                v-if="deleteErrorMessage"
                class="m-0 rounded-lg border-2 border-red-900 bg-rose-50 p-4 font-bold text-red-900"
                role="alert"
            >
              {{ deleteErrorMessage }}
            </p>

            <footer class="flex flex-wrap items-center justify-end gap-3 border-t border-slate-300 py-3 max-sm:justify-stretch">
              <div v-if="canEdit || canDelete" class="flex flex-wrap gap-3 max-sm:flex-1 max-sm:[&>*]:flex-1">
                <RouterLink
                    v-if="canEdit"
                    class="inline-flex min-h-11 items-center justify-center rounded border-2 border-slate-950 bg-blue-200 px-4 text-sm font-black text-slate-950 no-underline shadow-[2px_2px_0_#0f172a] transition hover:-translate-y-0.5 focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                    :to="{
                      name: 'post-edit',
                      params: { boardSlug, postId: post.id },
                      query: route.query.page ? { page: route.query.page } : {},
                    }"
                >
                  수정
                </RouterLink>
                <BaseButton
                    variant="danger"
                    class="min-h-11 rounded! border-2! border-slate-950! bg-rose-200! px-4 text-sm! font-black! shadow-[2px_2px_0_#0f172a] transition hover:-translate-y-0.5"
                    :disabled="deleting"
                    @click="removePost"
                >
                  {{ deleting ? '삭제 중...' : '삭제' }}
                </BaseButton>
              </div>

              <RouterLink
                  :to="{
                    name: 'board-detail',
                    params: { boardSlug },
                    query: route.query.page ? { page: route.query.page } : {},
                  }"
                  class="inline-flex min-h-11 items-center justify-center rounded border-2 border-slate-950 bg-blue-600 px-4 text-sm font-black text-white no-underline shadow-[2px_2px_0_#0f172a] transition hover:-translate-y-0.5 hover:bg-blue-700 focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-blue-600 max-sm:flex-1"
              >
                ← 목록으로
              </RouterLink>
            </footer>
          </article>

          <PostCommentsPanel
              v-if="board.policy.commentsEnabled"
              :locked="post.commentsLocked"
          />
        </div>

        <PostDetailSidebar
            :board-name="board.name"
            :board-slug="board.slug"
            :author-display-name="post.authorDisplayName"
        />
      </div>
    </template>
  </div>
</template>
