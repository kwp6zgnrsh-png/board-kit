<script setup lang="ts">
withDefaults(
    defineProps<{
      count: number
      liked: boolean
      disabled?: boolean
      loading?: boolean
      size?: 'sm' | 'md'
    }>(),
    {
      disabled: false,
      loading: false,
      size: 'md',
    },
)

const emit = defineEmits<{
  toggle: []
}>()
</script>

<template>
  <button
      type="button"
      class="inline-flex items-center justify-center gap-2 rounded-md border font-medium leading-5 transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 disabled:cursor-not-allowed disabled:opacity-50"
      :class="[
        size === 'sm' ? 'h-8 px-3 text-xs' : 'h-9 px-4 text-sm',
        liked
          ? 'border-blue-200 bg-blue-50 text-blue-600 enabled:hover:bg-blue-100'
          : 'border-slate-200 bg-white text-slate-400 enabled:hover:bg-slate-50',
      ]"
      :disabled="disabled || loading"
      :aria-pressed="liked"
      :aria-busy="loading"
      :aria-label="loading
        ? '좋아요 변경 중'
        : `${liked ? '좋아요 취소' : '좋아요'} ${count.toLocaleString()}개`"
      @click.stop="emit('toggle')"
      @keydown.stop
  >
    <svg
        class="size-4 shrink-0"
        viewBox="0 0 24 24"
        :fill="liked ? 'currentColor' : 'none'"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
    >
      <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z"/>
    </svg>

    <span class="tabular-nums">{{ count.toLocaleString() }}</span>
  </button>
</template>