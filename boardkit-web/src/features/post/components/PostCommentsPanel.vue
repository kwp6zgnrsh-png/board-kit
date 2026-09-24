<script setup lang="ts">
defineProps<{
  locked: boolean
}>()
</script>

<template>
  <section
    class="overflow-hidden rounded-lg border-[3px] border-slate-950 bg-white shadow-[2px_3px_0_#0f172a]"
    aria-labelledby="comments-heading"
  >
    <header class="flex min-h-13 items-center gap-2 border-b-2 border-slate-950 bg-slate-50 px-4 py-2">
      <span class="text-xl font-black text-slate-950" aria-hidden="true">▤</span>
      <h2 id="comments-heading" class="m-0 text-lg font-black text-slate-950">댓글</h2>
    </header>
    <div class="grid min-h-26 content-center gap-2 p-4 text-sm font-bold text-slate-600">
      <p v-if="locked" class="m-0">이 게시글은 댓글 작성이 잠겨 있습니다.</p>
      <p class="m-0">댓글 기능은 준비 중입니다.</p>
    </div>
  </section>
</template>
