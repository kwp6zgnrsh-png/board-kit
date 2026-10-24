<script setup lang="ts">
import {computed, ref} from 'vue'
import type {JSONContent} from '@tiptap/core'

import BaseButton from '@/components/ui/BaseButton.vue'
import PostReplyEditor from '@/features/post/components/PostReplyEditor.vue'
import type {PostReplyResponse} from '@/features/post/types/postReply'

const props = withDefaults(defineProps<{
  reply?: PostReplyResponse | null
  submitting?: boolean
  errorMessage?: string
  disabled?: boolean
}>(), {
  reply: null,
  submitting: false,
  errorMessage: '',
  disabled: false,
})

const emit = defineEmits<{
  submit: [document: JSONContent]
  cancel: []
}>()

const editing = props.reply !== null
const formDisabled = computed(() => props.disabled || props.submitting)
const validationError = ref('')

const contentDocument = ref<JSONContent>(
    props.reply?.contentDocument
        ? JSON.parse(JSON.stringify(props.reply.contentDocument))
        : documentFromText(props.reply?.content ?? ''),
)

const text = computed(() => extractText(contentDocument.value))
const textLength = computed(() => text.value.length)
const displayedError = computed(
    () => validationError.value || props.errorMessage,
)

function documentFromText(value: string): JSONContent {
  return {
    type: 'doc',
    content: value.split(/\r?\n/).map(line => ({
      type: 'paragraph',
      content: line ? [{type: 'text', text: line}] : [],
    })),
  }
}

function extractText(document: JSONContent): string {
  const paragraphs: string[] = []

  function visit(node: JSONContent) {
    if (
        node.type === 'paragraph' ||
        node.type === 'heading' ||
        node.type === 'codeBlock'
    ) {
      const value = (node.content ?? []).map(child => {
        if (child.type === 'text') return child.text ?? ''
        if (child.type === 'hardBreak') return '\n'
        return ''
      }).join('')

      paragraphs.push(value)
      return
    }

    node.content?.forEach(visit)
  }

  visit(document)
  return paragraphs.join('\n')
}

function submit() {
  if (formDisabled.value) return

  if (props.submitting) return

  validationError.value = ''

  if (!text.value.trim()) {
    validationError.value = '공식 답변 내용을 입력해 주세요.'
    return
  }

  if (textLength.value > 2000) {
    validationError.value = '공식 답변은 2,000자 이하여야 합니다.'
    return
  }

  emit('submit', JSON.parse(JSON.stringify(contentDocument.value)))
}
</script>

<template>
  <form class="grid gap-4" :aria-busy="submitting" @submit.prevent="submit">
    <div>
      <h3 class="mb-2 text-sm font-semibold text-zinc-800">
        {{ editing ? '공식 답변 수정' : '공식 답변 작성' }}
      </h3>

      <PostReplyEditor
          v-model="contentDocument"
          :disabled="formDisabled"
      />
    </div>

    <div class="flex flex-wrap items-center justify-between gap-2 text-xs">
      <p class="text-zinc-500">
        서식을 사용할 수 있으며 이미지·첨부파일은 지원하지 않습니다.
      </p>

      <p
          class="tabular-nums"
          :class="textLength > 2000 ? 'text-red-600' : 'text-zinc-500'"
      >
        {{ textLength.toLocaleString() }} / 2,000자
      </p>
    </div>

    <p
        v-if="displayedError"
        role="alert"
        class="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
    >
      {{ displayedError }}
    </p>

    <div class="flex justify-end gap-2">
      <BaseButton
          variant="secondary"
          size="sm"
          :disabled="submitting"
          @click="emit('cancel')"
      >
        취소
      </BaseButton>

      <BaseButton type="submit" size="sm" :disabled="formDisabled">
        {{ submitting ? '저장 중...' : editing ? '수정' : '등록' }}
      </BaseButton>
    </div>
  </form>
</template>