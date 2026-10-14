<script setup lang="ts">
import {ref} from 'vue'
import type {ChainedCommands} from '@tiptap/core'
import type {Editor} from '@tiptap/vue-3'
import {normalizeWebLink} from '@/features/post/editor/postEditor'

const props = withDefaults(defineProps<{
  editor: Editor
  disabled?: boolean
}>(), {
  disabled: false,
})

interface ToolbarButton {
  label: string
  title: string
  labelClass?: string
  activeName?: string
  activeAttrs?: Record<string, unknown>
  command: (chain: ChainedCommands) => ChainedCommands
}

const errorMessage = ref('')

const buttons: ToolbarButton[] = [
  {
    label: '본문',
    title: '일반 문단',
    activeName: 'paragraph',
    command: chain => chain.setParagraph(),
  },
  {
    label: 'H2',
    title: '큰 본문 제목',
    activeName: 'heading',
    activeAttrs: {level: 2},
    command: chain => chain.toggleHeading({level: 2}),
  },
  {
    label: 'H3',
    title: '작은 본문 제목',
    activeName: 'heading',
    activeAttrs: {level: 3},
    command: chain => chain.toggleHeading({level: 3}),
  },
  {
    label: 'B',
    title: '굵게',
    labelClass: 'font-bold',
    activeName: 'bold',
    command: chain => chain.toggleBold(),
  },
  {
    label: 'I',
    title: '기울임',
    labelClass: 'italic',
    activeName: 'italic',
    command: chain => chain.toggleItalic(),
  },
  {
    label: 'U',
    title: '밑줄',
    labelClass: 'underline',
    activeName: 'underline',
    command: chain => chain.toggleUnderline(),
  },
  {
    label: 'S',
    title: '취소선',
    labelClass: 'line-through',
    activeName: 'strike',
    command: chain => chain.toggleStrike(),
  },
  {
    label: '• 목록',
    title: '글머리표 목록',
    activeName: 'bulletList',
    command: chain => chain.toggleBulletList(),
  },
  {
    label: '1. 목록',
    title: '번호 목록',
    activeName: 'orderedList',
    command: chain => chain.toggleOrderedList(),
  },
  {
    label: '인용',
    title: '인용문',
    activeName: 'blockquote',
    command: chain => chain.toggleBlockquote(),
  },
  {
    label: '</>',
    title: '인라인 코드',
    labelClass: 'font-mono',
    activeName: 'code',
    command: chain => chain.toggleCode(),
  },
  {
    label: '코드 블록',
    title: '코드 블록',
    activeName: 'codeBlock',
    command: chain => chain.toggleCodeBlock(),
  },
  {
    label: '구분선',
    title: '구분선 삽입',
    command: chain => chain.setHorizontalRule(),
  },
  {
    label: '↶',
    title: '실행 취소',
    command: chain => chain.undo(),
  },
  {
    label: '↷',
    title: '다시 실행',
    command: chain => chain.redo(),
  },
]

function isActive(button: ToolbarButton): boolean {
  return button.activeName
      ? props.editor.isActive(button.activeName, button.activeAttrs)
      : false
}

function canRun(button: ToolbarButton): boolean {
  return !props.disabled &&
      button.command(props.editor.can().chain().focus()).run()
}

function run(button: ToolbarButton) {
  if (!canRun(button)) return

  errorMessage.value = ''
  button.command(props.editor.chain().focus()).run()
}

function editLink() {
  if (props.disabled) return
  errorMessage.value = ''

  if (
      props.editor.isActive('code') ||
      props.editor.isActive('codeBlock')
  ) {
    errorMessage.value = '코드에는 링크를 적용할 수 없습니다.'
    return
  }

  if (
      props.editor.state.selection.empty &&
      !props.editor.isActive('link')
  ) {
    errorMessage.value = '링크를 연결할 글자를 먼저 선택해 주세요.'
    return
  }

  const currentHref = props.editor.getAttributes('link').href
  const input = window.prompt(
      'http 또는 https 주소를 입력하세요. 비우면 링크를 제거합니다.',
      typeof currentHref === 'string' ? currentHref : 'https://',
  )

  if (input === null) return

  if (!input.trim()) {
    props.editor.chain().focus().extendMarkRange('link').unsetLink().run()
    return
  }

  const href = normalizeWebLink(input)
  if (!href) {
    errorMessage.value = '올바른 http 또는 https 주소를 입력해 주세요.'
    return
  }

  const applied = props.editor.chain()
      .focus()
      .extendMarkRange('link')
      .setLink({href})
      .run()

  if (!applied) {
    errorMessage.value = '선택한 부분에 링크를 적용할 수 없습니다.'
  }
}
</script>

<template>
  <div class="border-b border-slate-200 bg-slate-50 p-2">
    <div class="flex flex-wrap gap-1" role="toolbar" aria-label="본문 서식">
      <button
          v-for="button in buttons"
          :key="button.title"
          type="button"
          :title="button.title"
          :aria-label="button.title"
          :aria-pressed="button.activeName ? isActive(button) : undefined"
          :disabled="!canRun(button)"
          class="inline-flex h-8 min-w-8 items-center justify-center rounded border px-2 text-xs font-medium focus-visible:outline-2 focus-visible:outline-blue-600 disabled:cursor-not-allowed disabled:opacity-40"
          :class="isActive(button)
          ? 'border-blue-300 bg-blue-50 text-blue-700'
          : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-100'"
          @mousedown.prevent
          @click="run(button)"
      >
        <span :class="button.labelClass">{{ button.label }}</span>
      </button>

      <button
          type="button"
          title="링크 추가·수정·제거"
          :aria-pressed="editor.isActive('link')"
          :disabled="disabled"
          class="h-8 rounded border px-2 text-xs font-medium focus-visible:outline-2 focus-visible:outline-blue-600 disabled:opacity-40"
          :class="editor.isActive('link')
          ? 'border-blue-300 bg-blue-50 text-blue-700'
          : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-100'"
          @mousedown.prevent
          @click="editLink"
      >
        링크
      </button>
    </div>

    <p
        v-if="errorMessage"
        role="alert"
        class="mt-2 text-xs text-red-600"
    >
      {{ errorMessage }}
    </p>
  </div>
</template>