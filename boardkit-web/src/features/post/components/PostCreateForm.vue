<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'

import { createPost } from '@/api/postApi'
import { ApiError } from '@/api/http'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCheckbox from '@/components/ui/BaseCheckbox.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import type { JSONContent } from '@tiptap/core'
import PostContentEditor from './PostContentEditor.vue'
import { buildPostUpload, getBodyImageKeys } from '@/features/post/editor/postDocument'
import type { BoardDetailResponse } from '@/features/board/types/board'
import PostImageModal from './PostImageModal.vue'
import PostAttachmentPicker from './PostAttachmentPicker.vue'
import type { PostEditorImage, SelectedPostAttachment } from '@/features/post/types/post'

const props = defineProps<{
  board: BoardDetailResponse
}>()

const form = reactive({
  title: '',
  categoryId: '',
  isSecret: props.board.policy.secretMode === 'REQUIRED',
  secretPassword: '',
  commentsLocked: false,
})

const images = ref<PostEditorImage[]>([])
const attachments = ref<SelectedPostAttachment[]>([])
const imageModalOpen = ref(false)

const contentDocument = ref<JSONContent>({
  type: 'doc',
  content: [{ type: 'paragraph' }],
})

const contentEditor = ref<InstanceType<typeof PostContentEditor> | null>(null)

const bodyImages = computed(() => {
  const imageByKey = new Map(images.value.map(image => [image.key, image]))

  return getBodyImageKeys(contentDocument.value)
      .map(key => imageByKey.get(key))
      .filter((image): image is PostEditorImage => image !== undefined)
})

const submitting = ref(false)
const errorMessage = ref('')
const createdPostId = ref<number | null>(null)

const categoryOptions = computed(() => [
  {
    value: '',
    label: props.board.policy.categoryRequired
        ? '카테고리를 선택하세요'
        : '선택 안 함',
  },
  ...props.board.categories.map((category) => ({
    value: String(category.id),
    label: category.name,
  })),
])

function applyImages(nextImages: PostEditorImage[]) {
  const registered = new Map(images.value.map(image => [image.key, image]))

  for (const image of nextImages) {
    registered.set(image.key, image)
  }

  // 실행 취소로 복구할 수 있도록 적용했던 파일과 URL을 보관한다.
  images.value = [...registered.values()]

  const applied = contentEditor.value?.applyImages(nextImages) ?? false
  imageModalOpen.value = false

  if (!applied) {
    errorMessage.value = '이미지를 본문에 적용하지 못했습니다. 다시 시도해 주세요.'
  }
}

onBeforeUnmount(() => {
  const urls = new Set(images.value.map(image => image.previewUrl))

  for (const url of urls) {
    URL.revokeObjectURL(url)
  }
})

async function submit() {
  if (submitting.value) return

  errorMessage.value = ''
  createdPostId.value = null

  try {
    if (!form.title.trim()) {
      throw new Error('제목을 입력하세요.')
    }
    if (props.board.policy.categoryRequired && !form.categoryId) {
      throw new Error('카테고리를 선택하세요.')
    }

    const upload = buildPostUpload(contentDocument.value, images.value, attachments.value,)

    if (!upload.hasText && upload.imageCount === 0) {
      throw new Error('본문에 내용이나 이미지를 넣어 주세요.')
    }
    if (props.board.policy.imageRequired && upload.imageCount === 0) {
      throw new Error('본문에 이미지를 한 개 이상 넣어 주세요.')
    }
    if (upload.files.length > 0 && !props.board.policy.attachmentsEnabled) {
      throw new Error('첨부파일을 사용할 수 없는 게시판입니다.')
    }
    if (upload.files.length > props.board.policy.maxFileCount) {
      throw new Error(`이미지와 첨부파일은 합해서 최대 ${props.board.policy.maxFileCount}개까지 등록할 수 있습니다.`,)
    }

    submitting.value = true

    const response = await createPost(
        props.board.slug,
        {
          title: form.title.trim(),
          contentDocument: upload.contentDocument,
          categoryId: form.categoryId ? Number(form.categoryId) : null,
          isSecret: form.isSecret,
          secretPassword:
              form.isSecret && form.secretPassword.trim()
                  ? form.secretPassword
                  : null,
          commentsLocked:
              props.board.policy.commentsEnabled && form.commentsLocked,
        },
        upload.files,
    )

    createdPostId.value = response.id
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      errorMessage.value = '로그인이 필요합니다.'
    } else if (error instanceof ApiError && error.status === 403) {
      errorMessage.value = '게시글 작성 권한이 없습니다.'
    } else {
      errorMessage.value = error instanceof Error ? error.message : '게시글 작성에 실패했습니다.'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form class="grid min-w-0 gap-6 rounded-lg border border-slate-200 bg-white p-5 sm:p-6" @submit.prevent="submit">
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
      <div v-if="board.policy.attachmentsEnabled" class="flex flex-wrap items-start gap-3">
        <button
            type="button"
            :disabled="submitting"
            class="rounded border border-slate-200 bg-white px-3 py-2 text-sm font-semibold hover:bg-blue-50 disabled:opacity-50"
            @click="imageModalOpen = true"
        >
          이미지
        </button>

        <PostAttachmentPicker
            v-model="attachments"
            :upload-profile="board.policy.uploadProfile"
            :other-file-count="bodyImages.length"
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
    </section>

    <section
        v-if="board.policy.secretMode !== 'OFF'"
        class="grid gap-4 rounded-md bg-slate-50 p-4"
    >
      <BaseCheckbox
          v-if="board.policy.secretMode === 'OPTIONAL'"
          v-model="form.isSecret"
          label="비밀글"
          description="선택하면 비밀글로 작성됩니다."
      />

      <p
          v-else
          class="rounded-md bg-blue-50 p-3 text-sm text-slate-600"
      >
        이 게시판의 모든 글은 비밀글로 작성됩니다.
      </p>

      <BaseInput
          v-if="form.isSecret"
          id="secret-password"
          v-model="form.secretPassword"
          label="비밀글 비밀번호"
          type="password"
          hint="입력하지 않으면 별도 비밀번호 없이 열람할 수 있습니다."
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
        class="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700"
        role="alert"
    >
      {{ errorMessage }}
    </p>

    <p
        v-if="createdPostId"
        class="rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-700"
    >
      게시글이 등록되었습니다. 게시글 번호: {{ createdPostId }}
    </p>

    <div class="flex justify-end border-t border-slate-200 pt-5">
      <BaseButton
          type="submit"
          :disabled="submitting"
      >
        {{ submitting ? '등록 중...' : '게시글 등록' }}
      </BaseButton>
    </div>
  </form>

  <PostImageModal
      :open="imageModalOpen"
      :images="bodyImages"
      :other-file-count="attachments.length"
      :max-file-count="board.policy.maxFileCount"
      :max-file-size-bytes="board.policy.maxFileSizeBytes"
      :allow-add="board.policy.attachmentsEnabled"
      @apply="applyImages"
      @cancel="imageModalOpen = false"
  />

</template>
