<script setup lang="ts">
defineProps<{
  name: 'home' | 'message' | 'settings' | 'write' | 'lock' | 'image' | 'file' | 'arrow' | 'chart' | 'users' | 'flag' | 'shield' | 'menu' | 'close' | 'panel' | 'chevron'
}>()

const paths = {
  home: 'M3 10 12 3l9 7M5 9v12h5v-7h4v7h5V9',
  message: 'M5 4h14a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H9l-6 3V6a2 2 0 0 1 2-2ZM7 10h.01M12 10h.01M17 10h.01',
  settings: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8ZM9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1 1-3Z',
  write: 'm14 4 6 6M4 20l1-6L16 3a2 2 0 0 1 3 0l2 2a2 2 0 0 1 0 3L10 19l-6 1Z',
  lock: 'M7 10V7a5 5 0 0 1 10 0v3M5 10h14v11H5V10ZM12 14v3',
  image: 'M3 3h18v18H3V3ZM3 16l6-6 4 4 3-3 5 5M16 7h.01',
  file: 'M14 2H5v20h14V7l-5-5ZM14 2v5h5M8 12h8M8 16h6',
  arrow: 'M5 12h14m-5-5 5 5-5 5',
  chart: 'M4 3v18h17M8 16v-4M13 16V8M18 16V5',
  users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M16 3a4 4 0 0 1 0 8M22 21v-2a4 4 0 0 0-3-3.87M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  flag: 'M4 22V3m0 1c4-3 8 3 12 0v10c-4 3-8-3-12 0',
  shield: 'M12 3 3 7v5c0 5 5 8 9 10 4-2 9-5 9-10V7l-9-4ZM9 12l2 2 4-4',
  menu: 'M4 6h16M4 12h16M4 18h16',
  close: 'm6 6 12 12M6 18 18 6',
  panel: 'M3 3h18v18H3V3ZM9 3v18',
  chevron: 'm9 5 7 7-7 7',
}
</script>

<template>
  <svg class="size-4 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path :d="paths[name]" />
  </svg>
</template>
