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
  <label class="flex items-start gap-3 rounded-md border border-slate-200 bg-white p-3"
    :class="disabled ? 'cursor-not-allowed opacity-50' : 'cursor-pointer hover:border-slate-300'">
    <input v-model="model" type="checkbox" :disabled="disabled"
      class="mt-0.5 size-4 shrink-0 accent-blue-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600" />
    <span class="grid gap-1">
      <strong class="text-sm font-medium text-slate-700">{{ label }}</strong>
      <small v-if="description" class="text-xs leading-5 text-slate-500">{{ description }}</small>
    </span>
  </label>
</template>
