<script setup lang="ts">
defineProps<{
  name: string
  description: string | null
}>()
</script>

<template>
  <header class="board-header">
    <div>
      <p class="board-header__eyebrow">BOARD</p>
      <h1>{{ name }}</h1>
      <p v-if="description" class="board-header__description">
        {{ description }}
      </p>
    </div>
  </header>
</template>

<style scoped>
.board-header h1,
.board-header p {
  margin: 0;
}

.board-header__eyebrow {
  margin-bottom: 0.4rem;
  color: #2563eb;
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.board-header h1 {
  color: #0f172a;
  font-size: clamp(1.75rem, 4vw, 2.5rem);
}

.board-header__description {
  margin-top: 0.65rem;
  color: #64748b;
}
</style>