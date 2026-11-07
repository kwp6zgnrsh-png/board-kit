<script setup lang="ts">
withDefaults(
  defineProps<{
    id: string
    label: string
    placeholder?: string
    rows?: number
    maxLength?: number
    showCount?: boolean
  }>(),
  {
    placeholder: '',
    rows: 4,
    maxLength: 2000,
    showCount: true,
  },
)

const model = defineModel<string>({ required: true })
</script>

<template>
  <label class="grid min-w-0 content-start gap-2" :for="id">
    <span class="text-sm font-medium text-zinc-700">{{ label }}</span>
    <textarea
        :id="id"
        v-model="model"
        class="field-control min-h-24 resize-y py-2"
        :placeholder="placeholder"
        :rows="rows"
        :maxlength="maxLength"
    />
    <small v-if="showCount" class="justify-self-end text-xs tabular-nums text-zinc-400">{{ model.length }} / {{ maxLength }}</small>
  </label>
</template>
