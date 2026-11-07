<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { archiveBoard, getAdminBoard } from '@/api/boardApi'
import { ApiError } from '@/api/http'
import BaseButton from '@/components/ui/BaseButton.vue'
import BoardSettingsForm from '@/features/board/components/BoardSettingsForm.vue'
import type { BoardAdminDetailResponse } from '@/features/board/types/board'

const route = useRoute()
const router = useRouter()

const board = ref<BoardAdminDetailResponse | null>(null)
const loading = ref(false)
const archiving = ref(false)
const errorMessage = ref('')
const archiveErrorMessage = ref('')

const boardSlug = computed(
    () => String(route.params.boardSlug ?? ''),
)

let requestSequence = 0

async function loadBoard(slug: string) {
  const currentRequest = ++requestSequence

  loading.value = true
  errorMessage.value = ''
  board.value = null

  try {
    const response = await getAdminBoard(slug)

    if (currentRequest === requestSequence) {
      board.value = response
    }
  } catch (error) {
    if (currentRequest !== requestSequence) return

    if (error instanceof ApiError && error.status === 404) {
      errorMessage.value = '게시판을 찾을 수 없습니다.'
    } else if (error instanceof ApiError && error.status === 403) {
      errorMessage.value = '게시판 관리 권한이 없습니다.'
    } else {
      errorMessage.value =
          error instanceof Error
              ? error.message
              : '게시판 설정을 불러오지 못했습니다.'
    }
  } finally {
    if (currentRequest === requestSequence) {
      loading.value = false
    }
  }
}

async function confirmArchive() {
  if (!board.value || archiving.value) return

  const confirmed = window.confirm(
      `"${board.value.name}" 게시판을 보관하시겠습니까?`,
  )

  if (!confirmed) return

  archiving.value = true
  archiveErrorMessage.value = ''

  try {
    await archiveBoard(board.value.slug)
    await router.replace({ name: 'home' })
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      archiveErrorMessage.value = '이미 보관되었거나 존재하지 않는 게시판입니다.'
    } else {
      archiveErrorMessage.value =
          error instanceof Error
              ? error.message
              : '게시판 보관에 실패했습니다.'
    }
  } finally {
    archiving.value = false
  }
}

watch(boardSlug, loadBoard, { immediate: true })
</script>

<template>
  <div class="grid gap-8">
    <header class="page-heading">
      <div>
        <p class="m-0 mb-2 text-xs font-semibold text-primary">
          게시판 관리
        </p>

        <h1 class="page-title">
          게시판 설정
        </h1>

        <p class="m-0 mt-3 text-sm font-medium text-zinc-600">
          게시판 정보와 운영 정책을 수정합니다.
        </p>
      </div>

      <BaseButton
          v-if="board"
          variant="danger"
          :disabled="archiving"
          @click="confirmArchive"
      >
        {{ archiving ? '보관 중...' : '게시판 보관' }}
      </BaseButton>
    </header>

    <p
        v-if="loading"
        class="m-0 rounded-none border border-zinc-200 bg-zinc-100 px-5 py-14 text-center font-semibold text-zinc-600"
    >
      게시판 설정을 불러오는 중입니다.
    </p>

    <p
        v-else-if="errorMessage"
        class="m-0 rounded-none border border-red-200 bg-red-50 px-5 py-12 text-center font-semibold text-red-900"
        role="alert"
    >
      {{ errorMessage }}
    </p>

    <template v-else-if="board">
      <p
          v-if="archiveErrorMessage"
          class="m-0 rounded-none border border-red-200 bg-red-50 px-5 py-4 text-center font-semibold text-red-900"
          role="alert"
      >
        {{ archiveErrorMessage }}
      </p>

      <BoardSettingsForm
          :key="board.id"
          :board="board"
      />
    </template>
  </div>
</template>
