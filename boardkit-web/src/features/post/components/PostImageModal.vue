<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { PostEditorImage } from '@/features/post/types/post'

const props = defineProps<{
  open: boolean
  images: PostEditorImage[]
  otherFileCount: number
  maxFileCount: number
  maxFileSizeBytes: number
  allowAdd: boolean
}>()

const emit = defineEmits<{
  apply: [images: PostEditorImage[]]
  cancel: []
}>()

const dialog = ref<HTMLDialogElement | null>(null)
const draftImages = ref<PostEditorImage[]>([])
const errorMessage = ref('')
const draggingKey = ref<string | null>(null)

// 이번 모달에서 새로 만든 URL만 관리한다.
const createdUrls = new Set<string>()
const imageExtensions = new Set(['.jpg', '.jpeg', '.png', '.gif'])

function releaseCreatedUrls(keep = new Set<string>()) {
  for (const url of createdUrls) {
    if (!keep.has(url)) {
      URL.revokeObjectURL(url)
    }
  }
  createdUrls.clear()
}

function synchronizeOpen() {
  if (!dialog.value) return

  if (props.open) {
    if (dialog.value.open) return

    draftImages.value = [...props.images]
    errorMessage.value = ''
    draggingKey.value = null
    dialog.value.showModal()
  } else {
    releaseCreatedUrls()
    dialog.value.close()
  }
}

watch(() => props.open, synchronizeOpen, { flush: 'post' })
onMounted(synchronizeOpen)
onBeforeUnmount(() => releaseCreatedUrls())

function selectImages(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  errorMessage.value = ''

  if (!props.allowAdd || files.length === 0) return

  const totalCount =
      props.otherFileCount + draftImages.value.length + files.length

  if (totalCount > props.maxFileCount) {
    errorMessage.value =
        `이미지와 첨부파일은 합해서 최대 ${props.maxFileCount}개까지 선택할 수 있습니다.`
    return
  }

  // 선택한 파일 전체를 검사한 뒤 미리보기를 만든다.
  for (const file of files) {
    const dotIndex = file.name.lastIndexOf('.')
    const extension = dotIndex < 0
        ? ''
        : file.name.slice(dotIndex).toLowerCase()

    if (!imageExtensions.has(extension)) {
      errorMessage.value = 'JPG, JPEG, PNG, GIF 이미지만 선택할 수 있습니다.'
      return
    }

    if (file.size > props.maxFileSizeBytes) {
      const maximumMb = props.maxFileSizeBytes / (1024 * 1024)
      errorMessage.value = `이미지 하나의 최대 용량은 ${maximumMb}MB입니다.`
      return
    }
  }

  const additions = files.map((file): PostEditorImage => {
    const previewUrl = URL.createObjectURL(file)
    createdUrls.add(previewUrl)

    return {
      kind: 'new',
      key: crypto.randomUUID(),
      file,
      name: file.name,
      sizeBytes: file.size,
      previewUrl,
    }
  })

  draftImages.value = [...draftImages.value, ...additions]
}

function removeImage(key: string) {
  const target = draftImages.value.find(image => image.key === key)
  if (!target) return

  // 기존에 적용한 이미지의 URL은 부모 폼이 관리한다.
  if (createdUrls.delete(target.previewUrl)) {
    URL.revokeObjectURL(target.previewUrl)
  }

  draftImages.value = draftImages.value.filter(image => image.key !== key)
  errorMessage.value = ''
}

function startDrag(event: DragEvent, key: string) {
  draggingKey.value = key

  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', key)
  }
}

function dropImage(targetKey: string) {
  const sourceIndex = draftImages.value.findIndex(
      image => image.key === draggingKey.value,
  )
  const targetIndex = draftImages.value.findIndex(
      image => image.key === targetKey,
  )
  const image = draftImages.value[sourceIndex]

  if (!image || targetIndex < 0 || sourceIndex === targetIndex) return

  draftImages.value.splice(sourceIndex, 1)
  draftImages.value.splice(targetIndex, 0, image)
  draggingKey.value = null
}

function moveImage(index: number, direction: number) {
  const targetIndex = index + direction
  const image = draftImages.value[index]

  if (!image || targetIndex < 0 || targetIndex >= draftImages.value.length) return

  draftImages.value.splice(index, 1)
  draftImages.value.splice(targetIndex, 0, image)
}

function apply() {
  const keptUrls = new Set(draftImages.value.map(image => image.previewUrl))

  // 적용할 URL의 관리 책임은 부모 폼으로 넘긴다.
  releaseCreatedUrls(keptUrls)
  emit('apply', [...draftImages.value])
}

function cancel() {
  releaseCreatedUrls()
  emit('cancel')
}
</script>

<template>
  <dialog
      ref="dialog"
      aria-labelledby="post-image-modal-title"
      class="m-auto max-h-[85dvh] w-[90vw] max-w-3xl overflow-y-auto rounded-lg border border-zinc-200 bg-white p-0 text-zinc-900 shadow-xl backdrop:bg-zinc-950/40"
      @cancel.prevent="cancel"
  >
    <header class="border-b border-zinc-200 bg-zinc-50 px-5 py-4">
      <h2 id="post-image-modal-title" class="m-0 text-xl font-semibold">
        이미지 추가
      </h2>
    </header>

    <section class="grid gap-4 p-5">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <label
            v-if="allowAdd"
            class="cursor-pointer rounded border border-zinc-200 bg-white px-3 py-2 text-sm font-semibold hover:bg-accent"
        >
          + 이미지 추가
          <input
              type="file"
              multiple
              accept=".jpg,.jpeg,.png,.gif"
              class="sr-only"
              @change="selectImages"
          >
        </label>

        <p class="m-0 text-sm text-zinc-600">
          이미지 {{ draftImages.length }}개
          · 전체 파일 {{ draftImages.length + otherFileCount }}개
        </p>
      </div>

      <p class="m-0 text-sm text-zinc-600">
        이미지를 드래그해 순서를 바꾸세요.
        처음 추가한 이미지는 본문 상단에 배치됩니다.
        재정렬하면 텍스트를 유지하고 이미지 순서만 변경합니다.
      </p>

      <p
          v-if="errorMessage"
          role="alert"
          class="m-0 rounded border border-red-200 bg-rose-50 p-3 text-sm font-medium text-red-800"
      >
        {{ errorMessage }}
      </p>

      <ul class="m-0 grid list-none grid-cols-2 gap-3 p-0 sm:grid-cols-3">
        <li
            v-for="(image, index) in draftImages"
            :key="image.key"
            draggable="true"
            class="min-w-0 cursor-grab rounded border border-zinc-200 bg-zinc-50 p-2 active:cursor-grabbing"
            :class="{ 'opacity-40': draggingKey === image.key }"
            @dragstart="startDrag($event, image.key)"
            @dragover.prevent
            @drop.prevent="dropImage(image.key)"
            @dragend="draggingKey = null"
        >
          <img
              :src="image.previewUrl"
              :alt="image.name"
              :draggable="false"
              class="aspect-square w-full rounded object-contain"
          >

          <p class="my-2 truncate text-sm font-medium" :title="image.name">
            {{ index + 1 }}. {{ image.name }}
          </p>

          <div class="flex flex-wrap gap-1">
            <button
                type="button"
                :disabled="index === 0"
                :aria-label="`${image.name} 앞으로 이동`"
                class="rounded border border-zinc-200 bg-white px-2 py-1 text-xs font-medium disabled:opacity-30"
                @click="moveImage(index, -1)"
            >
              ←
            </button>
            <button
                type="button"
                :disabled="index === draftImages.length - 1"
                :aria-label="`${image.name} 뒤로 이동`"
                class="rounded border border-zinc-200 bg-white px-2 py-1 text-xs font-medium disabled:opacity-30"
                @click="moveImage(index, 1)"
            >
              →
            </button>
            <button
                type="button"
                :aria-label="`${image.name} 삭제`"
                class="ml-auto rounded border border-zinc-200 bg-rose-50 px-2 py-1 text-xs font-medium"
                @click="removeImage(image.key)"
            >
              삭제
            </button>
          </div>
        </li>
      </ul>

      <p
          v-if="draftImages.length === 0"
          class="m-0 py-10 text-center text-sm text-zinc-500"
      >
        선택한 이미지가 없습니다.
      </p>
    </section>

    <footer class="flex justify-end gap-2 border-t border-zinc-200 bg-zinc-50 p-4">
      <button
          type="button"
          class="rounded border border-zinc-200 bg-white px-4 py-2 text-sm font-semibold"
          @click="cancel"
      >
        취소
      </button>
      <button
          type="button"
          class="rounded border border-zinc-200 bg-primary px-4 py-2 text-sm font-semibold text-white"
          @click="apply"
      >
        적용
      </button>
    </footer>
  </dialog>
</template>
