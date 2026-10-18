<script setup lang="ts">
import type {MemberProfileResponse} from '@/features/member/types/member'

defineProps<{
  profile: MemberProfileResponse
}>()
</script>

<template>
  <section class="overflow-hidden rounded-lg border border-slate-200 bg-white">
    <header class="border-b border-slate-200 px-5 py-4">
      <h2 class="text-base font-semibold text-slate-900">기본 정보</h2>
    </header>

    <dl class="grid gap-5 px-5 py-5 sm:grid-cols-2">
      <div>
        <dt class="text-sm text-slate-500">닉네임</dt>
        <dd class="mt-1 break-words font-medium text-slate-900">
          {{ profile.displayName }}
        </dd>
      </div>
      <div>
        <dt class="text-sm text-slate-500">아이디</dt>
        <dd class="mt-1 break-all font-medium text-slate-900">
          {{ profile.loginId }}
        </dd>
      </div>
      <div>
        <dt class="text-sm text-slate-500">회원 구분</dt>
        <dd class="mt-1 font-medium text-slate-900">
          {{ profile.role === 'ADMIN' ? '관리자' : '일반 회원' }}
        </dd>
      </div>
      <div>
        <dt class="text-sm text-slate-500">가입일</dt>
        <dd class="mt-1 font-medium text-slate-900">
          {{ profile.createdAt.slice(0, 10) }}
        </dd>
      </div>
    </dl>
  </section>
</template>