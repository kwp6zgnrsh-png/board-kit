<script setup lang="ts">
defineProps<{
  name: 'home' | 'message' | 'settings' | 'write' | 'lock' | 'image' | 'file' | 'arrow'
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
}
</script>

<template>
  <svg class="size-5 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path :d="paths[name]" />
  </svg>
</template>
