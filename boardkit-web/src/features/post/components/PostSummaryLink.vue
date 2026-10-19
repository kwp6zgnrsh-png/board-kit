<script setup lang="ts">
import {computed} from 'vue'
import {RouterLink} from 'vue-router'
import type {LocationQueryRaw} from 'vue-router'

import {getAuthSession} from '@/features/auth/services/authSession'
import type {PostSummaryResponse} from '@/features/post/types/post'

defineOptions({inheritAttrs: false})

const props = defineProps<{
  boardSlug: string
  post: PostSummaryResponse
  query?: LocationQueryRaw
}>()

const canOpen = computed(() => {
  if (!props.post.blinded) return true

  const session = getAuthSession()
  return session !== null && (
      session.role === 'ADMIN' ||
      session.memberId === props.post.authorId
  )
})
</script>

<template>
  <RouterLink
      v-if="canOpen"
      v-bind="$attrs"
      :to="{
      name: 'post-detail',
      params: { boardSlug: props.boardSlug, postId: props.post.id },
      query: props.query,
    }"
  >
    <slot/>
  </RouterLink>

  <div
      v-else
      v-bind="$attrs"
      data-blocked="true"
      class="cursor-default data-[blocked=true]:hover:border-slate-200 data-[blocked=true]:hover:bg-slate-50 data-[blocked=true]:hover:text-slate-500"
  >
    <slot name="blocked">
      <span class="text-sm text-slate-500">
        블라인드된 게시글입니다.
      </span>
    </slot>
  </div>
</template>