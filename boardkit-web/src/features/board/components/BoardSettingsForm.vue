<script setup lang="ts">
import { computed, reactive, ref } from 'vue'

import {createBoard, updateBoard} from '@/api/boardApi'
import { ApiError } from '@/api/http'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCheckbox from '@/components/ui/BaseCheckbox.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import BaseTextarea from '@/components/ui/BaseTextarea.vue'
import type {
  BoardAdminDetailResponse,
  BoardCreateRequest,
  BoardDisplayType,
  BoardPolicyRequest,
  BoardUpdateRequest,
  ReadPermission,
  SecretMode,
  UploadProfile,
  WritePermission,
} from '@/features/board/types/board'

const props = defineProps<{
  board?: BoardAdminDetailResponse
}>()

const emit = defineEmits<{
  updated: []
}>()

interface CategoryForm {
  key: number
  id: number | null
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
  { value: 'IMAGE_AND_FILE', label: '이미지·TXT·ZIP·PDF' },
]

const editing = computed(
    () => props.board !== undefined,
)

const initialBoard = props.board

const currentVersion = ref(initialBoard?.version ?? 0)

const form = reactive({
  name: initialBoard?.name ?? '',
  slug: initialBoard?.slug ?? '',
  description: initialBoard?.description ?? '',
  displayType:
      initialBoard?.displayType ??
      ('LIST' as BoardDisplayType),
  sortOrder: initialBoard?.sortOrder ?? 0,

  readPermission:
      initialBoard?.policy.readPermission ??
      ('PUBLIC' as ReadPermission),
  writePermission:
      initialBoard?.policy.writePermission ??
      ('MEMBER' as WritePermission),
  commentPermission:
      initialBoard?.policy.commentPermission ??
      ('MEMBER' as WritePermission),
  secretMode:
      initialBoard?.policy.secretMode ??
      ('OFF' as SecretMode),

  categoriesEnabled:
      initialBoard?.policy.categoriesEnabled ?? false,
  categoryRequired:
      initialBoard?.policy.categoryRequired ?? false,

  attachmentsEnabled:
      initialBoard?.policy.attachmentsEnabled ?? false,
  uploadProfile:
      initialBoard?.policy.uploadProfile ??
      ('IMAGE' as UploadProfile),
  maxFileCount:
      initialBoard && initialBoard.policy.maxFileCount > 0
          ? initialBoard.policy.maxFileCount
          : 5,
  maxFileSizeMb:
      initialBoard &&
      initialBoard.policy.maxFileSizeBytes > 0
          ? initialBoard.policy.maxFileSizeBytes /
          (1024 * 1024)
          : 10,
  imageRequired:
      initialBoard?.policy.imageRequired ?? false,
  thumbnailEnabled:
      initialBoard?.policy.thumbnailEnabled ?? false,
  commentsEnabled:
      initialBoard?.policy.commentsEnabled ?? true,
  commentRepliesEnabled:
      initialBoard?.policy.commentRepliesEnabled ?? true,
  postLikesEnabled:
      initialBoard?.policy.postLikesEnabled ?? true,
  commentLikesEnabled:
      initialBoard?.policy.commentLikesEnabled ?? true,
  officialReplyEnabled:
      initialBoard?.policy.officialReplyEnabled ?? false,

  categories:
      initialBoard?.categories.map(
          (category, index): CategoryForm => ({
            key: index + 1,
            id: category.id,
            name: category.name,
            sortOrder: category.sortOrder,
          }),
      ) ?? [],
})

let nextCategoryKey = form.categories.length + 1

const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

function addCategory() {
  form.categories.push({
    key: nextCategoryKey++,
    id: null,
    name: '',
    sortOrder: form.categories.length,
  })
}

function removeCategory(key: number) {
  form.categories = form.categories.filter(
      (category) => category.key !== key,
  )
}

function buildPolicy(): BoardPolicyRequest {
  const attachmentsEnabled = form.attachmentsEnabled
  const commentsEnabled = form.commentsEnabled

  return {
    readPermission: form.readPermission,
    writePermission: form.writePermission,
    commentPermission: form.commentPermission,
    secretMode: form.secretMode,
    categoriesEnabled: form.categoriesEnabled,
    categoryRequired: form.categoriesEnabled && form.categoryRequired, attachmentsEnabled,
    uploadProfile: form.uploadProfile,
    maxFileCount:
        attachmentsEnabled
            ? form.maxFileCount
            : 0,
    maxFileSizeBytes:
        attachmentsEnabled
            ? Math.round(
                form.maxFileSizeMb * 1024 * 1024,
            )
            : 0,
    imageRequired: attachmentsEnabled && form.imageRequired,
    thumbnailEnabled: attachmentsEnabled && form.thumbnailEnabled,
    commentsEnabled,
    commentRepliesEnabled: commentsEnabled && form.commentRepliesEnabled,
    postLikesEnabled: form.postLikesEnabled,
    commentLikesEnabled: form.commentLikesEnabled,
    officialReplyEnabled: form.officialReplyEnabled,
  }
}

function buildCreateRequest(): BoardCreateRequest {
  return {
    name: form.name.trim(),
    slug: form.slug.trim(),
    description: form.description.trim(),
    displayType: form.displayType,
    sortOrder: form.sortOrder,
    policy: buildPolicy(),
    categories:
        form.categoriesEnabled
            ? form.categories.map((category) => ({
              name: category.name.trim(),
              sortOrder: category.sortOrder,
            }))
            : [],
  }
}

function buildUpdateRequest(
    board: BoardAdminDetailResponse,
): BoardUpdateRequest {
  return {
    name: form.name.trim(),
    description: form.description.trim(),
    displayType: form.displayType,
    sortOrder: form.sortOrder,
    policy: buildPolicy(),

    // 카테고리 기능을 꺼도 배열은 메모리에 유지한다.
    // 백엔드는 이 경우 카테고리를 변경하지 않는다.
    categories: form.categories.map((category) => ({
      id: category.id,
      name: category.name.trim(),
      sortOrder: category.sortOrder,
    })),

    version: currentVersion.value,
  }
}

async function submit() {
  errorMessage.value = ''
  successMessage.value = ''
  submitting.value = true

  try {
    if (props.board) {
      await updateBoard(props.board.slug, buildUpdateRequest(props.board))

      currentVersion.value++
      successMessage.value = '게시판 설정이 수정되었습니다.'
      emit('updated')
    } else {
      const response = await createBoard(buildCreateRequest())

      successMessage.value = `${response.name}이 생성되었습니다. 주소: /boards/${response.slug}`
    }
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      errorMessage.value = '관리자 로그인이 필요합니다.'
    } else if (error instanceof ApiError && error.status === 403) {
      errorMessage.value = '게시판 관리 권한이 없습니다.'
    } else if (error instanceof ApiError && error.status === 409) {
      errorMessage.value = '게시판 설정이 변경되었습니다. 새로고침 후 다시 시도해주세요.'
    } else {
      errorMessage.value = error instanceof Error
              ? error.message
              : `게시판 ${editing.value ? '수정' : '생성'}에 실패했습니다.`
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form class="grid min-w-0 gap-5" @submit.prevent="submit">
    <section class="grid gap-5 rounded-xl border border-border bg-card p-5 sm:p-6 shadow-xs">
      <div class="flex flex-col justify-between gap-2 border-b border-zinc-200 pb-4 sm:flex-row sm:items-center">
        <div class="flex items-center gap-2">
          <span class="text-xs font-semibold text-primary">01</span>
          <h2 class="text-base font-semibold text-zinc-900">기본 정보</h2>
        </div>
        <p class="text-xs leading-5 text-zinc-500">게시판 이름과 화면 표시 방식을 설정합니다.</p>
      </div>

      <div class="grid gap-5 sm:grid-cols-2">
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
          :hint="
            editing
            ? 'URL 주소는 게시판 생성 후 변경할 수 없습니다.'
            : '영문 소문자, 숫자, 하이픈만 사용할 수 있습니다.'
          "
          :disabled="editing"
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

    <section class="grid gap-5 rounded-xl border border-border bg-card p-5 sm:p-6 shadow-xs">
      <div class="flex flex-col justify-between gap-2 border-b border-zinc-200 pb-4 sm:flex-row sm:items-center">
        <div class="flex items-center gap-2">
          <span class="text-xs font-semibold text-primary">02</span>
          <h2 class="text-base font-semibold text-zinc-900">접근 권한</h2>
        </div>
        <p class="text-xs leading-5 text-zinc-500">조회, 게시글 작성, 댓글 작성 권한을 지정합니다.</p>
      </div>

      <div class="grid gap-5 sm:grid-cols-2">
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

    <section class="grid gap-5 rounded-xl border border-border bg-card p-5 sm:p-6 shadow-xs">
      <div class="flex flex-col justify-between gap-3 border-b border-zinc-200 pb-4 sm:flex-row sm:items-center">
        <div class="flex items-center gap-2">
          <span class="text-xs font-semibold text-primary">03</span>
          <h2 class="text-base font-semibold text-zinc-900">카테고리</h2>
        </div>
        <BaseCheckbox v-model="form.categoriesEnabled" label="카테고리 사용" />
      </div>

      <template v-if="form.categoriesEnabled">
        <BaseCheckbox
          v-model="form.categoryRequired"
          label="카테고리 선택 필수"
          description="게시글 작성 시 카테고리를 반드시 선택하게 합니다."
        />

        <div class="grid gap-3">
          <div
            v-for="category in form.categories"
            :key="category.key"
            class="grid items-end gap-3 rounded-md bg-zinc-50 p-4 sm:grid-cols-[minmax(0,1fr)_7rem_auto]"
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

    <section class="grid gap-5 rounded-xl border border-border bg-card p-5 sm:p-6 shadow-xs">
      <div class="flex flex-col justify-between gap-3 border-b border-zinc-200 pb-4 sm:flex-row sm:items-center">
        <div class="flex items-center gap-2">
          <span class="text-xs font-semibold text-primary">04</span>
          <h2 class="text-base font-semibold text-zinc-900">첨부파일과 이미지</h2>
        </div>
        <BaseCheckbox v-model="form.attachmentsEnabled" label="첨부파일 사용" />
      </div>

      <p
          v-if="form.attachmentsEnabled"
          class="m-0 rounded-lg border border-zinc-200 bg-zinc-50 px-4 py-3 text-sm font-semibold text-zinc-600"
      >
        대표 이미지는 게시글 본문에서 가장 먼저 배치된 이미지로 자동 지정됩니다.
      </p>

      <div v-if="form.attachmentsEnabled" class="grid gap-5 sm:grid-cols-2">
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
      </div>

      <div v-if="form.attachmentsEnabled" class="grid gap-3 sm:grid-cols-2">
        <BaseCheckbox v-model="form.imageRequired" label="이미지 첨부 필수" />
        <BaseCheckbox v-model="form.thumbnailEnabled" label="썸네일 생성" />
      </div>
    </section>

    <section class="grid gap-5 rounded-xl border border-border bg-card p-5 sm:p-6 shadow-xs">
      <div class="flex flex-col justify-between gap-2 border-b border-zinc-200 pb-4 sm:flex-row sm:items-center">
        <div class="flex items-center gap-2">
          <span class="text-xs font-semibold text-primary">05</span>
          <h2 class="text-base font-semibold text-zinc-900">상호작용 기능</h2>
        </div>
        <p class="text-xs leading-5 text-zinc-500">댓글과 좋아요, 관리자 공식 답변 사용 여부를 선택합니다.</p>
      </div>

      <div class="grid gap-3 sm:grid-cols-2">
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
        />
        <BaseCheckbox v-model="form.officialReplyEnabled" label="관리자 공식 답변" />
      </div>
    </section>

    <p v-if="errorMessage" class="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">
      {{ errorMessage }}
    </p>

    <p
        v-if="successMessage"
        class="rounded-md border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-700"
        role="status"
    >
      {{ successMessage }}
    </p>

    <div class="flex justify-end border-t border-zinc-200 pt-5">
      <BaseButton type="submit" :disabled="submitting">
        {{
          submitting
              ? editing
                  ? '수정 중...'
                  : '생성 중...'
              : editing
                  ? '설정 저장'
                  : '게시판 생성'
        }}
      </BaseButton>
    </div>
  </form>
</template>
