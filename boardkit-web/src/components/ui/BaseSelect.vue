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
  <label class="grid min-w-0 content-start gap-2" :for="id">
    <span class="text-sm font-medium text-slate-700">{{ label }}</span>
    <select :id="id" v-model="model" class="w-full min-w-0 rounded-md border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-800 outline-none transition-colors placeholder:text-slate-400 focus:border-blue-500 focus:ring-2 focus:ring-blue-100 disabled:bg-slate-50 disabled:text-slate-400 h-10">
      <option v-for="option in options" :key="option.value" :value="option.value">
        {{ option.label }}
      </option>
    </select>
    <small v-if="hint" class="text-xs leading-5 text-slate-500">{{ hint }}</small>
  </label>
</template>
