<script setup lang="ts">
import { RouterView } from 'vue-router'

import BoardMenu from '@/features/board/components/BoardMenu.vue'
</script>

<template>
  <header class="topbar">
    <div class="topbar__inner">
      <div class="topbar__brand">
        <strong>BoardKit</strong>
        <span>Community Management</span>
      </div>

      <BoardMenu />
    </div>
  </header>

  <main class="app-shell">
    <RouterView />
  </main>
</template>