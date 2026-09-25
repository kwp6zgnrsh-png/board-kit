<script setup lang="ts">
import { computed, reactive, ref } from 'vue'

import { ApiError } from '@/api/http'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCheckbox from '@/components/ui/BaseCheckbox.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import BaseTextarea from '@/components/ui/BaseTextarea.vue'
import type { BoardDetailResponse } from '@/features/board/types/board'
import { useRoute, useRouter } from 'vue-router'
import { updatePost } from '@/api/postApi'
import type { PostDetailResponse } from '@/features/post/types/post'

const props = defineProps<{
  board: BoardDetailResponse
  post: PostDetailResponse
}>()

const router = useRouter()
const route = useRoute()

const form = reactive({
  title: props.post.title,
  content: props.post.content,
  categoryId: props.post.categoryId === null
      ? ''
      : String(props.post.categoryId),
  isSecret: props.post.secret ||
      props.board.policy.secretMode === 'REQUIRED',
  secretPassword: '',
  clearSecretPassword: false,
  commentsLocked: props.post.commentsLocked,
})

const submitting = ref(false)
const errorMessage = ref('')

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

const imageUploadRequired = computed(
    () => props.board.policy.imageRequired,
)

function validateForm(): boolean {
  if (!form.title.trim()) {
    errorMessage.value = '제목을 입력하세요.'
    return false
  }

  if (!form.content.trim()) {
    errorMessage.value = '본문을 입력하세요.'
    return false
  }

  if (props.board.policy.categoryRequired && !form.categoryId) {
    errorMessage.value = '카테고리를 선택하세요.'
    return false
  }

  if (imageUploadRequired.value) {
    errorMessage.value = '이 게시판은 이미지 첨부가 필요합니다. 파일 기능 구현 후 수정할 수 있습니다.'
    return false
  }

  return true
}
async function submit() {
  errorMessage.value = ''

  if (!validateForm() || submitting.value) {
    return
  }

  const newSecretPassword =
      form.isSecret && form.secretPassword.trim()
          ? form.secretPassword
          : null
  const clearSecretPassword =
      form.isSecret && form.clearSecretPassword

  if (clearSecretPassword && newSecretPassword) {
    errorMessage.value = '비밀번호 변경과 삭제를 동시에 선택할 수 없습니다.'
    return
  }

  submitting.value = true

  try {
    await updatePost(props.board.slug, props.post.id, {
      title: form.title.trim(),
      content: form.content.trim(),
      categoryId: form.categoryId
          ? Number(form.categoryId)
          : null,
      isSecret: form.isSecret,
      secretPassword: newSecretPassword,
      clearSecretPassword: clearSecretPassword,
      commentsLocked: props.board.policy.commentsEnabled &&
          form.commentsLocked,
      version: props.post.version,
    })

    await router.replace({
      name: 'post-detail',
      params: {
        boardSlug: props.board.slug,
        postId: props.post.id,
      },
      query: route.query,
    })
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      errorMessage.value = '게시글이 변경되었습니다. 상세 화면을 다시 열고 수정해주세요.'
    } else if (error instanceof ApiError && error.status === 403) {
      errorMessage.value = '게시글 수정 권한이 없습니다.'
    } else {
      errorMessage.value =
          error instanceof Error
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
      class="grid min-w-0 gap-6 rounded-lg border-[3px] border-slate-950 bg-white p-5 shadow-[2px_3px_0_#0f172a] sm:p-8 [&_.field\_\_label]:font-black! [&_.field\_\_label]:text-slate-950! [&_.field\_\_control]:rounded-sm! [&_.field\_\_control]:border-2! [&_.field\_\_control]:border-slate-950! [&_.field\_\_control:focus]:border-blue-600! [&_.checkbox]:rounded-sm! [&_.checkbox]:border-2! [&_.checkbox]:border-slate-950! [&_.checkbox_strong]:font-black!"
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

    <BaseTextarea
        id="post-content"
        v-model="form.content"
        label="본문"
        placeholder="게시글 내용을 입력하세요."
        :rows="12"
    />

    <section
        v-if="board.policy.secretMode !== 'OFF' || post.secret"
        class="grid gap-4 rounded border-2 border-slate-950 bg-slate-50 p-4 sm:p-5"
    >
      <BaseCheckbox
          v-if="board.policy.secretMode === 'OPTIONAL'"
          v-model="form.isSecret"
          label="비밀글"
          description="선택하면 비밀글로 작성됩니다."
      />

      <p
          v-else-if="board.policy.secretMode === 'REQUIRED'"
          class="m-0 rounded border-2 border-slate-950 bg-blue-100 px-4 py-3 font-bold text-slate-700"
      >
        이 게시판의 모든 글은 비밀글로 작성됩니다.
      </p>
      <p v-else class="m-0 rounded border-2 border-slate-950 bg-blue-100 px-4 py-3 font-bold text-slate-700">
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
        v-if="imageUploadRequired"
        class="m-0 rounded border-2 border-slate-950 bg-rose-100 px-4 py-3 font-bold text-red-900"
    >
      이 게시판은 이미지 첨부가 필수입니다. 파일 기능이 아직 구현되지 않아 글을 작성할 수 없습니다.
    </p>

    <p
        v-if="errorMessage"
        class="m-0 rounded border-2 border-slate-950 bg-rose-100 px-4 py-3 font-bold text-red-900"
        role="alert"
    >
      {{ errorMessage }}
    </p>

    <div class="flex justify-end border-t-2 border-slate-950 pt-4">
      <BaseButton
          type="submit"
          class="min-h-11 rounded! border-2! border-slate-950! bg-blue-500! px-4 text-sm! font-black! shadow-[2px_2px_0_#0f172a] transition hover:-translate-y-0.5 hover:bg-blue-600! max-sm:w-full"
          :disabled="submitting || imageUploadRequired"
      >
        {{ submitting ? '수정 중...' : '게시글 수정' }}
      </BaseButton>
    </div>
  </form>
</template>
