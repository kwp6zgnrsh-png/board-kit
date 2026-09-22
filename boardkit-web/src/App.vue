<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'

import BoardMenu from '@/features/board/components/BoardMenu.vue'
import {
  getAuthSession,
} from '@/features/auth/services/authSession'

const route = useRoute()
const session = ref(getAuthSession())

watch(
  () => route.fullPath,
  () => {
    session.value = getAuthSession()
  },
)
</script>

<template>
  <header class="sticky top-0 z-40 border-b-[3px] border-slate-950 bg-white/95 backdrop-blur">
    <div class="mx-auto flex min-h-20 w-full max-w-[1500px] items-center gap-4 px-4 py-3 lg:px-6">
      <div class="shrink-0 rotate-[-1deg] rounded border-[3px] border-slate-950 bg-yellow-300 px-4 py-2 shadow-[2px_2px_0_#0f172a]">
        <strong class="font-display block text-xl leading-none text-slate-950 sm:text-2xl">
          BOARDKIT
        </strong>
        <span class="mt-1 block text-[9px] font-black tracking-[0.18em] text-slate-800">
          PEOPLE · IDEAS · TOGETHER
        </span>
      </div>

      <BoardMenu />

      <div class="ml-auto flex shrink-0 items-center gap-2 border-l-2 border-slate-950 pl-3">
        <RouterLink
          v-if="session?.role === 'ADMIN'"
          :to="{ name: 'admin-board-create' }"
          class="hidden rounded border-2 border-slate-950 bg-white px-3 py-2 text-xs font-black text-slate-950 shadow-[2px_2px_0_#0f172a] transition hover:-translate-y-0.5 sm:block"
        >
          + 게시판
        </RouterLink>

        <span
          v-if="session"
          class="rounded border-2 border-slate-950 bg-pink-300 px-3 py-2 text-xs font-black text-slate-950"
        >
          {{ session.displayName }}
        </span>

        <RouterLink
          v-else
          :to="{ name: 'login' }"
          class="rounded border-[3px] border-slate-950 bg-pink-300 px-4 py-2 text-sm font-black text-slate-950 shadow-[2px_2px_0_#0f172a] transition hover:-translate-y-0.5"
        >
          로그인
        </RouterLink>
      </div>
    </div>
  </header>

  <main class="mx-auto min-h-[calc(100vh-10rem)] w-full max-w-[1500px] px-4 py-6 lg:px-6 lg:py-8">
    <RouterView />
  </main>

  <footer class="border-t-[3px] border-slate-950 bg-white">
    <div class="mx-auto flex w-full max-w-[1500px] flex-wrap items-center justify-between gap-3 px-4 py-5 text-xs text-slate-600 lg:px-6">
      <strong class="font-display text-lg text-slate-950">BOARDKIT</strong>
      <p class="font-semibold tracking-wide">A SMALL POST CAN MAKE A BIG CONNECTION.</p>
      <p>Dynamic community management</p>
    </div>
  </footer>
</template>
