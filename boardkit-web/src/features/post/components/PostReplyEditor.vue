<script setup lang="ts">
import {onBeforeUnmount, onMounted, shallowRef, watch} from 'vue'
import {Editor, EditorContent} from '@tiptap/vue-3'
import type {JSONContent} from '@tiptap/core'

import PostEditorToolbar from '@/features/post/components/PostEditorToolbar.vue'
import {
  createTextEditorExtensions,
  POST_DOCUMENT_CLASS,
} from '@/features/post/editor/postEditor'

const props = withDefaults(defineProps<{
  disabled?: boolean
}>(), {
  disabled: false,
})

const contentDocument = defineModel<JSONContent>({required: true})
const editor = shallowRef<Editor | null>(null)

onMounted(() => {
  editor.value = new Editor({
    extensions: createTextEditorExtensions(),
    content: contentDocument.value,
    editable: !props.disabled,
    editorProps: {
      attributes: {
        role: 'textbox',
        'aria-label': '공식 답변 본문',
        'aria-multiline': 'true',
        class: 'min-h-56 px-4 py-4 outline-none ' + POST_DOCUMENT_CLASS,
      },
      handlePaste(_view, event) {
        return (event.clipboardData?.files.length ?? 0) > 0
      },
      handleDrop(_view, event) {
        return (event.dataTransfer?.files.length ?? 0) > 0
      },
    },
    onUpdate({editor: currentEditor}) {
      contentDocument.value = currentEditor.getJSON()
    },
  })
})

watch([editor, contentDocument], ([currentEditor, document]) => {
  if (!currentEditor) return

  if (JSON.stringify(currentEditor.getJSON()) === JSON.stringify(document)) {
    return
  }

  currentEditor.commands.setContent(document, {
    emitUpdate: false,
    errorOnInvalidContent: true,
  })
})

watch(() => props.disabled, disabled => {
  editor.value?.setEditable(!disabled, false)
})

onBeforeUnmount(() => {
  editor.value?.destroy()
})
</script>

<template>
  <section
      class="overflow-hidden surface-panel"
      aria-label="공식 답변 편집기"
  >
    <template v-if="editor">
      <PostEditorToolbar :editor="editor" :disabled="disabled"/>

      <EditorContent
          :editor="editor"
          class="max-h-[60vh] overflow-y-auto"
          :class="{ 'bg-zinc-100': disabled }"
      />
    </template>
  </section>
</template>