<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <header class="topbar">
    <div class="topbar__inner">
      <strong>BoardKit</strong>
      <span>Community Management</span>
    </div>
  </header>

  <main class="app-shell">
    <RouterView />
  </main>
</template>
