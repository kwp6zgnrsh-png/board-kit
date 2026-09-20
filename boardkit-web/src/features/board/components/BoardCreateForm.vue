<script setup lang="ts">
import { reactive, ref } from 'vue'

import { ApiError, createBoard } from '@/api/boardApi'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCheckbox from '@/components/ui/BaseCheckbox.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import BaseTextarea from '@/components/ui/BaseTextarea.vue'
import type {
  BoardCreateRequest,
  BoardCreateResponse,
  BoardDisplayType,
  ReadPermission,
  RepresentativeImageMode,
  SecretMode,
  UploadProfile,
  WritePermission,
} from '@/features/board/types/board'

interface CategoryForm {
  key: number
  name: string
  sortOrder: number
}

const displayTypeOptions = [
  { value: 'LIST', label: '목록형' },
  { value: 'CARD', label: '카드형' },
]

const readPermissionOptions = [
  { value: 'PUBLIC', label: '전체 공개' },
  { value: 'MEMBER', label: '회원만' },
  { value: 'ADMIN', label: '관리자만' },
]

const writePermissionOptions = [
  { value: 'MEMBER', label: '회원' },
  { value: 'ADMIN', label: '관리자' },
]

const secretModeOptions = [
  { value: 'OFF', label: '사용 안 함' },
  { value: 'OPTIONAL', label: '작성자가 선택' },
  { value: 'REQUIRED', label: '모든 글에 필수' },
]

const uploadProfileOptions = [
  { value: 'IMAGE', label: '이미지만' },
  { value: 'IMAGE_AND_ZIP', label: '이미지와 ZIP' },
]

const representativeImageOptions = [
  { value: 'FIRST_IMAGE', label: '첫 이미지 자동 선택' },
  { value: 'MANUAL', label: '작성자가 직접 선택' },
]

const form = reactive({
  name: '',
  slug: '',
  description: '',
  displayType: 'LIST' as BoardDisplayType,
  sortOrder: 0,
  readPermission: 'PUBLIC' as ReadPermission,
  writePermission: 'MEMBER' as WritePermission,
  commentPermission: 'MEMBER' as WritePermission,
  secretMode: 'OFF' as SecretMode,
  categoriesEnabled: false,
  categoryRequired: false,
  attachmentsEnabled: false,
  uploadProfile: 'IMAGE' as UploadProfile,
  maxFileCount: 5,
  maxFileSizeMb: 10,
  imageRequired: false,
  thumbnailEnabled: false,
  representativeImageMode: 'FIRST_IMAGE' as RepresentativeImageMode,
  commentsEnabled: true,
  commentRepliesEnabled: true,
  postLikesEnabled: true,
  commentLikesEnabled: true,
  officialReplyEnabled: false,
  categories: [] as CategoryForm[],
})

const submitting = ref(false)
const errorMessage = ref('')
const createdBoard = ref<BoardCreateResponse | null>(null)
let nextCategoryKey = 1

function addCategory() {
  form.categories.push({
    key: nextCategoryKey++,
    name: '',
    sortOrder: form.categories.length,
  })
}

function removeCategory(key: number) {
  form.categories = form.categories.filter((category) => category.key !== key)
}

function buildRequest(): BoardCreateRequest {
  const attachmentsEnabled = form.attachmentsEnabled
  const commentsEnabled = form.commentsEnabled

  return {
    name: form.name.trim(),
    slug: form.slug.trim(),
    description: form.description.trim(),
    displayType: form.displayType,
    sortOrder: form.sortOrder,
    policy: {
      readPermission: form.readPermission,
      writePermission: form.writePermission,
      commentPermission: form.commentPermission,
      secretMode: form.secretMode,
      categoriesEnabled: form.categoriesEnabled,
      categoryRequired: form.categoriesEnabled && form.categoryRequired,
      attachmentsEnabled,
      uploadProfile: form.uploadProfile,
      maxFileCount: attachmentsEnabled ? form.maxFileCount : 0,
      maxFileSizeBytes: attachmentsEnabled
        ? Math.round(form.maxFileSizeMb * 1024 * 1024)
        : 0,
      imageRequired: attachmentsEnabled && form.imageRequired,
      thumbnailEnabled: attachmentsEnabled && form.thumbnailEnabled,
      representativeImageMode: form.representativeImageMode,
      commentsEnabled,
      commentRepliesEnabled: commentsEnabled && form.commentRepliesEnabled,
      postLikesEnabled: form.postLikesEnabled,
      commentLikesEnabled: commentsEnabled && form.commentLikesEnabled,
      officialReplyEnabled: form.officialReplyEnabled,
    },
    categories: form.categoriesEnabled
      ? form.categories.map(({ name, sortOrder }) => ({
          name: name.trim(),
          sortOrder,
        }))
      : [],
  }
}

async function submit() {
  errorMessage.value = ''
  createdBoard.value = null
  submitting.value = true

  try {
    createdBoard.value = await createBoard(buildRequest())
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      errorMessage.value = '관리자 로그인이 필요합니다.'
    } else if (error instanceof ApiError && error.status === 403) {
      errorMessage.value = '게시판 생성 권한이 없습니다.'
    } else {
      errorMessage.value =
        error instanceof Error ? error.message : '게시판 생성에 실패했습니다.'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form class="board-form" @submit.prevent="submit">
    <section class="form-section">
      <div class="section-heading">
        <div>
          <span class="section-number">01</span>
          <h2>기본 정보</h2>
        </div>
        <p>게시판 이름과 화면 표시 방식을 설정합니다.</p>
      </div>

      <div class="field-grid field-grid--two">
        <BaseInput
          id="board-name"
          v-model="form.name"
          label="게시판 이름"
          placeholder="예: 자유게시판"
          required
        />
        <BaseInput
          id="board-slug"
          v-model="form.slug"
          label="URL 주소"
          placeholder="예: free"
          hint="영문 소문자, 숫자, 하이픈만 사용할 수 있습니다."
          required
        />
        <BaseSelect
          id="display-type"
          v-model="form.displayType"
          label="목록 표시 방식"
          :options="displayTypeOptions"
        />
        <BaseInput
          id="sort-order"
          v-model="form.sortOrder"
          label="메뉴 정렬 순서"
          type="number"
          :min="0"
          required
        />
      </div>

      <BaseTextarea
        id="board-description"
        v-model="form.description"
        label="게시판 설명"
        placeholder="게시판의 목적과 이용 방법을 입력하세요."
      />
    </section>

    <section class="form-section">
      <div class="section-heading">
        <div>
          <span class="section-number">02</span>
          <h2>접근 권한</h2>
        </div>
        <p>조회, 게시글 작성, 댓글 작성 권한을 지정합니다.</p>
      </div>

      <div class="field-grid field-grid--two">
        <BaseSelect
          id="read-permission"
          v-model="form.readPermission"
          label="읽기 권한"
          :options="readPermissionOptions"
        />
        <BaseSelect
          id="write-permission"
          v-model="form.writePermission"
          label="게시글 작성 권한"
          :options="writePermissionOptions"
        />
        <BaseSelect
          id="comment-permission"
          v-model="form.commentPermission"
          label="댓글 작성 권한"
          :options="writePermissionOptions"
        />
        <BaseSelect
          id="secret-mode"
          v-model="form.secretMode"
          label="비밀글"
          :options="secretModeOptions"
        />
      </div>
    </section>

    <section class="form-section">
      <div class="section-heading section-heading--with-switch">
        <div>
          <span class="section-number">03</span>
          <h2>카테고리</h2>
        </div>
        <BaseCheckbox v-model="form.categoriesEnabled" label="카테고리 사용" />
      </div>

      <template v-if="form.categoriesEnabled">
        <BaseCheckbox
          v-model="form.categoryRequired"
          label="카테고리 선택 필수"
          description="게시글 작성 시 카테고리를 반드시 선택하게 합니다."
        />

        <div class="category-list">
          <div
            v-for="category in form.categories"
            :key="category.key"
            class="category-row"
          >
            <BaseInput
              :id="`category-name-${category.key}`"
              v-model="category.name"
              label="카테고리 이름"
              placeholder="예: 질문"
              required
            />
            <BaseInput
              :id="`category-order-${category.key}`"
              v-model="category.sortOrder"
              label="정렬 순서"
              type="number"
              :min="0"
              required
            />
            <BaseButton variant="danger" @click="removeCategory(category.key)">
              삭제
            </BaseButton>
          </div>
        </div>

        <BaseButton variant="secondary" @click="addCategory">카테고리 추가</BaseButton>
      </template>
    </section>

    <section class="form-section">
      <div class="section-heading section-heading--with-switch">
        <div>
          <span class="section-number">04</span>
          <h2>첨부파일과 이미지</h2>
        </div>
        <BaseCheckbox v-model="form.attachmentsEnabled" label="첨부파일 사용" />
      </div>

      <div v-if="form.attachmentsEnabled" class="field-grid field-grid--two">
        <BaseSelect
          id="upload-profile"
          v-model="form.uploadProfile"
          label="허용 형식 묶음"
          :options="uploadProfileOptions"
        />
        <BaseInput
          id="max-file-count"
          v-model="form.maxFileCount"
          label="최대 파일 개수"
          type="number"
          :min="1"
          required
        />
        <BaseInput
          id="max-file-size"
          v-model="form.maxFileSizeMb"
          label="파일당 최대 용량(MB)"
          type="number"
          :min="1"
          required
        />
        <BaseSelect
          id="representative-image"
          v-model="form.representativeImageMode"
          label="대표 이미지 선택"
          :options="representativeImageOptions"
        />
      </div>

      <div v-if="form.attachmentsEnabled" class="toggle-grid">
        <BaseCheckbox v-model="form.imageRequired" label="이미지 첨부 필수" />
        <BaseCheckbox v-model="form.thumbnailEnabled" label="썸네일 생성" />
      </div>
    </section>

    <section class="form-section">
      <div class="section-heading">
        <div>
          <span class="section-number">05</span>
          <h2>상호작용 기능</h2>
        </div>
        <p>댓글과 좋아요, 관리자 공식 답변 사용 여부를 선택합니다.</p>
      </div>

      <div class="toggle-grid">
        <BaseCheckbox v-model="form.commentsEnabled" label="댓글" />
        <BaseCheckbox
          v-model="form.commentRepliesEnabled"
          label="대댓글"
          :disabled="!form.commentsEnabled"
        />
        <BaseCheckbox v-model="form.postLikesEnabled" label="게시글 좋아요" />
        <BaseCheckbox
          v-model="form.commentLikesEnabled"
          label="댓글 좋아요"
          :disabled="!form.commentsEnabled"
        />
        <BaseCheckbox v-model="form.officialReplyEnabled" label="관리자 공식 답변" />
      </div>
    </section>

    <p v-if="errorMessage" class="message message--error" role="alert">
      {{ errorMessage }}
    </p>

    <p v-if="createdBoard" class="message message--success" role="status">
      {{ createdBoard.name }}이 생성되었습니다. 주소: /boards/{{ createdBoard.slug }}
    </p>

    <div class="form-actions">
      <BaseButton type="submit" :disabled="submitting">
        {{ submitting ? '생성 중...' : '게시판 생성' }}
      </BaseButton>
    </div>
  </form>
</template>

<style scoped>
.board-form {
  display: grid;
  gap: 1.25rem;
}

.form-section {
  display: grid;
  gap: 1.25rem;
  padding: 1.5rem;
  border: 1px solid #e2e8f0;
  border-radius: 1rem;
  background: #fff;
  box-shadow: 0 8px 30px rgb(15 23 42 / 4%);
}

.section-heading {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 1rem;
  border-bottom: 1px solid #e2e8f0;
}

.section-heading > div {
  display: flex;
  gap: 0.65rem;
  align-items: center;
}

.section-heading h2,
.section-heading p {
  margin: 0;
}

.section-heading h2 {
  color: #0f172a;
  font-size: 1.05rem;
}

.section-heading p {
  color: #64748b;
  font-size: 0.875rem;
}

.section-number {
  color: #2563eb;
  font-size: 0.75rem;
  font-weight: 800;
}

.field-grid,
.toggle-grid {
  display: grid;
  gap: 1rem;
}

.field-grid--two,
.toggle-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.category-list {
  display: grid;
  gap: 0.75rem;
}

.category-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 8rem auto;
  gap: 0.75rem;
  align-items: end;
  padding: 1rem;
  border-radius: 0.75rem;
  background: #f8fafc;
}

.message {
  margin: 0;
  padding: 1rem;
  border-radius: 0.75rem;
  font-weight: 700;
}

.message--error {
  color: #991b1b;
  background: #fee2e2;
}

.message--success {
  color: #166534;
  background: #dcfce7;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 720px) {
  .section-heading,
  .section-heading--with-switch {
    display: grid;
  }

  .field-grid--two,
  .toggle-grid,
  .category-row {
    grid-template-columns: 1fr;
  }
}
</style>
