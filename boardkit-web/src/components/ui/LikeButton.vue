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
      class="inline-flex items-center justify-center gap-2 rounded-md border font-medium leading-5 transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:cursor-not-allowed disabled:opacity-50"
      :class="[
        size === 'sm' ? 'h-8 px-3 text-xs' : 'h-9 px-4 text-sm',
        liked
          ? 'border-zinc-300 bg-zinc-100 text-zinc-950 enabled:hover:bg-zinc-200'
          : 'border-zinc-200 bg-white text-zinc-400 enabled:hover:bg-zinc-50',
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
      <path d="m7 10 4-7a2 2 0 0 1 3 2v5h5a2 2 0 0 1 1.96 2.4l-1.4 7A2 2 0 0 1 17.6 21H7Z"/>
      <rect x="3" y="10" width="4" height="11" rx="1"/>
    </svg>

    <span class="tabular-nums">{{ count.toLocaleString() }}</span>
  </button>
</template>
