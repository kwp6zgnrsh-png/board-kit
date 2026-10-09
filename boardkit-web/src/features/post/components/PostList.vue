<script setup lang="ts">
import type { PostSummaryResponse } from '@/features/post/types/post'
import { useRoute } from 'vue-router'
import CommunityIcon from '@/components/ui/CommunityIcon.vue'
import PostThumbnail from '@/features/post/components/PostThumbnail.vue'

defineProps<{
  boardSlug: string
  posts: PostSummaryResponse[]
}>()

const route = useRoute()

function formatDate(value: string): string {
  return value.slice(0, 16).replace('T', ' ').replaceAll('-', '.')
}
</script>

<template>
  <p v-if="posts.length === 0" class="rounded-md border border-slate-200 px-4 py-20 text-center text-sm leading-6 text-slate-500">
    아직 등록된 게시글이 없습니다.<br />첫 번째 이야기를 시작해 보세요.
  </p>
  <div v-else class="overflow-hidden rounded-md border border-slate-200">
    <table class="w-full table-fixed border-collapse text-sm">
      <caption class="sr-only">게시글 번호, 제목, 작성자, 작성일, 조회수</caption>
      <thead class="border-b border-slate-200 bg-slate-50 text-slate-700">
        <tr>
          <th scope="col" class="hidden w-16 px-3 py-2.5 text-center font-medium sm:table-cell">번호</th>
          <th scope="col" class="px-4 py-2.5 text-left font-medium">제목</th>
          <th scope="col" class="hidden w-32 px-3 py-2.5 text-left font-medium md:table-cell">작성자</th>
          <th scope="col" class="hidden w-36 px-3 py-2.5 text-center font-medium xl:table-cell">날짜</th>
          <th scope="col" class="w-16 px-3 py-2.5 text-center font-medium sm:w-20">조회</th>
        </tr>
      </thead>
      <tbody class="divide-y divide-slate-100">
        <tr v-for="post in posts" :key="post.id" class="group transition-colors hover:bg-blue-50/50" :class="post.pinned ? 'bg-slate-50/70' : 'bg-white'">
          <td class="hidden px-2 py-1.5 text-center text-xs tabular-nums text-slate-500 sm:table-cell">
            <span v-if="post.pinned" class="inline-block rounded bg-slate-400 px-2.5 py-0.5 text-[11px] font-medium text-white">공지</span>
            <template v-else>{{ post.id }}</template>
          </td>
          <td class="px-4 py-1.5">
            <RouterLink :to="{ name: 'post-detail', params: { boardSlug, postId: post.id }, query: route.query }"
              class="flex min-w-0 items-center gap-1.5 text-slate-800 group-hover:text-blue-600 focus-visible:outline-2 focus-visible:outline-blue-600">
              <span v-if="post.pinned" class="shrink-0 rounded bg-slate-400 px-1.5 py-0.5 text-[10px] font-medium text-white sm:hidden">공지</span>
              <CommunityIcon v-if="post.secret" name="lock" class="size-4 text-slate-500" aria-label="비밀글" role="img" :aria-hidden="false" />
              <span v-if="post.categoryName" class="hidden shrink-0 text-xs text-slate-400 lg:inline">[{{ post.categoryName }}]</span>
              <PostThumbnail v-if="!post.secret && post.representativeFileId !== null &&post.thumbnailAvailable"
                  :board-slug="boardSlug"
                  :post-id="post.id"
                  :file-id="post.representativeFileId"
                  class="size-8 rounded"
              />
              <span class="truncate" :class="post.pinned ? 'font-semibold' : 'font-normal'">{{ post.title }}</span>
              <span v-if="post.commentCount > 0" class="shrink-0 text-xs tabular-nums text-red-500">[{{ post.commentCount }}]</span>
            </RouterLink>
            <p class="mt-1 text-xs text-slate-400 xl:hidden"><span class="md:hidden">{{ post.authorDisplayName }} · </span>{{ formatDate(post.createdAt) }}</p>
          </td>
          <td class="hidden px-3 py-1.5 md:table-cell">
            <div class="flex items-center gap-2">
              <span class="grid size-5 shrink-0 place-items-center rounded-full bg-slate-100 text-[10px] font-medium text-slate-500" aria-hidden="true">{{ post.authorDisplayName.slice(0, 1) }}</span>
              <span class="truncate text-xs text-slate-600">{{ post.authorDisplayName }}</span>
            </div>
          </td>
          <td class="hidden px-3 py-1.5 text-center text-xs tabular-nums text-slate-500 xl:table-cell">{{ formatDate(post.createdAt) }}</td>
          <td class="px-3 py-1.5 text-center text-xs tabular-nums text-slate-500">{{ post.viewCount.toLocaleString() }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
