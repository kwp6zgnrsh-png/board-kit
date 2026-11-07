<script setup lang="ts">
import { ref } from 'vue'

import { createComment } from '@/api/commentApi'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseTextarea from '@/components/ui/BaseTextarea.vue'

const props = defineProps<{
  boardSlug: string
  postId: number
  parentId?: number
  secretPassword?: string
}>()

const emit = defineEmits<{
  created: [commentId: number]
}>()

const content = ref('')
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
    const response = await createComment(
        props.boardSlug,
        props.postId,
        {
          content: normalizedContent,
          parentId: props.parentId ?? null,
        },
        props.secretPassword,
    )

    content.value = ''
    emit('created', response.id)
  } catch (error) {
    errorMessage.value =
        error instanceof Error
            ? error.message
            : '댓글 등록에 실패했습니다.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form
      class="my-4 -mx-3 grid min-w-0 gap-2 border border-border bg-zinc-50 p-3"
      @submit.prevent="submit"
  >
    <BaseTextarea
        :id="parentId ? `comment-reply-${parentId}` : 'comment-content'"
        v-model="content"
        :label="parentId ? '답글 작성' : '댓글 작성'"
        :placeholder="parentId ? '답글 내용을 입력하세요.' : '댓글 내용을 입력하세요.'"
        :rows="parentId ? 3 : 4"
        :max-length="2000"
        :show-count="false"
    />

    <p
        v-if="errorMessage"
        class="m-0 rounded-none border border-red-200 bg-rose-50 px-4 py-3 text-sm font-medium text-red-900"
        role="alert"
    >
      {{ errorMessage }}
    </p>

    <div class="flex justify-end">
      <BaseButton
          type="submit"
          size="sm"
          :disabled="submitting"
      >
        {{ submitting ? '등록 중...' : parentId ? '답글 등록' : '댓글 등록' }}
      </BaseButton>
    </div>
  </form>
</template>
