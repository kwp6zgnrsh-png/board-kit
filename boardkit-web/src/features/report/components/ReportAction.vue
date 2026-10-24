<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, ref, watch} from 'vue'

import {ApiError} from '@/api/http'
import {createCommentReport, createPostReport} from '@/api/reportApi'
import BaseButton from '@/components/ui/BaseButton.vue'
import {getAuthSession} from '@/features/auth/services/authSession'
import ReportForm from '@/features/report/components/ReportForm.vue'
import type {ReportCreateRequest} from '@/features/report/types/report'

const props = withDefaults(defineProps<{
  boardSlug: string
  postId: number
  authorId: number
  commentId?: number
  secretPassword?: string
  disabled?: boolean
  size?: 'sm' | 'md'
}>(), {
  disabled: false,
  size: 'sm',
})

const dialog = ref<HTMLDialogElement | null>(null)
const formOpen = ref(false)
const submitting = ref(false)
const completed = ref(false)
const errorMessage = ref('')
const resultMessage = ref('')
let contextSequence = 0

const session = computed(() => getAuthSession())

const canReport = computed(() =>
    session.value !== null &&
    session.value.memberId !== props.authorId,
)

async function openForm() {
  if (
      !canReport.value ||
      props.disabled ||
      submitting.value ||
      completed.value ||
      formOpen.value
  ) return

  const context = contextSequence
  errorMessage.value = ''
  resultMessage.value = ''
  formOpen.value = true

  await nextTick()

  if (context !== contextSequence || !canReport.value) return

  if (props.disabled) {
    formOpen.value = false
    return
  }

  dialog.value?.showModal()
}

function closeForm() {
  formOpen.value = false
  dialog.value?.close()
}

function cancelReport() {
  if (submitting.value) return
  closeForm()
}

async function submitReport(request: ReportCreateRequest) {
  const memberId = session.value?.memberId

  if (
      memberId === undefined ||
      !canReport.value ||
      props.disabled ||
      submitting.value ||
      completed.value ||
      !formOpen.value
  ) return

  const context = contextSequence
  submitting.value = true
  errorMessage.value = ''

  try {
    if (props.commentId === undefined) {
      await createPostReport(
          props.boardSlug,
          props.postId,
          request,
          props.secretPassword,
      )
    } else {
      await createCommentReport(
          props.boardSlug,
          props.postId,
          props.commentId,
          request,
          props.secretPassword,
      )
    }

    if (
        context !== contextSequence ||
        session.value?.memberId !== memberId
    ) return

    completed.value = true
    resultMessage.value = '신고가 등록되었습니다.'
    closeForm()
  } catch (error) {
    if (
        context !== contextSequence ||
        session.value?.memberId !== memberId
    ) return

    if (error instanceof ApiError && error.status === 409) {
      completed.value = true
      resultMessage.value = '이미 신고한 대상입니다.'
      closeForm()
    } else {
      errorMessage.value = getErrorMessage(error)
    }
  } finally {
    if (context === contextSequence) submitting.value = false
  }
}

function getErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 400) {
      return '신고 사유와 상세 내용을 확인해 주세요.'
    }
    if (error.status === 401) {
      return '다시 로그인해 주세요.'
    }
    if (error.status === 403) {
      return '신고할 수 없습니다. 회원 상태나 게시글 접근 권한을 확인해 주세요.'
    }
    if (error.status === 404) {
      return '게시글 또는 댓글을 찾을 수 없습니다. 새로고침해 확인해 주세요.'
    }
  }

  return '신고 결과를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

watch(
    [
      () => props.boardSlug,
      () => props.postId,
      () => props.commentId,
      () => props.authorId,
      () => props.secretPassword,
      () => session.value?.memberId ?? null,
      () => session.value?.role ?? null,
    ],
    () => {
      contextSequence++
      closeForm()
      submitting.value = false
      completed.value = false
      errorMessage.value = ''
      resultMessage.value = ''
    },
    {flush: 'sync'},
)

onBeforeUnmount(() => {
  contextSequence++
  dialog.value?.close()
})
</script>

<template>
  <div v-if="canReport" class="grid min-w-0 justify-items-end gap-2">
    <BaseButton
        variant="secondary"
        :size="size"
        :disabled="disabled || submitting || completed"
        @click="openForm"
    >
      {{ completed ? '신고 완료' : '신고' }}
    </BaseButton>

    <p
        v-if="resultMessage"
        role="status"
        class="max-w-xs text-xs text-zinc-600"
    >
      {{ resultMessage }}
    </p>

    <dialog
        ref="dialog"
        :aria-label="commentId === undefined ? '게시글 신고' : '댓글 신고'"
        class="m-auto max-h-[calc(100dvh_-_2rem)] w-[calc(100%_-_2rem)] max-w-lg overflow-y-auto rounded-lg border border-zinc-200 bg-white p-5 shadow-xl backdrop:bg-zinc-950/40"
        @cancel.prevent="cancelReport"
    >
      <ReportForm
          v-if="formOpen"
          :submitting="submitting"
          :error-message="errorMessage"
          @submit="submitReport"
          @cancel="cancelReport"
      />
    </dialog>
  </div>
</template>