<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string
    description?: string
    disabled?: boolean
  }>(),
  {
    description: '',
    disabled: false,
  },
)

const model = defineModel<boolean>({ required: true })
</script>

<template>
  <label class="checkbox" :class="{ 'checkbox--disabled': disabled }">
    <input v-model="model" type="checkbox" :disabled="disabled" />
    <span>
      <strong>{{ label }}</strong>
      <small v-if="description">{{ description }}</small>
    </span>
  </label>
</template>

<style scoped>
.checkbox {
  display: flex;
  gap: 0.7rem;
  align-items: flex-start;
  padding: 0.8rem;
  border: 1px solid #e2e8f0;
  border-radius: 0.65rem;
  cursor: pointer;
}

.checkbox input {
  width: 1.05rem;
  height: 1.05rem;
  margin-top: 0.15rem;
  accent-color: #2563eb;
}

.checkbox span {
  display: grid;
  gap: 0.2rem;
}

.checkbox strong {
  color: #334155;
  font-size: 0.9rem;
}

.checkbox small {
  color: #64748b;
}

.checkbox--disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
