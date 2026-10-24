<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'
import CommunityIcon from '@/components/ui/CommunityIcon.vue'
import BoardMenu from '@/features/board/components/BoardMenu.vue'
import type { BoardSummaryResponse } from '@/features/board/types/board'
import type { AuthSession } from '@/features/auth/types/auth'

defineProps<{
  boards: BoardSummaryResponse[]
  loading: boolean
  session: AuthSession | null
}>()

const emit = defineEmits<{ navigate: [] }>()
const route = useRoute()
const managementLinks = [
  { name: 'admin-dashboard', label: '게시판 관리', icon: 'settings', matches: ['admin-dashboard', 'admin-board-create', 'admin-board-edit'] },
  { name: 'admin-post-replies', label: '공식 답변', icon: 'message', matches: ['admin-post-replies'] },
  { name: 'admin-reports', label: '신고 처리', icon: 'flag', matches: ['admin-reports', 'admin-report-detail'] },
  { name: 'admin-blinded', label: '블라인드 관리', icon: 'shield', matches: ['admin-blinded'] },
  { name: 'admin-member-sanctions', label: '회원 제재', icon: 'users', matches: ['admin-member-sanctions'] },
  { name: 'admin-board-statistics', label: '활동 통계', icon: 'chart', matches: ['admin-board-statistics'] },
] as const
</script>

<template>
  <div class="flex h-full min-h-0 flex-col gap-6 p-3">
    <RouterLink :to="{ name: 'home' }" class="flex items-center gap-3 rounded-lg p-2 focus-visible:outline-2 focus-visible:outline-ring" @click="emit('navigate')">
      <span class="grid size-9 shrink-0 place-items-center rounded-lg bg-primary text-primary-foreground shadow-xs">
        <svg viewBox="0 0 24 24" class="size-5" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true">
          <rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/>
          <rect x="3" y="14" width="7" height="7" rx="1.5"/><path d="M14 14h7v7h-7z"/>
        </svg>
      </span>
      <span class="grid gap-0.5">
        <span class="text-sm font-semibold tracking-tight text-foreground">BoardKit</span>
        <span class="text-xs text-muted-foreground">Community workspace</span>
      </span>
    </RouterLink>

    <div class="min-h-0 flex-1 overflow-y-auto">
      <section aria-label="커뮤니티 탐색">
        <h2 class="mb-2 px-3 text-[11px] font-medium tracking-wide text-muted-foreground">커뮤니티</h2>
        <BoardMenu :boards="boards" :loading="loading" variant="sidebar" @click="emit('navigate')"/>
      </section>

      <section v-if="session?.role === 'ADMIN'" class="mt-7" aria-label="관리자 메뉴">
        <h2 class="mb-2 px-3 text-[11px] font-medium tracking-wide text-muted-foreground">운영 관리</h2>
        <nav class="grid gap-1">
          <RouterLink v-for="item in managementLinks" :key="item.name" :to="{ name: item.name }"
            class="flex min-h-9 items-center gap-2.5 rounded-md px-3 py-2 text-[13px] transition-colors focus-visible:outline-2 focus-visible:outline-ring"
            :class="item.matches.some(name => name === route.name) ? 'bg-zinc-200/70 font-medium text-foreground' : 'text-zinc-600 hover:bg-zinc-200/50 hover:text-foreground'"
            :aria-current="item.matches.some(name => name === route.name) ? 'page' : undefined"
            @click="emit('navigate')">
            <CommunityIcon :name="item.icon" class="size-4"/>
            {{ item.label }}
          </RouterLink>
        </nav>
      </section>
    </div>

    <div class="border-t border-border pt-3">
      <RouterLink v-if="session" :to="{ name: 'my-page' }"
        class="flex items-center gap-3 rounded-lg p-2 transition-colors hover:bg-zinc-200/50 focus-visible:outline-2 focus-visible:outline-ring"
        @click="emit('navigate')">
        <span class="grid size-9 shrink-0 place-items-center rounded-lg border border-border bg-background text-sm font-medium">{{ session.displayName.slice(0, 1) }}</span>
        <span class="grid min-w-0 flex-1 gap-0.5">
          <span class="truncate text-sm font-medium text-foreground">{{ session.displayName }}</span>
          <span class="text-xs text-muted-foreground">{{ session.role === 'ADMIN' ? '관리자' : '커뮤니티 멤버' }}</span>
        </span>
        <CommunityIcon name="chevron" class="size-3.5 text-muted-foreground"/>
      </RouterLink>
      <div v-else class="rounded-lg border border-border bg-background p-3">
        <p class="text-xs leading-5 text-muted-foreground">로그인하고 이야기에 참여해 보세요.</p>
        <RouterLink :to="{ name: 'login' }" class="mt-2 inline-flex h-8 w-full items-center justify-center rounded-md bg-primary px-3 text-xs font-medium text-primary-foreground hover:bg-primary/90" @click="emit('navigate')">로그인</RouterLink>
      </div>
    </div>
  </div>
</template>
