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
  <label class="flex items-start gap-3 rounded-none border border-border bg-background p-4 "
    :class="disabled ? 'cursor-not-allowed opacity-50' : 'cursor-pointer hover:border-zinc-300'">
    <input v-model="model" type="checkbox" :disabled="disabled"
      class="mt-0.5 size-4 shrink-0 accent-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring" />
    <span class="grid gap-1">
      <strong class="text-sm font-medium text-zinc-700">{{ label }}</strong>
      <small v-if="description" class="text-xs leading-5 text-zinc-500">{{ description }}</small>
    </span>
  </label>
</template>
