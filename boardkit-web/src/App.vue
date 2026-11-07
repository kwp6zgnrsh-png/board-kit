<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {RouterLink, RouterView, useRoute, useRouter} from 'vue-router'
import {logout as requestLogout} from '@/api/authApi'
import {getBoards} from '@/api/boardApi'
import {ApiError} from '@/api/http'
import BaseButton from '@/components/ui/BaseButton.vue'
import CommunityIcon from '@/components/ui/CommunityIcon.vue'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import type {BoardSummaryResponse} from '@/features/board/types/board'
import {
  getAuthSession,
  clearAuthSession,
} from '@/features/auth/services/authSession'

const route = useRoute()
const router = useRouter()
const session = computed(() => getAuthSession())
const boards = ref<BoardSummaryResponse[]>([])
const loading = ref(false)
const loggingOut = ref(false)
const logoutErrorMessage = ref('')
let requestSequence = 0

async function loadBoards() {
  const sequence = ++requestSequence
  loading.value = true

  try {
    const response = await getBoards()
    if (sequence === requestSequence) boards.value = response
  } catch {
    if (sequence === requestSequence) boards.value = []
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

async function logout() {
  if (loggingOut.value) return

  loggingOut.value = true
  logoutErrorMessage.value = ''

  try {
    await requestLogout()
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 401)) {
      logoutErrorMessage.value =
          error instanceof Error ? error.message : '로그아웃에 실패했습니다.'
      return
    }
  } finally {
    loggingOut.value = false
  }

  clearAuthSession()
  await router.replace({name: 'home'})
}

watch(
    [
      () => route.fullPath,
      () => session.value?.memberId,
      () => session.value?.role,
    ],
    () => {
      void loadBoards()
    },
    {immediate: true},
)


const mobileNavigation = ref<HTMLDialogElement | null>(null)
const navigationOpen = ref(false)
const routeTitles: Record<string, string> = {
  home: '홈', login: '로그인', 'member-create': '회원가입', 'my-page': '마이페이지',
  'board-detail': '게시판', 'post-detail': '게시글 상세', 'post-create': '글쓰기', 'post-edit': '글 수정',
  'admin-dashboard': '게시판 관리', 'admin-board-create': '게시판 생성', 'admin-board-edit': '게시판 설정',
  'admin-post-replies': '공식 답변', 'admin-reports': '신고 처리', 'admin-report-detail': '신고 상세',
  'admin-blinded': '블라인드 관리', 'admin-member-sanctions': '회원 제재', 'admin-board-statistics': '활동 통계',
}
const pageTitle = computed(() => {
  if (route.name === 'board-detail') {
    return boards.value.find(board => board.slug === route.params.boardSlug)?.name ?? '게시판'
  }
  return routeTitles[String(route.name)] ?? 'um'
})
const sectionTitle = computed(() => String(route.name).startsWith('admin-') ? '운영 관리' : '커뮤니티')

function openNavigation() {
  mobileNavigation.value?.showModal()
  navigationOpen.value = true
}

function closeNavigation() {
  mobileNavigation.value?.close()
  navigationOpen.value = false
}

watch(() => route.fullPath, closeNavigation)
</script>

<template>
  <div class="min-h-svh bg-sidebar text-foreground">
    <a href="#page-content" class="sr-only z-50 rounded-none bg-primary p-3 text-sm text-primary-foreground focus:not-sr-only focus:fixed focus:left-3 focus:top-3">본문으로 이동</a>

    <aside class="fixed inset-y-0 left-0 z-30 hidden w-56 border-r border-border lg:block" aria-label="주 메뉴">
      <AppSidebar :boards="boards" :loading="loading" :session="session"/>
    </aside>

    <dialog ref="mobileNavigation" aria-label="주 메뉴" class="m-0 h-dvh max-h-none w-[min(20rem,90vw)] max-w-none border-r border-border bg-sidebar p-0 text-foreground backdrop:bg-black/40"
      @click.self="closeNavigation" @close="navigationOpen = false">
      <button type="button" class="absolute right-3 top-4 z-10 grid size-8 place-items-center rounded-none bg-sidebar text-muted-foreground hover:bg-muted focus-visible:outline-2 focus-visible:outline-ring" aria-label="메뉴 닫기" @click="closeNavigation">
        <CommunityIcon name="close" class="size-4"/>
      </button>
      <AppSidebar :boards="boards" :loading="loading" :session="session" @navigate="closeNavigation"/>
    </dialog>

    <div class="min-w-0 lg:pl-56">
      <div class="flex min-h-svh min-w-0 flex-col bg-background">
        <header class="sticky top-0 z-20 flex h-18 shrink-0 items-center justify-between gap-3 border-b border-border bg-background/95 px-5 backdrop-blur-sm sm:px-10">
          <div class="flex min-w-0 items-center gap-3">
            <button type="button" class="grid size-8 shrink-0 place-items-center rounded-none text-muted-foreground hover:bg-muted focus-visible:outline-2 focus-visible:outline-ring lg:hidden" aria-label="메뉴 열기" :aria-expanded="navigationOpen" @click="openNavigation">
              <CommunityIcon name="menu" class="size-4"/>
            </button>
            <span class="hidden font-mono text-[12px] tracking-widest text-muted-foreground lg:block">um</span>
            <span class="hidden h-4 w-px bg-border sm:block" aria-hidden="true"/>
            <nav aria-label="현재 위치" class="flex min-w-0 items-center gap-2 text-[13px]">
              <RouterLink :to="{ name: 'home' }" class="hidden shrink-0 text-muted-foreground hover:text-foreground sm:block">{{ sectionTitle }}</RouterLink>
              <CommunityIcon name="chevron" class="hidden size-3 text-zinc-400 sm:block"/>
              <span class="truncate font-medium text-foreground" aria-current="page">{{ pageTitle }}</span>
            </nav>
          </div>

          <div class="flex shrink-0 items-center gap-2">
            <RouterLink v-if="session" :to="{ name: 'my-page' }" aria-label="마이페이지" class="hidden h-8 items-center gap-2 rounded-none px-2 text-xs font-medium text-zinc-600 transition-colors hover:bg-muted sm:inline-flex">
              <CommunityIcon name="users" class="size-3.5"/>
              {{ session.displayName }}
            </RouterLink>
            <BaseButton v-if="session" size="sm" variant="secondary" :disabled="loggingOut" @click="logout">{{ loggingOut ? '로그아웃 중...' : '로그아웃' }}</BaseButton>
            <template v-else>
              <RouterLink :to="{ name: 'login' }" class="inline-flex h-8 items-center rounded-none px-3 text-xs font-medium text-zinc-600 transition-colors hover:bg-muted">로그인</RouterLink>
              <RouterLink :to="{ name: 'member-create' }" class="inline-flex h-8 items-center rounded-none border border-primary bg-primary px-3 text-xs font-medium text-primary-foreground transition-colors hover:bg-primary/90">회원가입</RouterLink>
            </template>
          </div>
        </header>

        <p v-if="logoutErrorMessage" role="alert" class="mx-4 mt-4 rounded-none border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 sm:mx-6">{{ logoutErrorMessage }}</p>

        <main id="page-content" tabindex="-1" class="mx-auto w-full min-w-0 max-w-[1440px] flex-1 px-5 py-8 outline-none sm:px-10 sm:py-12 lg:px-14">
          <RouterView/>
        </main>

        <footer class="flex flex-wrap items-center justify-between gap-2 border-t border-border px-5 py-6 text-[11px] text-muted-foreground sm:px-10">
          <p>© 2026 um</p>
          <RouterLink :to="{ name: 'home' }" class="transition-colors hover:text-foreground">홈으로</RouterLink>
        </footer>
      </div>
    </div>
  </div>
</template>
