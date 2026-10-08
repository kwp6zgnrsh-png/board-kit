<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { getBoards } from '@/api/boardApi'
import { ApiError } from '@/api/http'
import type { BoardSummaryResponse } from '@/features/board/types/board'

const boards = ref<BoardSummaryResponse[]>([])
const loading = ref(true)
const errorMessage = ref('')

const plannedMenus = [
  {
    title: '관리자 공식 답변',
    description: '게시글에 등록된 공식 답변을 관리합니다.',
  },
  {
    title: '신고 처리',
    description: '게시글과 댓글 신고를 확인하고 처리합니다.',
  },
  {
    title: '회원 제재',
    description: '회원 제재 사유와 기간을 관리합니다.',
  },
  {
    title: '게시판 활동 통계',
    description: '게시판별 게시글 수와 활동 현황을 확인합니다.',
  },
]

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
  <div class="grid gap-6">
    <header
        class="flex flex-wrap items-center justify-between gap-4 pb-1"
    >
      <div>
        <p class="m-0 text-xs font-medium text-blue-600">
          관리자
        </p>

        <h1 class="m-0 mt-1 text-3xl font-semibold tracking-tight text-slate-900">
          게시판 관리
        </h1>

        <p class="m-0 mt-2 text-sm font-medium text-slate-700">
          게시판과 커뮤니티 운영 기능을 관리합니다.
        </p>
      </div>

      <RouterLink
          :to="{ name: 'admin-board-create' }"
          class="inline-flex items-center rounded border border-slate-200 bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white no-underline transition hover:bg-blue-600"
      >
        + 게시판 생성
      </RouterLink>
    </header>

    <section
        class="overflow-hidden rounded-lg border border-slate-200 bg-white"
    >
      <header
          class="border-b border-slate-200 px-5 py-4"
      >
        <h2 class="m-0 text-base font-semibold text-slate-900">
          게시판 설정
        </h2>
        <p class="m-0 mt-1 text-sm font-medium text-slate-700">
          게시판 정보와 운영 정책을 변경하거나 게시판을 보관합니다.
        </p>
      </header>

      <p
          v-if="loading"
          class="m-0 px-5 py-12 text-center font-medium text-slate-500"
      >
        게시판 목록을 불러오는 중입니다.
      </p>

      <p
          v-else-if="errorMessage"
          class="m-4 rounded border border-red-200 bg-red-50 px-4 py-6 text-center font-semibold text-red-900"
          role="alert"
      >
        {{ errorMessage }}
      </p>

      <div
          v-else-if="boards.length > 0"
          class="divide-y divide-slate-200"
      >
        <article
            v-for="board in boards"
            :key="board.id"
            class="flex flex-wrap items-center justify-between gap-4 px-5 py-4"
        >
          <div class="min-w-0">
            <h3 class="m-0 truncate text-base font-semibold text-slate-900">
              {{ board.name }}
            </h3>
            <p class="m-0 mt-1 truncate text-sm font-medium text-slate-500">
              /boards/{{ board.slug }}
            </p>
          </div>

          <RouterLink
              :to="{
              name: 'admin-board-edit',
              params: { boardSlug: board.slug },
            }"
              class="inline-flex shrink-0 items-center rounded border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-900 no-underline transition hover:bg-slate-50"
          >
            설정
          </RouterLink>
        </article>
      </div>

      <p
          v-else
          class="m-0 px-5 py-12 text-center font-medium text-slate-500"
      >
        생성된 게시판이 없습니다.
      </p>
    </section>

    <section class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <article
          v-for="menu in plannedMenus"
          :key="menu.title"
          class="rounded-md border border-slate-200 bg-slate-50 p-5"
      >
        <div class="flex items-center justify-between gap-3">
          <h2 class="m-0 text-lg font-semibold text-slate-900">
            {{ menu.title }}
          </h2>
          <span
              class="shrink-0 rounded-full bg-slate-200 px-2 py-1 text-[10px] font-medium text-slate-500"
          >
            2차 초안
          </span>
        </div>

        <p class="m-0 mt-3 text-sm font-medium leading-6 text-slate-600">
          {{ menu.description }}
        </p>
      </article>
    </section>
  </div>
</template>
