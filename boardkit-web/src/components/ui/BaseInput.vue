<script setup lang="ts">
withDefaults(
  defineProps<{
    id: string
    label: string
    type?: 'text' | 'number' | 'password'
    placeholder?: string
    min?: number
    max?: number
    required?: boolean
    disabled?: boolean
    hint?: string
  }>(),
  {
    type: 'text',
    placeholder: '',
    required: false,
    disabled: false,
    hint: '',
  },
)

const model = defineModel<string | number>({ required: true })
</script>

<template>
  <label class="field" :for="id">
    <span class="field__label">
      {{ label }}
      <span v-if="required" aria-hidden="true" class="field__required">*</span>
    </span>
    <input
      :id="id"
      v-model="model"
      class="field__control"
      :type="type"
      :placeholder="placeholder"
      :min="min"
      :max="max"
      :required="required"
      :disabled="disabled"
    />
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

.field__required {
  color: #dc2626;
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

.field__control:disabled {
  color: #94a3b8;
  background: #f1f5f9;
}

.field__hint {
  color: #64748b;
}
</style>
