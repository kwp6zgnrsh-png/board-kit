<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { deleteComment, getComments } from '@/api/commentApi'
import { ApiError } from '@/api/http'
import CommentEditForm from '@/features/comment/components/CommentEditForm.vue'
import CommentCreateForm from '@/features/comment/components/CommentCreateForm.vue'
import { getAuthSession } from '@/features/auth/services/authSession'
import type { WritePermission } from '@/features/board/types/board'
import type { CommentItemResponse } from '@/features/comment/types/comment'

const props = defineProps<{
  boardSlug: string
  postId: number
  commentsEnabled: boolean
  commentPermission: WritePermission
  commentRepliesEnabled: boolean
  locked: boolean
  secretPassword?: string
}>()

const comments = ref<CommentItemResponse[]>([])
const page = ref(1)
const totalPages = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const totalElements = ref(0)
const pageSize = 20
let requestSequence = 0
const selectedReplyParentId = ref<number | null>(null)

const editingCommentId = ref<number | null>(null)
const deletingCommentId = ref<number | null>(null)

const resultMessage = ref('')
const actionErrorMessage = ref('')

const session = getAuthSession()

const canCreateReply = computed(
    () =>
        canCreateComment.value &&
        props.commentRepliesEnabled,
)

function canOpenReplyForm(
    comment: CommentItemResponse,
): boolean {
  return (
      canCreateReply.value &&
      comment.parentId === null &&
      !comment.deleted
  )
}

function canEditComment(
    comment: CommentItemResponse,
): boolean {
  return (
      !comment.deleted &&
      session !== null &&
      session.memberId === comment.authorId &&
      canCreateComment.value
  )
}

function canDeleteComment(
    comment: CommentItemResponse,
): boolean {
  return (
      !comment.deleted &&
      session !== null &&
      (
          session.memberId === comment.authorId ||
          session.role === 'ADMIN'
      )
  )
}

function startCommentEdit(
    comment: CommentItemResponse,
) {
  if (!canEditComment(comment)) {
    return
  }

  selectedReplyParentId.value = null
  editingCommentId.value = comment.id
  resultMessage.value = ''
  actionErrorMessage.value = ''
}

async function handleCommentUpdated() {
  editingCommentId.value = null
  resultMessage.value = '댓글이 수정되었습니다.'
  actionErrorMessage.value = ''

  await loadComments()
}

async function reloadAfterDelete() {
  await loadComments()

  if (totalPages.value === 0) {
    page.value = 1
    return
  }

  if (page.value > totalPages.value) {
    page.value = totalPages.value
    await loadComments()
  }
}

async function removeComment(
    comment: CommentItemResponse,
) {
  if (
      !canDeleteComment(comment) ||
      deletingCommentId.value !== null
  ) {
    return
  }

  if (!window.confirm('댓글을 삭제하시겠습니까?')) {
    return
  }

  deletingCommentId.value = comment.id
  resultMessage.value = ''
  actionErrorMessage.value = ''

  try {
    await deleteComment(
        props.boardSlug,
        props.postId,
        comment.id,
        comment.version,
    )

    selectedReplyParentId.value = null
    editingCommentId.value = null
    resultMessage.value = '댓글이 삭제되었습니다.'

    await reloadAfterDelete()
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      actionErrorMessage.value =
          '댓글이 변경되었습니다. 새로고침 후 다시 시도해주세요.'
    } else if (error instanceof ApiError && error.status === 404) {
      actionErrorMessage.value =
          '이미 삭제된 댓글입니다.'
    } else {
      actionErrorMessage.value =
          error instanceof Error
              ? error.message
              : '댓글 삭제에 실패했습니다.'
    }
  } finally {
    deletingCommentId.value = null
  }
}

function toggleReplyForm(comment: CommentItemResponse) {
  editingCommentId.value = null
  actionErrorMessage.value = ''

  if (!canOpenReplyForm(comment)) {
    return
  }

  resultMessage.value = ''

  selectedReplyParentId.value =
      selectedReplyParentId.value === comment.id
          ? null
          : comment.id
}

async function handleReplyCreated() {
  selectedReplyParentId.value = null
  resultMessage.value = '대댓글이 등록되었습니다.'

  await loadComments()
}

const canCreateComment = computed(() => {
  if (
      session === null ||
      !props.commentsEnabled ||
      props.locked
  ) {
    return false
  }

  return props.commentPermission === 'MEMBER' ||
      session.role === 'ADMIN'
})

async function handleCommentCreated() {
  resultMessage.value = '댓글이 등록되었습니다.'

  page.value = Math.max(
      1,
      Math.ceil((totalElements.value + 1) / pageSize),
  )

  await loadComments()
}

async function loadComments() {
  const currentRequest = ++requestSequence

  loading.value = true
  errorMessage.value = ''

  try {
    const response = await getComments(
        props.boardSlug,
        props.postId,
        {
          page: page.value,
          size: pageSize,
          secretPassword: props.secretPassword,
        },
    )

    if (currentRequest !== requestSequence) {
      return
    }

    comments.value = response.comments
    page.value = response.page
    totalElements.value = response.totalElements
    totalPages.value = response.totalPages

  } catch (error) {
    if (currentRequest !== requestSequence) {
      return
    }

    comments.value = []
    errorMessage.value =
        error instanceof Error
            ? error.message
            : '댓글을 불러오지 못했습니다.'
  } finally {
    if (currentRequest === requestSequence) {
      loading.value = false
    }
  }
}

async function changePage(nextPage: number) {
  if (nextPage < 1 ||
      nextPage > totalPages.value ||
      nextPage === page.value
  ) {
    return
  }

  selectedReplyParentId.value = null
  editingCommentId.value = null
  resultMessage.value = ''
  actionErrorMessage.value = ''
  page.value = nextPage

  await loadComments()
}

function formatDate(value: string): string {
  return value
      .replace('T', ' ')
      .slice(0, 16)
}

watch(
    [
      () => props.boardSlug,
      () => props.postId,
      () => props.secretPassword,
    ],
    () => {
      comments.value = []
      page.value = 1
      totalPages.value = 0
      totalElements.value = 0
      selectedReplyParentId.value = null
      editingCommentId.value = null
      actionErrorMessage.value = ''
      resultMessage.value = ''
      void loadComments()
    },
    {
      immediate: true,
    },
)
</script>

<template>
  <section
      class="overflow-hidden rounded-lg border border-slate-200 bg-white"
      aria-labelledby="comments-heading"
  >
    <header class="flex min-h-12 items-center gap-2 border-b border-slate-200 bg-slate-50 px-4 py-3">

      <h2
          id="comments-heading"
          class="m-0 text-sm font-semibold text-slate-900"
      >
        댓글
      </h2>
    </header>

    <p
        v-if="!commentsEnabled"
        class="m-0 border-b border-slate-200 bg-amber-50 px-4 py-3 text-sm font-medium text-slate-700"
    >
      댓글 작성이 비활성화되어 있습니다. 기존 댓글은 계속 조회할 수 있습니다.
    </p>

    <p
        v-else-if="locked"
        class="m-0 border-b border-slate-200 bg-amber-50 px-4 py-3 text-sm font-medium text-slate-700"
    >
      이 게시글은 댓글 작성이 잠겨 있습니다.
    </p>

    <!--     TODO:     -->
    <p
        v-if="resultMessage"
        class="m-0 border-b border-slate-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-800"
    >
      {{ resultMessage }}
    </p>

    <p
        v-if="actionErrorMessage"
        class="m-4 rounded border border-red-200 bg-rose-50 p-3 text-sm font-medium text-red-900"
        role="alert"
    >
      {{ actionErrorMessage }}
    </p>

    <p
        v-if="loading"
        class="m-0 p-5 text-sm font-medium text-slate-600"
    >
      댓글을 불러오는 중입니다.
    </p>

    <p
        v-else-if="errorMessage"
        class="m-4 rounded border border-red-200 bg-rose-50 p-4 text-sm font-medium text-red-900"
        role="alert"
    >
      {{ errorMessage }}
    </p>

    <p
        v-else-if="comments.length === 0"
        class="m-0 p-5 text-sm font-medium text-slate-600"
    >
      등록된 댓글이 없습니다.
    </p>

    <ol
        v-else
        class="m-0 grid list-none divide-y divide-slate-300 p-0"
    >
      <li
          v-for="comment in comments"
          :key="comment.id"
          class="grid gap-2 px-4 py-4"
          :class=" comment.parentId === null
          ? 'bg-white'
          : 'border-l-2 border-slate-200 bg-slate-50/60 pl-8'"
      >
        <div
            v-if="editingCommentId === comment.id"
            @click.stop
        >
          <CommentEditForm
              :board-slug="boardSlug"
              :post-id="postId"
              :comment="comment"
              :secret-password="secretPassword"
              @updated="handleCommentUpdated"
              @cancel="editingCommentId = null"
          />
        </div>

        <template v-else>
          <div
              class="grid gap-2"
              :class="
      canOpenReplyForm(comment)
        ? 'cursor-pointer rounded p-2 transition hover:bg-sky-50 focus-visible:outline-3 focus-visible:outline-blue-600'
        : '' "
              :role="canOpenReplyForm(comment) ? 'button' : undefined"
              :tabindex="canOpenReplyForm(comment) ? 0 : undefined"
              @click="toggleReplyForm(comment)"
              @keydown.enter.prevent="toggleReplyForm(comment)"
              @keydown.space.prevent="toggleReplyForm(comment)"
          >
            <div class="flex flex-wrap items-center justify-between gap-2">
              <strong class="text-sm font-semibold text-slate-900">
                {{ comment.deleted ? '삭제된 댓글' : comment.authorDisplayName }}
              </strong>

              <time
                  :datetime="comment.createdAt"
                  class="text-xs font-medium text-slate-500"
              >
                {{ formatDate(comment.createdAt) }}
              </time>
            </div>

            <p
                v-if="comment.deleted"
                class="m-0 text-sm font-medium text-slate-500"
            >
              삭제된 댓글입니다.
            </p>

            <p
                v-else
                class="m-0 whitespace-pre-wrap text-sm leading-6 text-slate-800 [overflow-wrap:anywhere]"
            >
              {{ comment.content }}
            </p>

            <small
                v-if="canOpenReplyForm(comment)"
                class="font-medium text-slate-500"
            >
              댓글을 클릭하면 대댓글을 작성할 수 있습니다.
            </small>
          </div>

          <div
              v-if="canEditComment(comment) || canDeleteComment(comment)"
              class="flex justify-end gap-2"
              @click.stop
          >
            <button
                v-if="canEditComment(comment)"
                type="button"
                class="h-8 rounded-md border border-slate-200 bg-white px-3 text-xs font-medium text-slate-600 hover:bg-slate-50"
                @click="startCommentEdit(comment)"
            >
              수정
            </button>

            <button
                v-if="canDeleteComment(comment)"
                type="button"
                class="h-8 rounded-md border border-red-200 bg-white px-3 text-xs font-medium text-red-600 hover:bg-red-50 disabled:opacity-50"
                :disabled="deletingCommentId !== null"
                @click="removeComment(comment)"
            >
              {{
                deletingCommentId === comment.id
                    ? '삭제 중...'
                    : '삭제'
              }}
            </button>
          </div>
        </template>

        <div
            v-if="selectedReplyParentId === comment.id"
            @click.stop
        >
          <CommentCreateForm
              :board-slug="boardSlug"
              :post-id="postId"
              :parent-id="comment.id"
              :secret-password="secretPassword"
              @created="handleReplyCreated"
          />
        </div>
      </li>
    </ol>

    <nav
        v-if="totalPages > 1"
        class="flex items-center justify-center gap-3 border-t border-slate-200 px-4 py-3"
        aria-label="댓글 페이지"
    >
      <button
          type="button"
          class="h-8 rounded border border-slate-200 bg-white px-3 text-xs font-semibold disabled:opacity-40"
          :disabled="page <= 1 || loading"
          @click="changePage(page - 1)"
      >
        이전
      </button>

      <span class="text-xs font-semibold text-slate-700">
        {{ page }} / {{ totalPages }}
      </span>

      <button
          type="button"
          class="h-8 rounded border border-slate-200 bg-white px-3 text-xs font-semibold disabled:opacity-40"
          :disabled="page >= totalPages || loading"
          @click="changePage(page + 1)"
      >
        다음
      </button>
    </nav>
    <CommentCreateForm
        v-if="canCreateComment"
        :board-slug="boardSlug"
        :post-id="postId"
        :secret-password="secretPassword"
        @created="handleCommentCreated"
    />

    <p
        v-else-if="commentsEnabled && !locked && session === null"
        class="m-0 border-b border-slate-200 bg-slate-50 px-4 py-3 text-sm font-medium text-slate-600"
    >
      로그인하면 댓글을 작성할 수 있습니다.
    </p>

    <p
        v-else-if="commentsEnabled && !locked"
        class="m-0 border-b border-slate-200 bg-slate-50 px-4 py-3 text-sm font-medium text-slate-600"
    >
      댓글 작성 권한이 없습니다.
    </p>

  </section>
</template>
