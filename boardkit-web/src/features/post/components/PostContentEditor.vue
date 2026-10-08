<script setup lang="ts">
import { onBeforeUnmount, onMounted, shallowRef, watch } from 'vue'
import { Editor, EditorContent } from '@tiptap/vue-3'
import type { JSONContent } from '@tiptap/core'
import Document from '@tiptap/extension-document'
import Paragraph from '@tiptap/extension-paragraph'
import Text from '@tiptap/extension-text'
import HardBreak from '@tiptap/extension-hard-break'
import { Gapcursor, UndoRedo } from '@tiptap/extensions'
import { PostImage } from '@/features/post/editor/postImage'
import type { PostEditorImage } from '@/features/post/types/post'

const props = withDefaults(defineProps<{
  disabled?: boolean
}>(), {
  disabled: false,
})

const contentDocument = defineModel<JSONContent>({ required: true })
const editor = shallowRef<Editor | null>(null)

onMounted(() => {
  editor.value = new Editor({
    extensions: [
      Document,
      Paragraph,
      Text,
      HardBreak,
      PostImage,
      Gapcursor,
      UndoRedo,
    ],
    content: contentDocument.value,
    editable: !props.disabled,
    editorProps: {
      attributes: {
        role: 'textbox',
        'aria-label': '게시글 본문',
        'aria-multiline': 'true',
        class:
            'min-h-72 p-4 text-sm leading-7 outline-none break-words focus:ring-2 focus:ring-inset focus:ring-blue-100 ' +
            '[&_p]:my-2 [&_p]:min-h-7',
      },
      handlePaste(_view, event) {
        return (event.clipboardData?.files.length ?? 0) > 0
      },
      handleDrop(_view, event) {
        return (event.dataTransfer?.files.length ?? 0) > 0
      },
    },
    onUpdate({ editor: currentEditor }) {
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

function applyImages(images: PostEditorImage[]): boolean {
  const currentEditor = editor.value
  if (!currentEditor || props.disabled) return false
  if (new Set(images.map(image => image.key)).size !== images.length) return false

  const currentBlocks = currentEditor.getJSON().content ?? []
  const blocks: JSONContent[] = []
  let remainingSlots = currentBlocks.filter(block => block.type === 'image').length
  let nextIndex = 0

  function imageBlock(image: PostEditorImage): JSONContent {
    return {
      type: 'image',
      attrs: {
        imageKey: image.key,
        src: image.previewUrl,
        alt: image.name,
      },
    }
  }

  function appendRemainingImages() {
    while (nextIndex < images.length) {
      const image = images[nextIndex]
      if (!image) break

      blocks.push(imageBlock(image), { type: 'paragraph' })
      nextIndex++
    }
  }

  if (remainingSlots === 0) {
    appendRemainingImages()
  }

  for (const block of currentBlocks) {
    if (block.type !== 'image') {
      blocks.push(block)
      continue
    }

    const image = images[nextIndex]
    if (image) {
      blocks.push(imageBlock(image))
      nextIndex++
    }

    remainingSlots--
    if (remainingSlots === 0) {
      appendRemainingImages()
    }
  }

  return currentEditor.chain()
      .insertContentAt(
          { from: 0, to: currentEditor.state.doc.content.size },
          blocks.length ? blocks : [{ type: 'paragraph' }],
          { updateSelection: false },
      )
      .focus()
      .run()
}

watch(() => props.disabled, disabled => {
  editor.value?.setEditable(!disabled, false)
})

onBeforeUnmount(() => {
  editor.value?.destroy()
})

defineExpose({ applyImages })
</script>

<template>
  <section class="overflow-hidden rounded border border-slate-200 bg-white">
    <header class="border-b border-slate-200 bg-slate-50 px-4 py-2.5">
      <p class="m-0 text-sm font-semibold text-slate-900">
        본문
      </p>
    </header>

    <EditorContent
        v-if="editor"
        :editor="editor"
        :class="{ 'bg-slate-100 text-slate-500': disabled }"
    />
  </section>
</template>
