<script setup lang="ts">
import {useRoute} from 'vue-router'
import CommunityIcon from '@/components/ui/CommunityIcon.vue'
import type {PostSummaryResponse} from '@/features/post/types/post'
import PostThumbnail from '@/features/post/components/PostThumbnail.vue'
import PostSummaryLink from '@/features/post/components/PostSummaryLink.vue'

defineProps<{
  boardSlug: string
  posts: PostSummaryResponse[]
}>()

const route = useRoute()

function formatDate(value: string): string {
  return value.slice(0, 10).replaceAll('-', '.')
}
</script>

<template>
  <p
      v-if="posts.length === 0"
      class="rounded-none border border-zinc-200 px-4 py-20 text-center text-sm leading-6 text-zinc-500"
  >
    아직 등록된 게시글이 없습니다.
  </p>

  <div v-else class="grid gap-6 sm:grid-cols-2 xl:grid-cols-3">
    <PostSummaryLink
        v-for="post in posts"
        :key="post.id"
        :board-slug="boardSlug"
        :post="post"
        :query="route.query"
        class="group overflow-hidden border border-border bg-white transition-colors hover:border-zinc-300 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
    >
      <div class="aspect-square overflow-hidden border-b border-zinc-100 bg-zinc-50">
        <PostThumbnail v-if="!post.secret &&post.representativeFileId !== null && post.thumbnailAvailable"
                       :board-slug="boardSlug"
                       :post-id="post.id"
                       :file-id="post.representativeFileId"
                       class="size-full"
        />
        <div v-else class="grid size-full place-items-center" aria-hidden="true">
          <CommunityIcon name="image" class="size-10 text-zinc-300"/>
        </div>
      </div>

      <div class="grid gap-4 p-5">
        <div
            v-if="post.pinned || post.categoryName || post.blinded"
            class="flex flex-wrap items-center gap-2 text-xs"
        >
          <span
              v-if="post.blinded"
              class="rounded-none bg-amber-50 px-2 py-0.5 font-medium text-amber-700"
          >
            블라인드
          </span>
          <span
              v-if="post.pinned"
              class="rounded-none bg-accent px-2 py-0.5 font-medium text-primary"
          >
            공지
          </span>
          <span v-if="post.categoryName" class="text-zinc-500">
            {{ post.categoryName }}
          </span>
        </div>

        <div class="flex min-w-0 items-start gap-1.5">
          <CommunityIcon
              v-if="post.secret"
              name="lock"
              class="mt-0.5 size-4 text-zinc-500"
              aria-label="비밀글"
              role="img"
              :aria-hidden="false"
          />
          <h2
              class="min-w-0 break-words text-sm font-medium leading-6 text-zinc-800 group-hover:text-primary"
          >
            {{ post.title }}
          </h2>
        </div>

        <div class="flex min-w-0 items-center justify-between gap-3 text-xs text-zinc-500">
          <span class="truncate">{{ post.authorDisplayName }}</span>
          <time :datetime="post.createdAt" class="shrink-0">
            {{ formatDate(post.createdAt) }}
          </time>
        </div>

        <div class="flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-zinc-100 pt-3 text-xs text-zinc-500">
          <span>조회 {{ post.viewCount.toLocaleString() }}</span>
          <span>댓글 {{ post.commentCount.toLocaleString() }}</span>
          <span>좋아요 {{ post.likeCount.toLocaleString() }}</span>
        </div>
      </div>
      <template #blocked>
        <div class="grid aspect-square place-items-center border-b border-zinc-100 bg-zinc-50">
          <CommunityIcon name="lock" class="size-8 text-zinc-300" />
        </div>

        <div class="grid gap-4 p-5">
          <h2 class="text-sm font-medium leading-6 text-zinc-500">
            블라인드된 게시글입니다.
          </h2>

          <div class="flex items-center justify-between gap-3 text-xs text-zinc-400">
            <span class="truncate">{{ post.authorDisplayName }}</span>
            <time :datetime="post.createdAt" class="shrink-0">
              {{ formatDate(post.createdAt) }}
            </time>
          </div>
        </div>
      </template>
    </PostSummaryLink>
  </div>
</template>