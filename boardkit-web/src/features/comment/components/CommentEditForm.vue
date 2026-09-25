<script setup lang="ts">
import { ref } from 'vue'

import { ApiError } from '@/api/http'
import { updateComment } from '@/api/commentApi'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseTextarea from '@/components/ui/BaseTextarea.vue'
import type {CommentItemResponse} from '@/features/comment/types/comment'

const props = defineProps<{
  boardSlug: string
  postId: number
  comment: CommentItemResponse
  secretPassword?: string
}>()

const emit = defineEmits<{
  updated: []
  cancel: []
}>()

const content = ref(props.comment.content ?? '')
const submitting = ref(false)
const errorMessage = ref('')

async function submit() {
  errorMessage.value = ''

  const normalizedContent = content.value.trim()

  if (!normalizedContent) {
    errorMessage.value = '댓글 내용을 입력하세요.'
    return
  }

  submitting.value = true

  try {
    await updateComment(
        props.boardSlug,
        props.postId,
        props.comment.id,
        {
          content: normalizedContent,
          version: props.comment.version,
        },
        props.secretPassword,
    )

    emit('updated')
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      errorMessage.value =
          '댓글이 변경되었습니다. 새로고침 후 다시 시도해주세요.'
    } else {
      errorMessage.value =
          error instanceof Error
              ? error.message
              : '댓글 수정에 실패했습니다.'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form
      class="grid gap-3 rounded border-2 border-slate-950 bg-blue-50 p-3"
      @submit.prevent="submit"
  >
    <BaseTextarea
        :id="`comment-edit-${comment.id}`"
        v-model="content"
        label="댓글 수정"
        :rows="3"
        :max-length="2000"
    />

    <p
        v-if="errorMessage"
        class="m-0 rounded border-2 border-red-900 bg-rose-50 p-3 text-sm font-bold text-red-900"
        role="alert"
    >
      {{ errorMessage }}
    </p>

    <div class="flex justify-end gap-2">
      <BaseButton
          type="button"
          variant="secondary"
          class="min-h-0! rounded! border-2! border-slate-950! bg-slate-100! px-3! py-1! text-xs! font-black! text-slate-950! shadow-none!"
          :disabled="submitting"
          @click="emit('cancel')"
      >
        취소
      </BaseButton>

      <BaseButton
          type="submit"
          class="min-h-0! rounded! border-2! border-slate-950! bg-blue-100! px-3! py-1! text-xs! font-black! text-slate-950! shadow-none!"
          :disabled="submitting"
      >
        {{ submitting ? '수정 중...' : '수정 완료' }}
      </BaseButton>
    </div>
  </form>
</template>
