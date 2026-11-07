<script setup lang="ts">
import {onMounted, ref} from 'vue'

import {getBoards} from '@/api/boardApi'
import {ApiError} from '@/api/http'
import type {BoardSummaryResponse} from '@/features/board/types/board'

const boards = ref<BoardSummaryResponse[]>([])
const loading = ref(true)
const errorMessage = ref('')

onMounted(async () => {
  try {
    boards.value = await getBoards()
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) {
      errorMessage.value = '관리자 권한이 필요합니다.'
    } else {
      errorMessage.value =
          error instanceof Error
              ? error.message
              : '게시판 목록을 불러오지 못했습니다.'
    }
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="grid gap-8">
    <header
        class="page-heading"
    >
      <div>
        <p class="m-0 text-xs font-medium text-primary">
          관리자
        </p>

        <h1 class="page-title">
          게시판 관리
        </h1>

        <p class="m-0 mt-2 text-sm font-medium text-zinc-700">
          게시판과 커뮤니티 운영 기능을 관리합니다.
        </p>
      </div>

      <RouterLink
          :to="{ name: 'admin-board-create' }"
          class="inline-flex items-center rounded-none border border-zinc-200 bg-primary px-4 py-2.5 text-sm font-semibold text-white no-underline transition hover:bg-primary"
      >
        + 게시판 생성
      </RouterLink>
    </header>

    <section
        class="overflow-hidden surface-panel"
    >
      <header
          class="border-b border-zinc-200 px-5 py-4"
      >
        <h2 class="m-0 text-base font-medium text-zinc-900">
          게시판 설정
        </h2>
        <p class="m-0 mt-1 text-sm font-medium text-zinc-700">
          게시판 정보와 운영 정책을 변경하거나 게시판을 보관합니다.
        </p>
      </header>

      <p
          v-if="loading"
          class="m-0 px-5 py-12 text-center font-medium text-zinc-500"
      >
        게시판 목록을 불러오는 중입니다.
      </p>

      <p
          v-else-if="errorMessage"
          class="m-4 rounded-none border border-red-200 bg-red-50 px-4 py-6 text-center font-semibold text-red-900"
          role="alert"
      >
        {{ errorMessage }}
      </p>

      <div
          v-else-if="boards.length > 0"
          class="divide-y divide-zinc-200"
      >
        <article
            v-for="board in boards"
            :key="board.id"
            class="flex flex-wrap items-center justify-between gap-4 px-5 py-4"
        >
          <div class="min-w-0">
            <h3 class="m-0 truncate text-base font-semibold text-zinc-900">
              {{ board.name }}
            </h3>
            <p class="m-0 mt-1 truncate text-sm font-medium text-zinc-500">
              /boards/{{ board.slug }}
            </p>
          </div>

          <RouterLink
              :to="{
              name: 'admin-board-edit',
              params: { boardSlug: board.slug },
            }"
              class="inline-flex shrink-0 items-center rounded-none border border-zinc-200 bg-white px-3 py-2 text-xs font-semibold text-zinc-900 no-underline transition hover:bg-zinc-50"
          >
            설정
          </RouterLink>
        </article>
      </div>

      <p
          v-else
          class="m-0 px-5 py-12 text-center font-medium text-zinc-500"
      >
        생성된 게시판이 없습니다.
      </p>
    </section>

    <section class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <RouterLink
          :to="{name: 'admin-post-replies'}"
          class="block surface-panel p-5 no-underline transition hover:border-zinc-300 hover:bg-accent"
      >
        <h2 class="text-lg font-medium text-zinc-900">
          관리자 공식 답변
        </h2>
        <p class="mt-3 text-sm leading-6 text-zinc-600">
          미답변 게시글을 확인하고 공식 답변을 관리합니다.
        </p>
        <span class="mt-4 inline-block text-xs font-medium text-primary">관리하기 →</span>
      </RouterLink>
      <RouterLink
          :to="{ name: 'admin-reports' }"
          class="block surface-panel p-5 no-underline transition hover:border-zinc-300 hover:bg-accent"
      >
        <h2 class="text-lg font-medium text-zinc-900">
          신고 처리
        </h2>
        <p class="mt-3 text-sm leading-6 text-zinc-600">
          게시글과 댓글 신고를 확인하고 처리합니다.
        </p>
        <span class="mt-4 inline-block text-xs font-medium text-primary">관리하기 →</span>
      </RouterLink>
      <RouterLink
          :to="{name: 'admin-blinded'}"
          class="block surface-panel p-5 no-underline transition hover:border-zinc-300 hover:bg-accent"
      >
        <h2 class="text-lg font-medium text-zinc-900">
          블라인드 관리
        </h2>
        <p class="mt-3 text-sm leading-6 text-zinc-600">
          블라인드된 게시글과 댓글을 확인하고 해제하거나 삭제합니다.
        </p>
        <span class="mt-4 inline-block text-xs font-medium text-primary">
          관리하기 →
        </span>
      </RouterLink>
      <RouterLink
          :to="{ name: 'admin-member-sanctions' }"
          class="block surface-panel p-5 no-underline transition hover:border-zinc-300 hover:bg-accent"
      >
        <h2 class="text-lg font-medium text-zinc-900">
          회원 제재
        </h2>
        <p class="mt-3 text-sm leading-6 text-zinc-600">
          회원을 검색하고 제재 등록·이력 조회·해제를 처리합니다.
        </p>
        <span class="mt-4 inline-block text-xs font-medium text-primary">
          관리하기 →
        </span>
      </RouterLink>
      <RouterLink
          :to="{ name: 'admin-board-statistics' }"
          class="block surface-panel p-5 no-underline transition hover:border-zinc-300 hover:bg-accent"
      >
        <h2 class="text-lg font-medium text-zinc-900">
          게시판 활동 통계
        </h2>
        <p class="mt-3 text-sm leading-6 text-zinc-600">
          게시판별 현황과 게시글·댓글 작성 추이를 확인합니다.
        </p>
        <span class="mt-4 inline-block text-xs font-medium text-primary">
          통계 보기 →
        </span>
      </RouterLink>
    </section>
  </div>
</template>
