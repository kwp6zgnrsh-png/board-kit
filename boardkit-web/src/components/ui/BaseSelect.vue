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
    <span class="text-sm font-medium text-zinc-700">{{ label }}</span>
    <select :id="id" v-model="model" class="field-control h-9">
      <option v-for="option in options" :key="option.value" :value="option.value">
        {{ option.label }}
      </option>
    </select>
    <small v-if="hint" class="text-xs leading-5 text-zinc-500">{{ hint }}</small>
  </label>
</template>
