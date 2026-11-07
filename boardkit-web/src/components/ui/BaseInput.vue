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
  <label class="grid min-w-0 content-start gap-2" :for="id">
    <span class="text-sm font-medium text-zinc-700">
      {{ label }}
      <span v-if="required" aria-hidden="true" class="ml-0.5 text-zinc-500">*</span>
    </span>
    <input
      :id="id"
      v-model="model"
      class="field-control h-10"
      :type="type"
      :placeholder="placeholder"
      :min="min"
      :max="max"
      :required="required"
      :disabled="disabled"
    />
    <small v-if="hint" class="text-xs leading-5 text-zinc-500">{{ hint }}</small>
  </label>
</template>
