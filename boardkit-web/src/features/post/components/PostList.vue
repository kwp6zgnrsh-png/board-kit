<script setup lang="ts">
import type { PostSummaryResponse } from '@/features/post/types/post'
import { useRoute } from 'vue-router'

defineProps<{
  boardSlug: string
  posts: PostSummaryResponse[]
}>()

const route = useRoute()

function formatDate(value: string): string {
  const date = value.slice(0, 10).split('-')

  if (date.length !== 3) {
    return value
  }

  return `${date[1]}-${date[2]}`
}
</script>

<template>
  <div>
    <p
      v-if="posts.length === 0"
      class="m-4 rounded border-2 border-dashed border-slate-400 bg-slate-50 px-4 py-16 text-center text-sm font-bold text-slate-500"
    >
      아직 등록된 게시글이 없습니다.<br />첫 번째 이야기를 시작해 보세요.
    </p>

    <div v-else class="overflow-x-auto">
      <table class="w-full min-w-[760px] border-collapse text-sm">
        <thead>
          <tr class="border-y-2 border-slate-950 bg-slate-100 text-slate-950">
            <th class="w-20 px-3 py-3 text-center font-black">번호</th>
            <th class="w-28 px-3 py-3 text-center font-black">카테고리</th>
            <th class="px-3 py-3 text-left font-black">제목</th>
            <th class="w-28 px-3 py-3 text-center font-black">작성자</th>
            <th class="w-20 px-3 py-3 text-center font-black">작성일</th>
            <th class="w-20 px-3 py-3 text-center font-black">조회</th>
            <th class="w-20 px-3 py-3 text-center font-black">댓글</th>
          </tr>
        </thead>

        <tbody>
          <tr
            v-for="post in posts"
            :key="post.id"
            class="group border-b border-slate-300 bg-white transition hover:bg-blue-50"
          >
            <td class="px-3 py-3 text-center font-semibold text-slate-600">{{ post.id }}</td>
            <td class="px-3 py-3 text-center text-xs font-bold text-slate-500">
              {{ post.categoryName ?? '-' }}
            </td>
            <td class="px-3 py-3">
              <div class="flex min-w-0 items-center gap-2">
                <span
                  v-if="post.pinned"
                  class="shrink-0 rounded border-2 border-slate-950 bg-red-500 px-2 py-0.5 text-[10px] font-black text-white shadow-[1px_1px_0_#0f172a]"
                >
                  HOT
                </span>
                <span
                  v-if="post.secret"
                  class="shrink-0 rounded border-2 border-slate-950 bg-violet-400 px-2 py-0.5 text-[10px] font-black text-slate-950"
                >
                  LOCK
                </span>
                <RouterLink
                    :to="{
                      name: 'post-detail',
                      params: {
                        boardSlug,
                        postId: post.id,
                      },
                      query: route.query,
                    }"
                    class="truncate font-bold text-slate-900 no-underline group-hover:text-blue-600 group-hover:underline"
                >
                  {{ post.title }}
                </RouterLink>
              </div>
            </td>
            <td class="px-3 py-3 text-center text-xs font-semibold text-slate-600">{{ post.authorDisplayName }}</td>
            <td class="px-3 py-3 text-center text-xs text-slate-500">{{ formatDate(post.createdAt) }}</td>
            <td class="px-3 py-3 text-center text-xs font-semibold text-slate-600">{{ post.viewCount.toLocaleString() }}</td>
            <td class="px-3 py-3 text-center text-xs font-bold text-slate-600">
              <span class="inline-flex items-center gap-1">
                <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                  <path d="M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4z" />
                </svg>
                {{ post.commentCount }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
