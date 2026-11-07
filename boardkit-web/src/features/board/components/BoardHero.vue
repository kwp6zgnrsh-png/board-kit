<script setup lang="ts">
defineProps<{ name: string; slug: string; description: string | null }>()
</script>
<template>
  <header class="mb-3 flex flex-wrap items-baseline gap-x-5 gap-y-2 border-b border-border pb-8 sm:pb-10">
    <h1 class="page-title">{{ name }}</h1>
    <p v-if="description" class="max-w-2xl text-sm leading-7 text-zinc-500">{{ description }}</p>
  </header>
</template>
