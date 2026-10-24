<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {generateHTML} from '@tiptap/core'
import type {JSONContent} from '@tiptap/core'
import type {DOMOutputSpec} from '@tiptap/pm/model'
import {getPostFileBlob} from '@/api/postApi'
import {getBodyImageNodes} from '@/features/post/editor/postDocument'
import {
  createPostEditorExtensions,
  POST_DOCUMENT_CLASS,
} from '@/features/post/editor/postEditor'
import {PostImage} from '@/features/post/editor/postImage'
import type {PostFileResponse} from '@/features/post/types/post'

interface DisplayImage {
  file: PostFileResponse
  url: string | null
  failed: boolean
}

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

// 상세 화면에서는 이미지 로딩 상태도 본문의 원래 위치에 표시한다.
const DisplayImageExtension = PostImage.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      imageStatus: {
        default: 'loading',
        rendered: false,
      },
    }
  },

  renderHTML({node}): DOMOutputSpec {
    const status = node.attrs.imageStatus
    const src = node.attrs.src

    if (
        status === 'ready' &&
        typeof src === 'string' &&
        src.startsWith('blob:')
    ) {
      return [
        'img',
        {
          src,
          alt: node.attrs.alt,
          draggable: 'false',
          decoding: 'async',
          class: 'mx-auto my-3 block h-auto max-w-full rounded',
        },
      ]
    }

    const message = status === 'loading'
        ? '이미지를 불러오는 중입니다.'
        : status === 'missing'
            ? '본문 이미지 정보를 확인할 수 없습니다.'
            : '이미지를 불러오지 못했습니다.'

    return [
      'div',
      {
        role: status === 'loading' ? 'status' : 'alert',
        class:
            'my-3 rounded border p-4 text-sm ' +
            (status === 'loading'
                ? 'border-zinc-200 bg-zinc-50 text-zinc-500'
                : 'border-red-200 bg-red-50 text-red-700'),
      },
      message,
    ]
  },
})

const displayExtensions = createPostEditorExtensions().map(extension =>
    extension.name === 'image' ? DisplayImageExtension : extension,
)

const sourceDocument = computed<JSONContent>(() => {
  if (props.contentDocument) {
    return props.contentDocument
  }

  const oldImages = props.files
      .filter(file => file.fileUsage === 'BODY_IMAGE')
      .sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id)

  return {
    type: 'doc',
    content: [
      ...oldImages.map(file => ({
        type: 'image',
        attrs: {fileId: file.id},
      })),
      ...props.content.split(/\r?\n/).map(text => ({
        type: 'paragraph',
        content: text ? [{type: 'text', text}] : [],
      })),
    ],
  }
})

const imagesById = computed(() =>
    new Map(images.value.map(image => [image.file.id, image])),
)

const renderedBody = computed(() => {
  function prepareNode(node: JSONContent): JSONContent {
    if (node.type === 'image') {
      const image = imagesById.value.get(Number(node.attrs?.fileId))

      return {
        type: 'image',
        attrs: {
          src: image?.url ?? null,
          alt: image?.file.originalName ?? '본문 이미지',
          imageStatus: !image
              ? 'missing'
              : image.failed
                  ? 'failed'
                  : image.url
                      ? 'ready'
                      : 'loading',
        },
      }
    }

    const copied: JSONContent = {...node}
    if (node.content) {
      copied.content = node.content.map(prepareNode)
    }

    return copied
  }

  try {
    return {
      html: generateHTML(
          prepareNode(sourceDocument.value),
          displayExtensions,
      ),
      error: '',
    }
  } catch {
    return {
      html: '',
      error: '본문을 표시하지 못했습니다.',
    }
  }
})

const downloadFiles = computed(() =>
    props.files
        .filter(file => file.fileUsage === 'ATTACHMENT')
        .sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id),
)

function revokeImageUrls() {
  for (const url of imageUrls) {
    URL.revokeObjectURL(url)
  }
  imageUrls.clear()
}

async function loadImages() {
  const currentRequest = ++requestSequence
  const slug = props.boardSlug
  const postId = props.postId
  const secretPassword = props.secretPassword

  revokeImageUrls()
  downloadingIds.value = []
  downloadErrorMessage.value = ''

  let referencedIds: Set<number>

  try {
    referencedIds = new Set(
        getBodyImageNodes(sourceDocument.value)
            .map(node => Number(node.attrs?.fileId)),
    )
  } catch {
    images.value = []
    return
  }

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
            return {file, url: null, failed: false}
          }

          const url = URL.createObjectURL(blob)
          imageUrls.add(url)
          return {file, url, failed: false}
        } catch {
          return {file, url: null, failed: true}
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
        {secretPassword: props.secretPassword},
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
      window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    }
  } catch (error) {
    if (currentRequest === requestSequence) {
      downloadErrorMessage.value = error instanceof Error
          ? error.message
          : '첨부파일을 내려받지 못했습니다.'
    }
  } finally {
    if (currentRequest === requestSequence) {
      downloadingIds.value = downloadingIds.value.filter(id => id !== file.id)
    }
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
      sourceDocument,
    ],
    loadImages,
    {immediate: true, deep: true},
)

onBeforeUnmount(() => {
  requestSequence++
  revokeImageUrls()
})
</script>

<template>
  <div class="min-h-36 min-w-0 py-5 text-zinc-800 max-sm:min-h-24">
    <p
        v-if="renderedBody.error"
        role="alert"
        class="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700"
    >
      {{ renderedBody.error }}
    </p>

    <div
        v-else
        :class="POST_DOCUMENT_CLASS"
        v-html="renderedBody.html"
    />

    <section
        v-if="downloadFiles.length"
        class="mt-6 grid gap-3 border-t border-zinc-200 pt-4"
    >
      <h2 class="m-0 text-base font-semibold text-zinc-900">
        첨부파일 {{ downloadFiles.length }}개
      </h2>

      <ul class="m-0 grid list-none gap-2 p-0">
        <li
            v-for="file in downloadFiles"
            :key="file.id"
            class="flex flex-wrap items-center justify-between gap-3 rounded border border-zinc-200 bg-zinc-50 p-3"
        >
          <div class="min-w-0">
            <p class="m-0 truncate font-medium text-zinc-900">
              {{ file.originalName }}
            </p>
            <p class="m-0 text-xs text-zinc-600">
              {{ formatFileSize(file.sizeBytes) }}
            </p>
          </div>

          <button
              type="button"
              :disabled="downloadingIds.includes(file.id)"
              class="rounded border border-zinc-200 bg-white px-3 py-2 text-sm font-semibold disabled:opacity-50"
              @click="downloadFile(file)"
          >
            {{ downloadingIds.includes(file.id) ? '받는 중...' : '다운로드' }}
          </button>
        </li>
      </ul>

      <p
          v-if="downloadErrorMessage"
          role="alert"
          class="m-0 rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700"
      >
        {{ downloadErrorMessage }}
      </p>
    </section>
  </div>
</template>