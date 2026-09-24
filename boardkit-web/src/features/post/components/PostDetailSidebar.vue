<script setup lang="ts">
defineProps<{
  boardName: string
  boardSlug: string
  authorDisplayName: string
}>()
</script>

<template>
  <aside class="grid min-w-0 content-start gap-3" aria-label="게시글 부가 정보">
    <section class="overflow-hidden rounded-lg border-[3px] border-slate-950 bg-white shadow-[2px_3px_0_#0f172a]">
      <header class="flex min-h-15 items-center gap-3 border-b-2 border-slate-950 bg-sky-400 px-3 py-2 text-slate-950">
        <span class="grid size-10 shrink-0 place-items-center rounded border-2 border-slate-950 bg-white text-2xl font-black" aria-hidden="true">●</span>
        <h2 class="m-0 text-lg font-black">작성자 정보</h2>
      </header>
      <div class="flex items-center gap-3 p-4">
        <span class="grid size-15 shrink-0 place-items-center rounded-full border-2 border-slate-950 bg-fuchsia-300 text-2xl font-black text-slate-950" aria-hidden="true">
          {{ authorDisplayName.slice(0, 1) }}
        </span>
        <div class="grid min-w-0 gap-1 break-words">
          <strong class="text-base font-black text-slate-950">{{ authorDisplayName }}</strong>
          <span class="text-xs text-slate-600">이 게시글의 작성자</span>
        </div>
      </div>
      <p class="mx-4 mb-4 border-t border-slate-300 pt-3 text-xs text-slate-600">프로필 정보는 추후 연결됩니다.</p>
    </section>

    <section class="overflow-hidden rounded-lg border-[3px] border-slate-950 bg-white shadow-[2px_3px_0_#0f172a]">
      <header class="flex min-h-15 items-center gap-3 border-b-2 border-slate-950 bg-emerald-400 px-3 py-2 text-slate-950">
        <span class="grid size-10 shrink-0 place-items-center rounded border-2 border-slate-950 bg-white text-2xl font-black" aria-hidden="true">◇</span>
        <div>
          <h2 class="m-0 text-lg leading-tight font-black">관련 글</h2>
          <p class="m-0 text-xs font-bold">{{ boardName }}에서 더 읽기</p>
        </div>
      </header>
      <div class="grid gap-4 p-4">
        <p class="m-0 text-xs text-slate-600">관련 글 추천은 준비 중입니다.</p>
        <RouterLink
          :to="{ name: 'board-detail', params: { boardSlug } }"
          class="flex justify-center gap-2 rounded border-2 border-slate-950 bg-white px-3 py-2 text-xs font-black text-slate-950 no-underline hover:bg-blue-50 focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
        >
          게시판 글 보기 <span aria-hidden="true">→</span>
        </RouterLink>
      </div>
    </section>

    <section class="overflow-hidden rounded-lg border-[3px] border-slate-950 bg-white shadow-[2px_3px_0_#0f172a]">
      <header class="flex min-h-15 items-center gap-3 border-b-2 border-slate-950 bg-yellow-300 px-3 py-2 text-slate-950">
        <span class="grid size-10 shrink-0 place-items-center rounded border-2 border-slate-950 bg-white text-2xl font-black" aria-hidden="true">✦</span>
        <div>
          <h2 class="m-0 text-lg leading-tight font-black">인기 글</h2>
          <p class="m-0 text-xs font-bold">{{ boardName }} 인기 게시글</p>
        </div>
      </header>
      <div class="p-4">
        <p class="m-0 text-xs text-slate-600">인기 글 목록은 준비 중입니다.</p>
      </div>
    </section>
  </aside>
</template>
