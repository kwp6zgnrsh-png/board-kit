<script setup lang="ts">
defineProps<{ name: string; slug: string; description: string | null }>()
</script>
<template>
  <header class="flex flex-wrap items-baseline gap-x-4 gap-y-2">
    <h1 class="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">{{ name }}</h1>
    <p v-if="description" class="text-sm leading-6 text-slate-500">{{ description }}</p>
  </header>
</template>
