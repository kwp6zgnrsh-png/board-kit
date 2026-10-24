<script setup lang="ts">
defineProps<{ name: string; description?: string | null }>()
</script>
<template>
  <header class="grid gap-2">
    <h1 class="text-2xl font-semibold tracking-tight text-zinc-900 sm:text-3xl">{{ name }}</h1>
    <p v-if="description" class="text-sm leading-6 text-zinc-500">{{ description }}</p>
  </header>
</template>
