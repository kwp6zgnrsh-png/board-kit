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
  <label class="grid min-w-0 content-start gap-2" :for="id">
    <span class="text-sm font-medium text-slate-700">{{ label }}</span>
    <textarea
        :id="id"
        v-model="model"
        class="w-full min-w-0 rounded-md border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-800 outline-none transition-colors placeholder:text-slate-400 focus:border-blue-500 focus:ring-2 focus:ring-blue-100 disabled:bg-slate-50 disabled:text-slate-400 resize-y"
        :placeholder="placeholder"
        :rows="rows"
        :maxlength="maxLength"
    />
    <small class="justify-self-end text-xs tabular-nums text-slate-400">{{ model.length }} / {{ maxLength }}</small>
  </label>
</template>
