<script setup lang="ts">
import {onBeforeUnmount, onMounted, shallowRef, watch} from 'vue'
import {Editor, EditorContent} from '@tiptap/vue-3'
import type {JSONContent} from '@tiptap/core'
import {
  createPostEditorExtensions,
  POST_DOCUMENT_CLASS,
} from '@/features/post/editor/postEditor'
import {getBodyImageNodes} from '@/features/post/editor/postDocument'
import type {PostEditorImage} from '@/features/post/types/post'
import PostEditorToolbar from './PostEditorToolbar.vue'

const props = withDefaults(defineProps<{
  disabled?: boolean
}>(), {
  disabled: false,
})

const contentDocument = defineModel<JSONContent>({required: true})
const editor = shallowRef<Editor | null>(null)

onMounted(() => {
  editor.value = new Editor({
    extensions: createPostEditorExtensions(),
    content: contentDocument.value,
    editable: !props.disabled,
    editorProps: {
      attributes: {
        role: 'textbox',
        'aria-label': '게시글 본문',
        'aria-multiline': 'true',
        class:
            'min-h-72 p-4 outline-none focus:ring-2 focus:ring-inset focus:ring-blue-100 ' +
            POST_DOCUMENT_CLASS,
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

function applyImages(images: PostEditorImage[]): boolean {
  const currentEditor = editor.value
  if (!currentEditor || props.disabled) return false
  if (new Set(images.map(image => image.key)).size !== images.length) return false

  const source = currentEditor.getJSON()
  let remainingSlots = getBodyImageNodes(source).length
  const hadImageSlots = remainingSlots > 0
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

  function appendRemainingImages(target: JSONContent[]) {
    while (nextIndex < images.length) {
      const image = images[nextIndex]
      if (!image) break

      target.push(imageBlock(image), {type: 'paragraph'})
      nextIndex++
    }
  }

  function rewriteNodes(nodes: JSONContent[]): JSONContent[] {
    const result: JSONContent[] = []

    for (const node of nodes) {
      if (node.type === 'image') {
        const image = images[nextIndex]
        if (image) {
          result.push(imageBlock(image))
          nextIndex++
        }

        remainingSlots--
        if (remainingSlots === 0) {
          appendRemainingImages(result)
        }
        continue
      }

      const copied: JSONContent = {...node}

      if (node.content) {
        copied.content = rewriteNodes(node.content)
      }
      if (
          copied.type === 'blockquote' &&
          copied.content?.length === 0
      ) {
        copied.content = [{type: 'paragraph'}]
      }

      result.push(copied)
    }

    return result
  }

  let blocks = rewriteNodes(source.content ?? [])

  if (!hadImageSlots) {
    const added: JSONContent[] = []
    appendRemainingImages(added)
    blocks = [...added, ...blocks]
  }
  if (blocks.length === 0) {
    blocks = [{type: 'paragraph'}]
  }

  try {
    currentEditor.state.schema.nodeFromJSON({
      ...source,
      content: blocks,
    }).check()

    return currentEditor.chain()
        .insertContentAt(
            {from: 0, to: currentEditor.state.doc.content.size},
            blocks,
            {updateSelection: false},
        )
        .focus()
        .run()
  } catch {
    return false
  }
}

watch(() => props.disabled, disabled => {
  editor.value?.setEditable(!disabled, false)
})

onBeforeUnmount(() => {
  editor.value?.destroy()
})

defineExpose({applyImages})
</script>

<template>
  <section class="overflow-hidden rounded-md border border-slate-200 bg-white">
    <header class="border-b border-slate-200 px-4 py-2.5">
      <p class="m-0 text-sm font-semibold text-slate-900">본문</p>
    </header>

    <template v-if="editor">
      <PostEditorToolbar :editor="editor" :disabled="disabled"/>

      <EditorContent
          :editor="editor"
          :class="{ 'bg-slate-100': disabled }"
      />
    </template>
  </section>
</template>