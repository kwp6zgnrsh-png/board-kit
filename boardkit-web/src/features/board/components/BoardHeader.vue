<script setup lang="ts">
defineProps<{ name: string; description?: string | null }>()
</script>
<template>
  <header class="grid gap-4 border-b border-border pb-8 sm:pb-10">
    <h1 class="page-title">{{ name }}</h1>
    <p v-if="description" class="text-sm leading-6 text-zinc-500">{{ description }}</p>
  </header>
</template>
