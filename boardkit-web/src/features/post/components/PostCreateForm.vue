<script setup lang="ts">
import { computed, reactive, ref } from 'vue'

import { createPost } from '@/api/postApi'
import { ApiError } from '@/api/http'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCheckbox from '@/components/ui/BaseCheckbox.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import BaseTextarea from '@/components/ui/BaseTextarea.vue'
import type { BoardDetailResponse } from '@/features/board/types/board'

const props = defineProps<{
  board: BoardDetailResponse
}>()

const form = reactive({
  title: '',
  content: '',
  categoryId: '',
  isSecret: props.board.policy.secretMode === 'REQUIRED',
  secretPassword: '',
  commentsLocked: false,
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

  if (
      props.board.policy.categoryRequired &&
      !form.categoryId
  ) {
    errorMessage.value = '카테고리를 선택하세요.'
    return false
  }

  if (imageUploadRequired.value) {
    errorMessage.value =
        '이 게시판은 이미지 첨부가 필요합니다. 파일 기능 구현 후 작성할 수 있습니다.'
    return false
  }

  return true
}

async function submit() {
  errorMessage.value = ''
  createdPostId.value = null

  if (!validateForm()) {
    return
  }

  submitting.value = true

  try {
    const response = await createPost(props.board.slug, {
      title: form.title.trim(),
      content: form.content.trim(),
      categoryId: form.categoryId
          ? Number(form.categoryId)
          : null,
      isSecret: form.isSecret,
      secretPassword:
          form.isSecret && form.secretPassword.trim()
              ? form.secretPassword
              : null,
      commentsLocked:
          props.board.policy.commentsEnabled &&
          form.commentsLocked,
    })

    createdPostId.value = response.id
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      errorMessage.value = '로그인이 필요합니다.'
    } else if (error instanceof ApiError && error.status === 403) {
      errorMessage.value = '게시글 작성 권한이 없습니다.'
    } else {
      errorMessage.value =
          error instanceof Error
              ? error.message
              : '게시글 작성에 실패했습니다.'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form class="post-form" @submit.prevent="submit">
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
        v-if="board.policy.secretMode !== 'OFF'"
        class="option-section"
    >
      <BaseCheckbox
          v-if="board.policy.secretMode === 'OPTIONAL'"
          v-model="form.isSecret"
          label="비밀글"
          description="선택하면 비밀글로 작성됩니다."
      />

      <p
          v-else
          class="policy-message"
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
        v-if="imageUploadRequired"
        class="policy-message policy-message--warning"
    >
      이 게시판은 이미지 첨부가 필수입니다. 파일 기능이 아직 구현되지 않아 글을 작성할 수 없습니다.
    </p>

    <p
        v-if="errorMessage"
        class="result-message result-message--error"
        role="alert"
    >
      {{ errorMessage }}
    </p>

    <p
        v-if="createdPostId"
        class="result-message result-message--success"
    >
      게시글이 등록되었습니다. 게시글 번호: {{ createdPostId }}
    </p>

    <div class="form-actions">
      <BaseButton
          type="submit"
          :disabled="submitting || imageUploadRequired"
      >
        {{ submitting ? '등록 중...' : '게시글 등록' }}
      </BaseButton>
    </div>
  </form>
</template>

<style scoped>
.post-form {
  display: grid;
  gap: 1.25rem;
  padding: 1.5rem;
  border: 1px solid #e2e8f0;
  border-radius: 1rem;
  background: #fff;
}

.option-section {
  display: grid;
  gap: 1rem;
}

.policy-message,
.result-message {
  margin: 0;
  padding: 0.85rem 1rem;
  border-radius: 0.7rem;
}

.policy-message {
  color: #475569;
  background: #f1f5f9;
}

.policy-message--warning,
.result-message--error {
  color: #991b1b;
  background: #fee2e2;
}

.result-message--success {
  color: #166534;
  background: #dcfce7;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
}
</style>