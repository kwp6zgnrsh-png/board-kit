<script setup lang="ts">
import { computed, ref } from 'vue'
import type { UploadProfile } from '@/features/board/types/board'
import type { SelectedPostAttachment } from '@/features/post/types/post'

const props = defineProps<{
  uploadProfile: UploadProfile
  otherFileCount: number
  maxFileCount: number
  maxFileSizeBytes: number
  disabled: boolean
}>()

const emit = defineEmits<{
  error: [message: string]
}>()

const attachments = defineModel<SelectedPostAttachment[]>({ required: true })
const fileInput = ref<HTMLInputElement | null>(null)

const profileExtensions: Record<UploadProfile, readonly string[]> = {
  IMAGE: ['.jpg', '.jpeg', '.png', '.gif'],
  IMAGE_AND_ZIP: ['.jpg', '.jpeg', '.png', '.gif', '.zip'],
  IMAGE_AND_FILE: ['.jpg', '.jpeg', '.png', '.gif', '.txt', '.zip', '.pdf'],
}

const allowedExtensions = computed(() => profileExtensions[props.uploadProfile])
const accept = computed(() => allowedExtensions.value.join(','))

function selectFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''

  if (props.disabled || files.length === 0) return

  const totalCount = props.otherFileCount + attachments.value.length + files.length
  if (totalCount > props.maxFileCount) {
    emit('error', `이미지와 첨부파일은 합해서 최대 ${props.maxFileCount}개까지 선택할 수 있습니다.`)
    return
  }

  for (const file of files) {
    const dotIndex = file.name.lastIndexOf('.')
    const extension = dotIndex < 0 ? '' : file.name.slice(dotIndex).toLowerCase()

    if (!allowedExtensions.value.includes(extension)) {
      emit('error', `이 게시판에서 허용하지 않는 파일 형식입니다: ${file.name}`)
      return
    }
    if (file.size === 0) {
      emit('error', `내용이 없는 파일은 첨부할 수 없습니다: ${file.name}`)
      return
    }
    if (file.size > props.maxFileSizeBytes) {
      const maximumMb = props.maxFileSizeBytes / (1024 * 1024)
      emit('error', `첨부파일 하나의 최대 용량은 ${maximumMb}MB입니다.`)
      return
    }
  }

  attachments.value = [
    ...attachments.value,
    ...files.map(file => ({ key: crypto.randomUUID(), file })),
  ]
  emit('error', '')
}

function removeFile(key: string) {
  if (props.disabled) return
  attachments.value = attachments.value.filter(item => item.key !== key)
}
</script>

<template>
  <div class="grid gap-2.5">
    <button
        type="button"
        :disabled="disabled"
        class="inline-flex h-11 items-center gap-2 justify-self-start rounded-md border border-zinc-300 bg-white px-3 text-sm font-medium text-zinc-800 hover:border-zinc-400 hover:bg-zinc-100 focus-visible:outline-2 focus-visible:outline-ring disabled:opacity-50 sm:px-4"
        @click="fileInput?.click()"
    >
      <svg class="size-5 text-zinc-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="m8 12 6-6a3 3 0 0 1 4 4l-8 8a5 5 0 0 1-7-7l8-8m-3 9 5-5a1 1 0 0 1 2 2l-6 6" />
      </svg>
      파일
    </button>

    <input
        ref="fileInput"
        type="file"
        :accept="accept"
        multiple
        class="hidden"
        :disabled="disabled"
        @change="selectFiles"
    >

    <ul v-if="attachments.length" class="m-0 grid list-none gap-2 p-0">
      <li
          v-for="item in attachments"
          :key="item.key"
          class="flex min-w-0 items-center justify-between gap-3 rounded-md border border-zinc-200 bg-white px-3 py-2"
      >
        <div class="min-w-0">
          <p class="m-0 truncate text-sm font-medium">{{ item.file.name }}</p>
          <p class="m-0 text-xs text-zinc-600">
            {{ (item.file.size / 1024).toFixed(1) }} KB
          </p>
        </div>

        <button
            type="button"
            :disabled="disabled"
            class="shrink-0 rounded border border-zinc-200 bg-rose-50 px-2 py-1 text-xs font-medium disabled:opacity-50"
            @click="removeFile(item.key)"
        >
          삭제
        </button>
      </li>
    </ul>
  </div>
</template>
