<script setup lang="ts">
withDefaults(
  defineProps<{
    type?: 'button' | 'submit'
    variant?: 'primary' | 'secondary' | 'danger'
    disabled?: boolean
  }>(),
  {
    type: 'button',
    variant: 'primary',
    disabled: false,
  },
)
</script>

<template>
  <button :type="type" class="button" :class="`button--${variant}`" :disabled="disabled">
    <slot />
  </button>
</template>

<style scoped>
.button {
  min-height: 2.65rem;
  padding: 0.65rem 1rem;
  border: 0;
  border-radius: 0.65rem;
  font-weight: 700;
  cursor: pointer;
}

.button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.button--primary {
  color: #fff;
  background: #2563eb;
}

.button--secondary {
  color: #334155;
  background: #e2e8f0;
}

.button--danger {
  color: #b91c1c;
  background: #fee2e2;
}
</style>
