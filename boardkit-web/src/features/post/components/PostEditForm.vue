<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { JSONContent } from '@tiptap/core'

import { ApiError } from '@/api/http'
import { getPostFileBlob, updatePost } from '@/api/postApi'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCheckbox from '@/components/ui/BaseCheckbox.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import type { BoardDetailResponse } from '@/features/board/types/board'
import type {
  PostDetailResponse,
  PostEditorImage,
  SelectedPostAttachment,
} from '@/features/post/types/post'
import { buildPostUpload, getBodyImageKeys } from '@/features/post/editor/postDocument'
import PostContentEditor from './PostContentEditor.vue'
import PostImageModal from './PostImageModal.vue'
import PostAttachmentPicker from './PostAttachmentPicker.vue'

const props = defineProps<{
  board: BoardDetailResponse
  post: PostDetailResponse
}>()

const router = useRouter()
const route = useRoute()

const form = reactive({
  title: props.post.title,
  categoryId: props.post.categoryId === null ? '' : String(props.post.categoryId),
  isSecret: props.post.secret || props.board.policy.secretMode === 'REQUIRED',
  secretPassword: '',
  clearSecretPassword: false,
  commentsLocked: props.post.commentsLocked,
})

const contentDocument = ref<JSONContent>({
  type: 'doc',
  content: [{ type: 'paragraph' }],
})
const contentEditor = ref<InstanceType<typeof PostContentEditor> | null>(null)
const images = ref<PostEditorImage[]>([])
const attachments = ref<SelectedPostAttachment[]>([])
const imageModalOpen = ref(false)
const loadingImages = ref(true)
const editorReady = ref(false)
const submitting = ref(false)
const errorMessage = ref('')

const existingAttachments = props.post.files
    .filter(file => file.fileUsage === 'ATTACHMENT')
    .sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id)

const removedAttachmentIds = ref<number[]>([])
const retainedAttachments = computed(() =>
    existingAttachments.filter(file => !removedAttachmentIds.value.includes(file.id)),
)

const bodyImages = computed(() => {
  const imageByKey = new Map(images.value.map(image => [image.key, image]))
  return getBodyImageKeys(contentDocument.value)
      .map(key => imageByKey.get(key))
      .filter((image): image is PostEditorImage => image !== undefined)
})

const categoryOptions = computed(() => [
  {
    value: '',
    label: props.board.policy.categoryRequired
        ? '카테고리를 선택하세요'
        : '선택 안 함',
  },
  ...props.board.categories.map(category => ({
    value: String(category.id),
    label: category.name,
  })),
])

const objectUrls = new Set<string>()
let disposed = false

async function loadDocument() {
  loadingImages.value = true
  editorReady.value = false
  errorMessage.value = ''
  const loadedUrls: string[] = []

  try {
    const bodyFiles = props.post.files
        .filter(file => file.fileUsage === 'BODY_IMAGE')
        .sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id)

    // JSON 본문이 없는 기존 게시글은 이미지 상단 + 기존 텍스트로 복원한다.
    const document: JSONContent = props.post.contentDocument
        ? JSON.parse(JSON.stringify(props.post.contentDocument))
        : {
          type: 'doc',
          content: [
            ...bodyFiles.map(file => ({
              type: 'image',
              attrs: { fileId: file.id },
            })),
            ...props.post.content.split(/\r?\n/).map(text => ({
              type: 'paragraph',
              content: text ? [{ type: 'text', text }] : [],
            })),
          ],
        }

    const fileById = new Map(bodyFiles.map(file => [file.id, file]))
    const loadedImages: PostEditorImage[] = []

    for (const block of document.content ?? []) {
      if (block.type !== 'image') continue

      const fileId = Number(block.attrs?.fileId)
      const file = fileById.get(fileId)
      if (!file || !file.contentType.startsWith('image/')) {
        throw new Error('본문 이미지 정보를 확인할 수 없습니다.')
      }

      const blob = await getPostFileBlob(props.board.slug, props.post.id, file.id)
      if (disposed) return

      const previewUrl = URL.createObjectURL(blob)
      loadedUrls.push(previewUrl)
      objectUrls.add(previewUrl)

      const image: PostEditorImage = {
        kind: 'existing',
        key: `existing:${file.id}`,
        fileId: file.id,
        name: file.originalName,
        sizeBytes: file.sizeBytes,
        previewUrl,
      }

      loadedImages.push(image)
      block.attrs = {
        imageKey: image.key,
        src: image.previewUrl,
        alt: image.name,
      }
    }

    if (disposed) return
    images.value = loadedImages
    contentDocument.value = document
    editorReady.value = true
  } catch (error) {
    for (const url of loadedUrls) {
      URL.revokeObjectURL(url)
      objectUrls.delete(url)
    }

    if (!disposed) {
      errorMessage.value = error instanceof Error
          ? error.message
          : '본문 이미지를 불러오지 못했습니다.'
    }
  } finally {
    if (!disposed) loadingImages.value = false
  }
}

onMounted(loadDocument)

onBeforeUnmount(() => {
  disposed = true
  for (const url of objectUrls) URL.revokeObjectURL(url)
  objectUrls.clear()
})

function applyImages(nextImages: PostEditorImage[]) {
  const registered = new Map(images.value.map(image => [image.key, image]))

  for (const image of nextImages) {
    registered.set(image.key, image)
    objectUrls.add(image.previewUrl)
  }

  // 본문에서 삭제해도 실행 취소로 복구할 수 있도록 보관한다.
  images.value = [...registered.values()]
  const applied = contentEditor.value?.applyImages(nextImages) ?? false
  imageModalOpen.value = false

  if (!applied) {
    errorMessage.value = '이미지를 본문에 적용하지 못했습니다. 다시 시도해 주세요.'
  }
}

function toggleExistingAttachment(fileId: number) {
  if (submitting.value) return
  removedAttachmentIds.value = removedAttachmentIds.value.includes(fileId)
      ? removedAttachmentIds.value.filter(id => id !== fileId)
      : [...removedAttachmentIds.value, fileId]
}

function formatFileSize(sizeBytes: number): string {
  return sizeBytes < 1024 * 1024
      ? `${(sizeBytes / 1024).toFixed(1)} KB`
      : `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`
}

async function submit() {
  if (submitting.value || !editorReady.value || imageModalOpen.value) return
  errorMessage.value = ''

  try {
    if (!form.title.trim()) throw new Error('제목을 입력하세요.')
    if (props.board.policy.categoryRequired && !form.categoryId) {
      throw new Error('카테고리를 선택하세요.')
    }

    const newSecretPassword = form.isSecret && form.secretPassword.trim()
        ? form.secretPassword
        : null
    const clearSecretPassword = form.isSecret && form.clearSecretPassword

    if (clearSecretPassword && newSecretPassword) {
      throw new Error('비밀번호 변경과 삭제를 동시에 선택할 수 없습니다.')
    }

    const upload = buildPostUpload(
        contentDocument.value,
        images.value,
        attachments.value,
        retainedAttachments.value.map(file => file.id),
    )

    if (!upload.hasText && upload.imageCount === 0) {
      throw new Error('본문에 내용이나 이미지를 넣어 주세요.')
    }
    if (props.board.policy.imageRequired && upload.imageCount === 0) {
      throw new Error('본문에 이미지를 한 개 이상 넣어 주세요.')
    }
    if (upload.files.length > 0 && !props.board.policy.attachmentsEnabled) {
      throw new Error('새 파일을 첨부할 수 없는 게시판입니다.')
    }

    const totalCount = upload.retainedFileIds.length + upload.files.length
    if (upload.files.length > 0 && totalCount > props.board.policy.maxFileCount) {
      throw new Error(`이미지와 첨부파일은 합해서 최대 ${props.board.policy.maxFileCount}개까지 등록할 수 있습니다.`)
    }

    submitting.value = true
    await updatePost(
        props.board.slug,
        props.post.id,
        {
          title: form.title.trim(),
          contentDocument: upload.contentDocument,
          categoryId: form.categoryId ? Number(form.categoryId) : null,
          isSecret: form.isSecret,
          secretPassword: newSecretPassword,
          clearSecretPassword,
          commentsLocked: props.board.policy.commentsEnabled && form.commentsLocked,
          version: props.post.version,
          retainedFileIds: upload.retainedFileIds,
        },
        upload.files,
    )

    await router.replace({
      name: 'post-detail',
      params: { boardSlug: props.board.slug, postId: props.post.id },
      query: route.query,
    })
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      errorMessage.value = '게시글이 변경되었습니다. 상세 화면을 다시 열고 수정해 주세요.'
    } else if (error instanceof ApiError && error.status === 401) {
      errorMessage.value = '로그인이 필요합니다.'
    } else if (error instanceof ApiError && error.status === 403) {
      errorMessage.value = '게시글 수정 권한이 없습니다.'
    } else {
      errorMessage.value = error instanceof Error
          ? error.message
          : '게시글 수정에 실패했습니다.'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form
      class="grid min-w-0 gap-6 rounded-lg border border-slate-200 bg-white p-5 sm:p-8"
      @submit.prevent="submit"
  >
    <BaseInput
        id="post-title"
        v-model="form.title"
        label="제목"
        placeholder="게시글 제목을 입력하세요."
        required
    />

    <BaseSelect
        v-if="board.policy.categoriesEnabled"
        id="post-category"
        v-model="form.categoryId"
        label="카테고리"
        :options="categoryOptions"
    />

    <section class="grid gap-4 rounded border border-slate-200 bg-white p-4">
      <p v-if="loadingImages" class="m-0 text-sm font-medium text-slate-600">
        본문 이미지를 불러오는 중입니다.
      </p>

      <button
          v-else-if="!editorReady"
          type="button"
          class="justify-self-start rounded border border-slate-200 bg-white px-3 py-2 text-sm font-semibold"
          @click="loadDocument"
      >
        본문 다시 불러오기
      </button>

      <template v-else>
        <div class="flex flex-wrap items-start gap-3">
          <button
              v-if="board.policy.attachmentsEnabled || bodyImages.length > 0"
              type="button"
              :disabled="submitting"
              class="rounded border border-slate-200 bg-white px-3 py-2 text-sm font-semibold hover:bg-blue-50 disabled:opacity-50"
              @click="imageModalOpen = true"
          >
            이미지
          </button>

          <PostAttachmentPicker
              v-if="board.policy.attachmentsEnabled"
              v-model="attachments"
              :upload-profile="board.policy.uploadProfile"
              :other-file-count="bodyImages.length + retainedAttachments.length"
              :max-file-count="board.policy.maxFileCount"
              :max-file-size-bytes="board.policy.maxFileSizeBytes"
              :disabled="submitting"
              @error="errorMessage = $event"
          />
        </div>

        <PostContentEditor
            ref="contentEditor"
            v-model="contentDocument"
            :disabled="submitting"
        />
      </template>
    </section>

    <section
        v-if="existingAttachments.length"
        class="grid gap-3 rounded border border-slate-200 bg-slate-50 p-4"
    >
      <h3 class="m-0 font-semibold text-slate-900">기존 다운로드 첨부파일</h3>

      <ul class="m-0 grid list-none gap-2 p-0">
        <li
            v-for="file in existingAttachments"
            :key="file.id"
            class="flex flex-wrap items-center justify-between gap-3 rounded border border-slate-200 bg-white p-3"
            :class="{ 'opacity-50': removedAttachmentIds.includes(file.id) }"
        >
          <div class="min-w-0">
            <p class="m-0 truncate font-semibold">{{ file.originalName }}</p>
            <p class="m-0 text-sm text-slate-600">
              {{ formatFileSize(file.sizeBytes) }}
            </p>
          </div>

          <button
              type="button"
              :disabled="submitting || !editorReady"
              class="rounded border border-slate-200 px-3 py-1 text-sm font-semibold disabled:opacity-50"
              :class="removedAttachmentIds.includes(file.id) ? 'bg-emerald-100' : 'bg-rose-100'"
              @click="toggleExistingAttachment(file.id)"
          >
            {{ removedAttachmentIds.includes(file.id) ? '삭제 취소' : '삭제' }}
          </button>
        </li>
      </ul>
    </section>

    <section
        v-if="board.policy.secretMode !== 'OFF' || post.secret"
        class="grid gap-4 rounded border border-slate-200 bg-slate-50 p-4 sm:p-5"
    >
      <BaseCheckbox
          v-if="board.policy.secretMode === 'OPTIONAL'"
          v-model="form.isSecret"
          label="비밀글"
          description="선택하면 비밀글로 작성됩니다."
      />

      <p
          v-else-if="board.policy.secretMode === 'REQUIRED'"
          class="m-0 rounded border border-slate-200 bg-blue-50 px-4 py-3 font-medium text-slate-700"
      >
        이 게시판의 모든 글은 비밀글로 작성됩니다.
      </p>
      <p v-else class="m-0 rounded border border-slate-200 bg-blue-50 px-4 py-3 font-medium text-slate-700">
        새 비밀글은 만들 수 없지만 이 글의 비밀 설정은 유지됩니다.
      </p>

      <BaseInput
          v-if="form.isSecret"
          id="secret-password"
          v-model="form.secretPassword"
          label="비밀글 비밀번호"
          type="password"
          hint="비워두면 기존 비밀번호를 유지합니다."
      />

      <BaseCheckbox
          v-if="post.secret && form.isSecret"
          v-model="form.clearSecretPassword"
          label="기존 비밀번호 제거"
          description="제거하면 비밀번호 없이 이 비밀글을 열람할 수 있습니다."
      />
    </section>

    <BaseCheckbox
        v-if="board.policy.commentsEnabled"
        v-model="form.commentsLocked"
        label="댓글 작성 잠금"
        description="이 게시글에 새 댓글을 작성할 수 없게 합니다."
    />

    <p
        v-if="errorMessage"
        class="m-0 rounded border border-slate-200 bg-rose-50 px-4 py-3 font-medium text-red-900"
        role="alert"
    >
      {{ errorMessage }}
    </p>

    <div class="flex justify-end border-t border-slate-200 pt-4">
      <BaseButton
          type="submit"
          :disabled="submitting || !editorReady"
      >
        {{ submitting ? '수정 중...' : '게시글 수정' }}
      </BaseButton>
    </div>
  </form>

  <PostImageModal
      v-if="editorReady"
      :open="imageModalOpen"
      :images="bodyImages"
      :other-file-count="retainedAttachments.length + attachments.length"
      :max-file-count="board.policy.maxFileCount"
      :max-file-size-bytes="board.policy.maxFileSizeBytes"
      :allow-add="board.policy.attachmentsEnabled && !submitting"
      @apply="applyImages"
      @cancel="imageModalOpen = false"
  />
</template>
