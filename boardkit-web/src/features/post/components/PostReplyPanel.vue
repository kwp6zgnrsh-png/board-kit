<script setup lang="ts">
import {computed, onBeforeUnmount, ref, shallowRef, watch} from 'vue'
import type {JSONContent} from '@tiptap/core'

import {ApiError} from '@/api/http'
import {
  createPostReply,
  deletePostReply,
  getPostReply,
  updatePostReply,
} from '@/api/postReplyApi'
import BaseButton from '@/components/ui/BaseButton.vue'
import {getAuthSession} from '@/features/auth/services/authSession'
import PostContent from '@/features/post/components/PostContent.vue'
import PostReplyForm from '@/features/post/components/PostReplyForm.vue'
import type {PostReplyResponse} from '@/features/post/types/postReply'

const props = defineProps<{
  boardSlug: string
  postId: number
  enabled: boolean
  secretPassword?: string
  postBlinded: boolean
  boardArchived: boolean
}>()

const session = computed(() => getAuthSession())
const reply = ref<PostReplyResponse | null>(null)
const editingReply = shallowRef<PostReplyResponse | null>(null)

const loaded = ref(false)
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const formOpen = ref(false)
const formSequence = ref(0)

const loadError = ref('')
const actionError = ref('')
const resultMessage = ref('')

let contextSequence = 0
let loadSequence = 0

const busy = computed(() => loading.value || saving.value || deleting.value)

const canManage = computed(() =>
    session.value?.role === 'ADMIN' &&
    props.enabled &&
    !props.boardArchived &&
    !props.postBlinded &&
    loaded.value,
)

const visible = computed(() => Boolean(
    loading.value ||
    loadError.value ||
    reply.value ||
    formOpen.value ||
    canManage.value ||
    resultMessage.value ||
    actionError.value,
))

function isCurrent(context: number): boolean {
  return context === contextSequence
}

async function loadReply() {
  const context = contextSequence
  const request = ++loadSequence

  loading.value = true
  loaded.value = false
  loadError.value = ''
  reply.value = null

  try {
    const response = await getPostReply(
        props.boardSlug,
        props.postId,
        props.secretPassword,
    )

    if (!isCurrent(context) || request !== loadSequence) return

    reply.value = response
    loaded.value = true
  } catch (error) {
    if (!isCurrent(context) || request !== loadSequence) return

    if (error instanceof ApiError && error.status === 403) {
      loadError.value = '공식 답변을 조회할 권한이 없습니다.'
    } else if (error instanceof ApiError && error.status === 404) {
      loadError.value = '게시판 또는 게시글을 찾을 수 없습니다.'
    } else {
      loadError.value = '공식 답변을 불러오지 못했습니다. 다시 시도해 주세요.'
    }
  } finally {
    if (isCurrent(context) && request === loadSequence) {
      loading.value = false
    }
  }
}

function refreshReply() {
  if (busy.value) return
  resultMessage.value = ''
  void loadReply()
}

function openForm() {
  if (!canManage.value || busy.value || formOpen.value) return

  editingReply.value = reply.value
      ? JSON.parse(JSON.stringify(reply.value))
      : null

  actionError.value = ''
  resultMessage.value = ''
  formSequence.value++
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
  editingReply.value = null
  actionError.value = ''
}

async function submitReply(contentDocument: JSONContent) {
  if (!canManage.value || busy.value || !formOpen.value) return

  const context = contextSequence
  const target = editingReply.value
  const slug = props.boardSlug
  const postId = props.postId

  saving.value = true
  actionError.value = ''
  resultMessage.value = ''

  try {
    let created: PostReplyResponse | null = null

    if (target) {
      await updatePostReply(slug, postId, target.id, {
        contentDocument,
        version: target.version,
      })
    } else {
      created = await createPostReply(slug, postId, {contentDocument})
    }

    if (!isCurrent(context)) return

    closeForm()
    resultMessage.value = target
        ? '공식 답변이 수정되었습니다.'
        : '공식 답변이 등록되었습니다.'

    if (created) {
      reply.value = created
      loaded.value = true
    } else {
      await loadReply()
    }
  } catch (error) {
    if (!isCurrent(context)) return
    actionError.value = changeErrorMessage(error)
  } finally {
    if (isCurrent(context)) saving.value = false
  }
}

async function removeReply() {
  const target = reply.value

  if (!canManage.value || busy.value || formOpen.value || !target) return
  if (!window.confirm('공식 답변을 삭제하시겠습니까?')) return

  const context = contextSequence
  deleting.value = true
  actionError.value = ''
  resultMessage.value = ''

  try {
    await deletePostReply(
        props.boardSlug,
        props.postId,
        target.id,
        target.version,
    )

    if (!isCurrent(context)) return

    reply.value = null
    loaded.value = true
    resultMessage.value = '공식 답변이 삭제되었습니다.'
  } catch (error) {
    if (!isCurrent(context)) return
    actionError.value = changeErrorMessage(error)
  } finally {
    if (isCurrent(context)) deleting.value = false
  }
}

function changeErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'CONTENT_BLINDED') {
      return '블라인드된 게시글에서는 공식 답변을 변경할 수 없습니다. 페이지를 새로고침해 주세요.'
    }
    if (error.status === 400) {
      return '답변 내용을 확인해 주세요. 이미지는 사용할 수 없으며 본문은 2,000자 이하여야 합니다.'
    }
    if (error.status === 401) return '다시 로그인해 주세요.'
    if (error.status === 403) {
      return '관리 권한이나 게시판 설정이 변경되었을 수 있습니다. 페이지를 새로고침해 확인해 주세요.'
    }
    if (error.status === 404) return '게시판 또는 게시글을 찾을 수 없습니다.'
    if (error.status === 409) {
      return '공식 답변이 변경되었습니다. 새로고침으로 최신 답변을 확인한 뒤 편집을 다시 시작해 주세요.'
    }
  }

  return '변경 결과를 확인하지 못했습니다. 새로고침으로 상태를 확인해 주세요.'
}

function formatDate(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

watch(
    [
      () => props.boardArchived,
      () => props.boardSlug,
      () => props.postId,
      () => props.secretPassword,
      () => session.value?.memberId ?? null,
      () => session.value?.role ?? null,
      () => props.postBlinded,
    ],
    () => {
      contextSequence++
      closeForm()
      saving.value = false
      deleting.value = false
      resultMessage.value = ''
      void loadReply()
    },
    {immediate: true, flush: 'sync'},
)

onBeforeUnmount(() => {
  contextSequence++
  loadSequence++
})
</script>

<template>
  <section
      v-if="visible"
      class="overflow-hidden surface-panel"
      aria-label="관리자 공식 답변"
      :aria-busy="busy"
  >
    <header class="flex flex-wrap items-center justify-between gap-3 border-b border-zinc-200 bg-accent px-5 py-3">
      <h2 class="text-sm font-medium text-zinc-900">관리자 공식 답변</h2>

      <div class="flex items-center gap-2">
        <BaseButton
            variant="secondary"
            size="sm"
            :disabled="busy"
            @click="refreshReply"
        >
          새로고침
        </BaseButton>

        <template v-if="canManage && !formOpen">
          <BaseButton size="sm" :disabled="busy" @click="openForm">
            {{ reply ? '수정' : '답변 작성' }}
          </BaseButton>

          <BaseButton
              v-if="reply"
              variant="danger"
              size="sm"
              :disabled="busy"
              @click="removeReply"
          >
            {{ deleting ? '삭제 중...' : '삭제' }}
          </BaseButton>
        </template>
      </div>
    </header>

    <div class="grid gap-4 p-5">
      <p
          v-if="resultMessage"
          role="status"
          class="rounded-none border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700"
      >
        {{ resultMessage }}
      </p>

      <p
          v-if="loadError"
          role="alert"
          class="rounded-none border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
      >
        {{ loadError }}
      </p>

      <p v-else-if="loading" class="py-4 text-center text-sm text-zinc-500">
        공식 답변을 불러오는 중입니다.
      </p>

      <article v-if="reply">
        <div class="flex flex-wrap gap-x-4 gap-y-1 text-xs text-zinc-500">
          <span class="font-medium text-zinc-700">
            {{ reply.authorDisplayName }}
          </span>
          <span>작성 {{ formatDate(reply.createdAt) }}</span>
          <span v-if="reply.updatedAt !== reply.createdAt">
            수정 {{ formatDate(reply.updatedAt) }}
          </span>
        </div>

        <PostContent
            :key="`${reply.id}:${reply.version}`"
            class="min-h-0! py-3!"
            :board-slug="boardSlug"
            :post-id="postId"
            :content="reply.content"
            :content-document="reply.contentDocument"
            :files="[]"
            :secret-password="secretPassword"
        />
      </article>

      <p
          v-else-if="loaded && !formOpen"
          class="py-4 text-center text-sm text-zinc-500"
      >
        등록된 공식 답변이 없습니다.
      </p>

      <p
          v-if="actionError && !formOpen"
          role="alert"
          class="rounded-none border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
      >
        {{ actionError }}
      </p>

      <PostReplyForm
          v-if="formOpen && canManage"
          :key="formSequence"
          :reply="editingReply"
          :submitting="saving"
          :disabled="loading || deleting || !canManage"
          :error-message="actionError"
          @submit="submitReply"
          @cancel="closeForm"
      />
    </div>
  </section>
</template>