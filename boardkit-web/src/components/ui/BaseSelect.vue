<script setup lang="ts">
defineProps<{
  id: string
  label: string
  options: ReadonlyArray<{ value: string; label: string }>
  hint?: string
}>()

const model = defineModel<string>({ required: true })
</script>

<template>
  <label class="field" :for="id">
    <span class="field__label">{{ label }}</span>
    <select :id="id" v-model="model" class="field__control">
      <option v-for="option in options" :key="option.value" :value="option.value">
        {{ option.label }}
      </option>
    </select>
    <small v-if="hint" class="field__hint">{{ hint }}</small>
  </label>
</template>

<style scoped>
.field {
  display: grid;
  gap: 0.45rem;
}

.field__label {
  color: #334155;
  font-size: 0.875rem;
  font-weight: 700;
}

.field__control {
  width: 100%;
  min-height: 2.75rem;
  padding: 0.7rem 0.85rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.65rem;
  color: #0f172a;
  background: #fff;
}

.field__control:focus {
  border-color: #2563eb;
  outline: 3px solid rgb(37 99 235 / 12%);
}

.field__hint {
  color: #64748b;
}
</style>
