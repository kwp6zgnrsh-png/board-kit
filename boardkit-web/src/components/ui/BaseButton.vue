<script setup lang="ts">
withDefaults(
  defineProps<{
    type?: 'button' | 'submit'
    variant?: 'primary' | 'secondary' | 'danger'
    size?: 'sm' | 'md'
    disabled?: boolean
  }>(),
  {
    type: 'button',
    variant: 'primary',
    size: 'md',
    disabled: false,
  },
)
</script>

<template>
  <button :type="type" :disabled="disabled"
    class="inline-flex items-center justify-center gap-2 rounded-md border font-medium leading-5 transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 disabled:opacity-50"
    :class="{
      'border-blue-600 bg-blue-600 text-white hover:border-blue-700 hover:bg-blue-700': variant === 'primary',
      'border-slate-200 bg-white text-slate-700 hover:bg-slate-50': variant === 'secondary',
      'border-red-200 bg-white text-red-600 hover:bg-red-50': variant === 'danger',
      'h-9 px-4 text-sm': size === 'md',
      'h-8 px-3 text-xs': size === 'sm',
    }">
    <slot />
  </button>
</template>
