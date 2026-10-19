<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useRoute} from 'vue-router'

import {getBoardDetail} from '@/api/boardApi'
import {ApiError} from '@/api/http'
import {getPostDetail} from '@/api/postApi'
import BoardHeader from '@/features/board/components/BoardHeader.vue'
import type {BoardDetailResponse} from '@/features/board/types/board'
import {getAuthSession} from '@/features/auth/services/authSession'
import PostEditForm from '@/features/post/components/PostEditForm.vue'
import type {PostDetailResponse} from '@/features/post/types/post'

const route = useRoute()
const board = ref<BoardDetailResponse | null>(null)
const post = ref<PostDetailResponse | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const authSession = computed(() => getAuthSession())

const boardSlug = computed(() => String(route.params.boardSlug ?? ''))
const postId = computed(() => {
  const id = Number(route.params.postId)
  return Number.isSafeInteger(id) && id > 0 ? id : null
})

let requestSequence = 0

async function loadPage(slug: string, id: number | null) {
  const currentRequest = ++requestSequence
  board.value = null
  post.value = null
  errorMessage.value = ''

  if (id === null) {
    errorMessage.value = '올바르지 않은 게시글 번호입니다.'
    return
  }

  loading.value = true

  try {
    const boardResponse = await getBoardDetail(slug)
    if (currentRequest !== requestSequence) return

    if (boardResponse.status !== 'ACTIVE') {
      errorMessage.value = '보관된 게시판의 게시글은 수정할 수 없습니다.'
      return
    }

    const session = getAuthSession()
    if (!session) {
      errorMessage.value = '로그인이 필요합니다.'
      return
    }

    if (boardResponse.policy.writePermission === 'ADMIN' && session.role !== 'ADMIN') {
      errorMessage.value = '게시글 수정 권한이 없습니다.'
      return
    }

    const postResponse = await getPostDetail(slug, id)
    if (currentRequest !== requestSequence) return

    if (postResponse.authorId !== session.memberId) {
      errorMessage.value = '작성자만 게시글을 수정할 수 있습니다.'
      return
    }

    if (postResponse.blinded) {
      errorMessage.value = '블라인드된 게시글은 수정할 수 없습니다.'
      return
    }

    board.value = boardResponse
    post.value = postResponse
  } catch (error) {
    if (currentRequest !== requestSequence) return

    if (error instanceof ApiError && error.status === 404) {
      errorMessage.value = '게시판 또는 게시글을 찾을 수 없습니다.'
    } else if (error instanceof ApiError && error.status === 403) {
      errorMessage.value = error.code === 'CONTENT_BLINDED'
          ? '블라인드된 게시글은 수정할 수 없습니다.'
          : '게시글 수정 권한이 없습니다.'
    } else {
      errorMessage.value =
          error instanceof Error ? error.message : '게시글을 불러오지 못했습니다.'
    }
  } finally {
    if (currentRequest === requestSequence) loading.value = false
  }
}

watch(
    [
      boardSlug,
      postId,
      () => authSession.value?.memberId ?? null,
      () => authSession.value?.role ?? null,
    ],
    ([slug, id]) => {
      void loadPage(slug, id)
    },
    { immediate: true, flush: 'sync' },
)
</script>

<template>
  <div class="grid min-w-0 gap-5">
    <p v-if="loading" class="m-0 rounded-lg border border-slate-200 bg-slate-100 p-5 font-medium text-slate-700">게시글을
      불러오는 중입니다.</p>
    <p v-else-if="errorMessage" class="m-0 rounded-lg border border-red-200 bg-rose-50 p-5 font-medium text-red-900"
       role="alert">{{ errorMessage }}</p>

    <template v-else-if="board && post">
      <BoardHeader
          :name="`${board.name} 글 수정`"
          :description="board.description"
          class="pb-1"
      />
      <PostEditForm
          :key="`${post.id}-${post.version}`"
          :board="board"
          :post="post"
      />
    </template>
  </div>
</template>
