<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { JSONContent } from '@tiptap/core'
import { getPostFileBlob } from '@/api/postApi'
import type { PostFileResponse } from '@/features/post/types/post'

interface DisplayImage {
  file: PostFileResponse
  url: string | null
  failed: boolean
}

type BodyBlock =
    | { type: 'paragraph'; text: string }
    | { type: 'image'; fileId: number }

const props = defineProps<{
  boardSlug: string
  postId: number
  content: string
  contentDocument: JSONContent | null
  files: PostFileResponse[]
  secretPassword?: string
}>()

const images = ref<DisplayImage[]>([])
const downloadingIds = ref<number[]>([])
const downloadErrorMessage = ref('')
const imageUrls = new Set<string>()
let requestSequence = 0

const bodyBlocks = computed<BodyBlock[]>(() => {
  if (!props.contentDocument) {
    const oldImages = props.files
        .filter(file => file.fileUsage === 'BODY_IMAGE')
        .sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id)

    return [
      ...oldImages.map((file): BodyBlock => ({
        type: 'image',
        fileId: file.id,
      })),
      { type: 'paragraph', text: props.content },
    ]
  }

  return (props.contentDocument.content ?? []).flatMap<BodyBlock>(block => {
    if (block.type === 'paragraph') {
      const text = (block.content ?? [])
          .map(inline => inline.type === 'hardBreak' ? '\n' : inline.text ?? '')
          .join('')

      return [{ type: 'paragraph', text }]
    }
    if (block.type === 'image') {
      return [{ type: 'image', fileId: Number(block.attrs?.fileId) }]
    }
    return []
  })
})

const imagesById = computed(() =>
    new Map(images.value.map(image => [image.file.id, image])),
)

const downloadFiles = computed(() =>
    props.files
        .filter(file => file.fileUsage === 'ATTACHMENT')
        .sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id),
)

function revokeImageUrls() {
  for (const url of imageUrls) URL.revokeObjectURL(url)
  imageUrls.clear()
}

async function loadImages() {
  const currentRequest = ++requestSequence
  const slug = props.boardSlug
  const postId = props.postId
  const secretPassword = props.secretPassword

  revokeImageUrls()

  const referencedIds = new Set(
      bodyBlocks.value.flatMap(block =>
          block.type === 'image' ? [block.fileId] : [],
      ),
  )
  const imageFiles = props.files.filter(file =>
      referencedIds.has(file.id) &&
      file.fileUsage === 'BODY_IMAGE' &&
      file.contentType.startsWith('image/'),
  )

  images.value = imageFiles.map(file => ({
    file,
    url: null,
    failed: false,
  }))

  const loadedImages = await Promise.all(
      imageFiles.map(async (file): Promise<DisplayImage> => {
        try {
          const blob = await getPostFileBlob(slug, postId, file.id, {
            secretPassword,
          })

          if (currentRequest !== requestSequence) {
            return { file, url: null, failed: false }
          }

          const url = URL.createObjectURL(blob)
          imageUrls.add(url)
          return { file, url, failed: false }
        } catch {
          return { file, url: null, failed: true }
        }
      }),
  )

  if (currentRequest === requestSequence) {
    images.value = loadedImages
  }
}

async function downloadFile(file: PostFileResponse) {
  if (downloadingIds.value.includes(file.id)) return

  const currentRequest = requestSequence
  downloadingIds.value = [...downloadingIds.value, file.id]
  downloadErrorMessage.value = ''

  try {
    const blob = await getPostFileBlob(
        props.boardSlug,
        props.postId,
        file.id,
        { secretPassword: props.secretPassword },
    )

    if (currentRequest !== requestSequence) return

    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = file.originalName

    try {
      document.body.appendChild(link)
      link.click()
    } finally {
      link.remove()
      // 브라우저가 다운로드를 시작할 시간을 준 뒤 해제한다.
      window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    }
  } catch (error) {
    if (currentRequest === requestSequence) {
      downloadErrorMessage.value = error instanceof Error
          ? error.message
          : '첨부파일을 내려받지 못했습니다.'
    }
  } finally {
    downloadingIds.value = downloadingIds.value.filter(id => id !== file.id)
  }
}

function formatFileSize(sizeBytes: number): string {
  return sizeBytes < 1024 * 1024
      ? `${(sizeBytes / 1024).toFixed(1)} KB`
      : `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`
}

watch(
    [
      () => props.boardSlug,
      () => props.postId,
      () => props.secretPassword,
      () => props.files,
      () => props.contentDocument,
    ],
    loadImages,
    { immediate: true, deep: true },
)

onBeforeUnmount(() => {
  requestSequence++
  revokeImageUrls()
})
</script>
<template>
  <div class="grid min-h-36 gap-0 py-5 text-slate-800 max-sm:min-h-24">
    <div class="grid min-w-0 gap-3">
      <template v-for="(block, index) in bodyBlocks" :key="index">
        <p
            v-if="block.type === 'paragraph'"
            class="m-0 min-h-8 leading-8 whitespace-pre-wrap [overflow-wrap:anywhere]"
        >
          {{ block.text }}
        </p>

        <figure
            v-else
            class="m-0 overflow-hidden rounded border border-slate-200 bg-slate-100"
        >
          <p
              v-if="!imagesById.has(block.fileId)"
              class="m-0 p-4 font-medium text-red-800"
              role="alert"
          >
            본문 이미지 정보를 확인할 수 없습니다.
          </p>

          <img
              v-else-if="imagesById.get(block.fileId)?.url"
              :src="imagesById.get(block.fileId)?.url ?? undefined"
              :alt="imagesById.get(block.fileId)?.file.originalName ?? '본문 이미지'"
              class="mx-auto block h-auto max-w-full"
          >

          <p
              v-else-if="imagesById.get(block.fileId)?.failed"
              class="m-0 p-4 font-medium text-red-800"
              role="alert"
          >
            이미지를 불러오지 못했습니다.
          </p>

          <p v-else class="m-0 p-4 font-medium text-slate-500">
            이미지를 불러오는 중입니다.
          </p>
        </figure>
      </template>
    </div>

    <section
        v-if="downloadFiles.length"
        class="mt-6 grid gap-3 border-t border-slate-200 pt-4"
    >
      <h2 class="m-0 text-base font-semibold text-slate-900">
        첨부파일 {{ downloadFiles.length }}개
      </h2>

      <ul class="m-0 grid list-none gap-2 p-0">
        <li
            v-for="file in downloadFiles"
            :key="file.id"
            class="flex flex-wrap items-center justify-between gap-3 rounded border border-slate-200 bg-slate-50 p-3"
        >
          <div class="min-w-0">
            <p class="m-0 truncate font-medium text-slate-900">
              {{ file.originalName }}
            </p>
            <p class="m-0 text-xs text-slate-600">
              {{ formatFileSize(file.sizeBytes) }}
            </p>
          </div>

          <button
              type="button"
              :disabled="downloadingIds.includes(file.id)"
              class="rounded border border-slate-200 bg-white px-3 py-2 text-sm font-semibold disabled:opacity-50"
              @click="downloadFile(file)"
          >
            {{ downloadingIds.includes(file.id) ? '받는 중...' : '다운로드' }}
          </button>
        </li>
      </ul>

      <p
          v-if="downloadErrorMessage"
          class="m-0 rounded border border-red-200 bg-rose-50 p-3 font-medium text-red-900"
          role="alert"
      >
        {{ downloadErrorMessage }}
      </p>
    </section>
  </div>
</template>
