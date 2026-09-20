<script setup lang="ts">
withDefaults(
  defineProps<{
    id: string
    label: string
    placeholder?: string
    rows?: number
    maxLength?: number
  }>(),
  {
    placeholder: '',
    rows: 4,
    maxLength: 2000,
  },
)

const model = defineModel<string>({ required: true })
</script>

<template>
  <label class="field" :for="id">
    <span class="field__label">{{ label }}</span>
    <textarea
      :id="id"
      v-model="model"
      class="field__control"
      :placeholder="placeholder"
      :rows="rows"
      :maxlength="maxLength"
    />
    <small class="field__count">{{ model.length }} / {{ maxLength }}</small>
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
  padding: 0.7rem 0.85rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.65rem;
  color: #0f172a;
  background: #fff;
  resize: vertical;
}

.field__control:focus {
  border-color: #2563eb;
  outline: 3px solid rgb(37 99 235 / 12%);
}

.field__count {
  justify-self: end;
  color: #64748b;
}
</style>
