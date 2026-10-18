<script setup lang="ts">
defineProps<{ name: string }>()

const paths: Record<string, string> = {
  '글머리표 목록': 'M4 6h.01M4 12h.01M4 18h.01M9 6h11M9 12h11M9 18h11',
  '번호 목록': 'M3 4h1v5M3 9h2M3 13h2l-2 4h2M9 6h11M9 12h11M9 18h11',
  '인용문': 'M4 7h6v6H4V7Zm10 0h6v6h-6V7ZM10 13c0 3-2 5-5 5m15-5c0 3-2 5-5 5',
  '인라인 코드': 'm8 7-5 5 5 5m8-10 5 5-5 5m-3-12-4 14',
  '코드 블록': 'M3 4h18v16H3V4Zm5 5-3 3 3 3m8-6 3 3-3 3m-3-7-2 8',
  '구분선 삽입': 'M4 12h16M4 6h4m-4 12h4',
  '실행 취소': 'M8 5 3 10l5 5M3 10h10a7 7 0 0 1 7 7',
  '다시 실행': 'm16 5 5 5-5 5m5-5H11a7 7 0 0 0-7 7',
  '링크': 'm10 13 4-4m-5 7-1 1a4 4 0 0 1-6-6l4-4a4 4 0 0 1 6 0m0 1 1-1a4 4 0 0 1 6 6l-4 4a4 4 0 0 1-6 0',
}
</script>

<template>
  <svg class="size-5 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path :d="paths[name]" />
  </svg>
</template>
