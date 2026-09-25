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
      class="grid gap-3 border-t-2 border-slate-950 bg-sky-50 p-4 [&_.field__label]:font-black! [&_.field__label]:text-slate-950! [&_.field__control]:rounded! [&_.field__control]:border-2! [&_.field__control]:border-slate-950!"
      @submit.prevent="submit"
  >
    <BaseTextarea
        :id="parentId ? `comment-reply-${parentId}` : 'comment-content'"
        v-model="content"
        :label="parentId ? '답글 작성' : '댓글 작성'"
        :placeholder="parentId ? '답글 내용을 입력하세요.' : '댓글 내용을 입력하세요.'"
        :rows="parentId ? 3 : 4"
        :max-length="2000"
    />

    <p
        v-if="errorMessage"
        class="m-0 rounded border-2 border-red-900 bg-rose-50 px-4 py-3 text-sm font-bold text-red-900"
        role="alert"
    >
      {{ errorMessage }}
    </p>

    <div class="flex justify-end">
      <BaseButton
          type="submit"
          class="min-h-0! rounded! border-2! border-slate-950! bg-blue-100! px-3! py-1! text-xs! font-black! text-slate-950! shadow-none!"
          :disabled="submitting"
      >
        {{ submitting ? '등록 중...' : parentId ? '답글 등록' : '댓글 등록' }}
      </BaseButton>
    </div>
  </form>
</template>
