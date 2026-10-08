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
    <span class="text-sm font-medium text-slate-700">
      {{ label }}
      <span v-if="required" aria-hidden="true" class="ml-0.5 text-red-500">*</span>
    </span>
    <input
      :id="id"
      v-model="model"
      class="w-full min-w-0 rounded-md border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-800 outline-none transition-colors placeholder:text-slate-400 focus:border-blue-500 focus:ring-2 focus:ring-blue-100 disabled:bg-slate-50 disabled:text-slate-400 h-10"
      :type="type"
      :placeholder="placeholder"
      :min="min"
      :max="max"
      :required="required"
      :disabled="disabled"
    />
    <small v-if="hint" class="text-xs leading-5 text-slate-500">{{ hint }}</small>
  </label>
</template>
