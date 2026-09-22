<script setup lang="ts">
import { computed } from 'vue'

import { getBoardTheme } from '@/features/board/services/boardTheme'

const props = defineProps<{
  name: string
  slug: string
  description: string | null
}>()

const theme = computed(() => getBoardTheme(props.slug))

const themeStyle = computed(() => ({
  '--board-accent': theme.value.accent,
  '--board-accent-strong': theme.value.accentStrong,
  '--board-soft': theme.value.soft,
}))
</script>

<template>
  <section
    :style="themeStyle"
    class="relative overflow-hidden rounded-lg border-[3px] border-slate-950 bg-[var(--board-soft)] px-5 py-6 sm:px-8 lg:min-h-48 lg:px-10"
  >
    <div class="relative z-10 max-w-3xl">
      <p class="mb-5 flex items-center gap-2 text-xs font-black text-slate-600">
        <span>BOARDKIT</span>
        <span aria-hidden="true">›</span>
        <span>{{ name }}</span>
      </p>

      <h1 class="font-display m-0 text-5xl leading-[0.95] text-slate-950 uppercase italic sm:text-6xl lg:text-7xl">
        <span class="title-highlight inline-block px-3 py-1">
          {{ name }}
        </span>
      </h1>

      <p class="mt-5 max-w-xl text-base font-bold text-slate-700 sm:text-xl">
        {{ description || '사람들과 자유롭게 이야기를 나누는 공간입니다.' }}
      </p>
    </div>

    <div class="pointer-events-none absolute right-6 bottom-0 hidden h-44 w-[38%] min-w-96 lg:block" aria-hidden="true">
      <div class="absolute right-64 bottom-0 h-24 w-24 rounded-full border-[4px] border-slate-950 bg-yellow-300">
        <span class="absolute top-8 left-7 h-2 w-2 rounded-full bg-slate-950"></span>
        <span class="absolute top-8 right-7 h-2 w-2 rounded-full bg-slate-950"></span>
        <span class="absolute right-7 bottom-6 left-7 h-4 rounded-b-full border-b-[4px] border-slate-950"></span>
      </div>
      <div class="hero-burst absolute right-36 bottom-[-4px] h-32 w-32"></div>
      <div class="hero-triangle absolute right-3 bottom-[-5px] h-32 w-32"></div>
      <div class="absolute right-24 top-0 rotate-[-8deg] border-[3px] border-slate-950 bg-slate-950 px-5 py-3 text-center text-sm font-black tracking-[0.18em] text-white shadow-[2px_2px_0_var(--board-accent)]">
        GOOD PEOPLE<br />GREAT IDEAS
      </div>
    </div>
  </section>
</template>

<style scoped>
.title-highlight {
  background: var(--board-accent);
  box-shadow: 8px 8px 0 rgb(255 255 255 / 78%);
  transform: rotate(-1deg);
}

.hero-burst {
  background: #0f172a;
  clip-path: polygon(
    50% 0%, 61% 23%, 82% 8%, 79% 34%, 100% 39%, 78% 53%,
    94% 73%, 68% 72%, 69% 100%, 51% 79%, 34% 100%, 33% 73%,
    6% 78%, 23% 55%, 0% 42%, 25% 35%, 18% 9%, 40% 24%
  );
}

.hero-burst::after {
  position: absolute;
  inset: 5px;
  background: #3b82f6;
  clip-path: inherit;
  content: '';
}

.hero-triangle {
  background: #0f172a;
  clip-path: polygon(50% 0, 100% 100%, 0 100%);
}

.hero-triangle::after {
  position: absolute;
  inset: 5px 6px 0;
  background: #f472b6;
  clip-path: inherit;
  content: '';
}
</style>
