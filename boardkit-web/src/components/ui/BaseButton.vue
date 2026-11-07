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
    class="inline-flex shrink-0 items-center justify-center gap-2 whitespace-nowrap rounded-none border font-medium leading-5 transition-colors outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 disabled:pointer-events-none disabled:opacity-50"
    :class="{
      'border-primary bg-primary text-primary-foreground hover:bg-primary/90': variant === 'primary',
      'border-input bg-background text-foreground hover:bg-accent': variant === 'secondary',
      'border-red-200 bg-white text-red-600 hover:bg-red-50': variant === 'danger',
      'h-10 px-4 text-sm': size === 'md',
      'h-8 px-3 text-xs': size === 'sm',
    }">
    <slot />
  </button>
</template>
