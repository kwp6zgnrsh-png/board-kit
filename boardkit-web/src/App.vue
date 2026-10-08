<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { getBoards } from '@/api/boardApi'
import BaseButton from '@/components/ui/BaseButton.vue'
import CommunityIcon from '@/components/ui/CommunityIcon.vue'
import BoardMenu from '@/features/board/components/BoardMenu.vue'
import type { BoardSummaryResponse } from '@/features/board/types/board'
import { getAuthSession, clearAuthSession } from '@/features/auth/services/authSession'

const route = useRoute()
const router = useRouter()
const session = ref(getAuthSession())
const boards = ref<BoardSummaryResponse[]>([])
const loading = ref(false)
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
  clearAuthSession()
  session.value = null
  // Also refresh when logout leaves the user on the current home route.
  void loadBoards()
  await router.replace({ name: 'home' })
}

watch(() => route.fullPath, () => {
  session.value = getAuthSession()
  void loadBoards()
}, { immediate: true })
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <a href="#page-content" class="sr-only z-50 bg-blue-600 p-3 text-sm text-white focus:not-sr-only focus:fixed focus:top-2 focus:left-2">본문으로 이동</a>
    <header class="sticky top-0 z-40 border-b border-slate-200 bg-white">
      <div class="mx-auto flex min-h-15 max-w-[1480px] items-center gap-2 px-4 sm:gap-4 sm:px-6 lg:px-8">
        <RouterLink :to="{ name: 'home' }" class="shrink-0 text-xl font-bold tracking-tight text-slate-900 sm:text-2xl lg:w-48">
          <span class="text-blue-600">Board</span>Kit
        </RouterLink>
        <BoardMenu :boards="boards" :loading="loading" class="hidden lg:flex" />
        <div class="ml-auto flex shrink-0 items-center gap-2 py-2">
          <span v-if="session" class="hidden max-w-28 truncate px-2 text-sm text-slate-600 sm:block">{{ session.displayName }}</span>
          <RouterLink v-if="session?.role === 'ADMIN'" :to="{ name: 'admin-dashboard' }"
            class="inline-flex h-9 items-center justify-center rounded-md border border-slate-200 bg-white px-3 text-xs font-medium text-slate-700 hover:bg-slate-50 sm:text-sm" aria-label="게시판 관리"><span class="sm:hidden">관리</span><span class="hidden sm:inline">게시판 관리</span></RouterLink>
          <BaseButton v-if="session" variant="secondary" @click="logout">로그아웃</BaseButton>
          <template v-else>
            <RouterLink :to="{ name: 'login' }" class="inline-flex h-9 items-center justify-center rounded-md border border-slate-200 px-3 text-sm font-medium text-slate-700 hover:bg-slate-50">로그인</RouterLink>
            <RouterLink :to="{ name: 'member-create' }" class="inline-flex h-9 items-center justify-center rounded-md border border-blue-600 bg-blue-600 px-3 text-sm font-medium text-white hover:bg-blue-700">회원가입</RouterLink>
          </template>
        </div>
      </div>
      <BoardMenu :boards="boards" :loading="loading" class="border-t border-slate-100 px-1 lg:hidden" />
    </header>

    <div class="mx-auto grid w-full max-w-[1480px] flex-1 lg:grid-cols-[240px_minmax(0,1fr)]">
      <aside class="hidden border-r border-slate-200 bg-slate-50/70 px-5 py-8 lg:block" aria-label="커뮤니티 탐색">
        <h2 class="mb-3 px-3 text-base font-semibold text-slate-900">전체 게시판</h2>
        <BoardMenu :boards="boards" :loading="loading" variant="sidebar" />
        <div v-if="session?.role === 'ADMIN'" class="mt-5 border-t border-slate-200 pt-5">
          <RouterLink :to="{ name: 'admin-dashboard' }" class="flex items-center gap-2.5 rounded-md px-3 py-3 text-sm font-medium"
            :class="String(route.name).startsWith('admin-') ? 'bg-blue-50 text-blue-600' : 'text-slate-600 hover:bg-slate-100'">
            <CommunityIcon name="settings" />게시판 관리
          </RouterLink>
        </div>
      </aside>
      <main id="page-content" tabindex="-1" class="min-w-0 px-4 py-7 outline-none sm:px-6 lg:px-8 lg:py-8">
        <RouterView />
      </main>
    </div>

    <footer class="border-t border-slate-200 bg-white">
      <div class="mx-auto flex max-w-[1480px] flex-wrap items-center justify-between gap-3 px-4 py-6 text-xs text-slate-500 sm:px-6 lg:px-8">
        <p>© 2026 BoardKit. All rights reserved.</p>
        <RouterLink :to="{ name: 'home' }" class="hover:text-blue-600">BoardKit 커뮤니티</RouterLink>
      </div>
    </footer>
  </div>
</template>
