<script setup lang="ts">
import { computed } from 'vue'

import type { BoardDetailResponse } from '@/features/board/types/board'

const props = defineProps<{
  board: BoardDetailResponse
}>()

const readPermissionLabel = computed(() => ({
  PUBLIC: '누구나 읽을 수 있어요',
  MEMBER: '회원만 읽을 수 있어요',
  ADMIN: '관리자만 읽을 수 있어요',
}[props.board.policy.readPermission]))

const writePermissionLabel = computed(() =>
  props.board.policy.writePermission === 'ADMIN'
    ? '관리자만 글을 쓸 수 있어요'
    : '회원이라면 글을 쓸 수 있어요',
)

const secretModeLabel = computed(() => ({
  OFF: '비밀글을 사용하지 않아요',
  OPTIONAL: '비밀글을 선택할 수 있어요',
  REQUIRED: '모든 글이 비밀글이에요',
}[props.board.policy.secretMode]))
</script>

<template>
  <details class="mt-3 border-t border-slate-100 pt-4 text-xs text-slate-500">
    <summary class="w-fit cursor-pointer rounded py-1 font-medium hover:text-blue-600 focus-visible:outline-2 focus-visible:outline-blue-600">게시판 이용 안내</summary>
    <dl class="mt-3 grid gap-3 rounded-md bg-slate-50 p-4 sm:grid-cols-2">
      <div><dt class="mb-1 font-medium text-slate-700">읽기 권한</dt><dd>{{ readPermissionLabel }}</dd></div>
      <div><dt class="mb-1 font-medium text-slate-700">글쓰기 권한</dt><dd>{{ writePermissionLabel }}</dd></div>
      <div><dt class="mb-1 font-medium text-slate-700">비밀글</dt><dd>{{ secretModeLabel }}</dd></div>
      <div><dt class="mb-1 font-medium text-slate-700">댓글</dt><dd>{{ board.policy.commentsEnabled ? '댓글로 대화할 수 있어요' : '새 댓글을 작성할 수 없어요' }}</dd></div>
    </dl>
  </details>
</template>
