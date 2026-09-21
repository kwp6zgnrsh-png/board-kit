<script setup lang="ts">
import type {
  PostSummaryResponse,
} from '@/features/post/types/post'

defineProps<{
  boardSlug: string
  page: number
  posts: PostSummaryResponse[]
}>()

function formatDate(value: string): string {
  return value
      .replace('T', ' ')
      .slice(0, 16)
}
</script>

<template>
  <div class="post-list">
    <p
        v-if="posts.length === 0"
        class="post-list__empty"
    >
      등록된 게시글이 없습니다.
    </p>

    <div v-else class="post-list__table-wrapper">
      <table class="post-list__table">
        <thead>
        <tr>
          <th>번호</th>
          <th>카테고리</th>
          <th class="post-list__title">제목</th>
          <th>작성자</th>
          <th>작성일</th>
          <th>조회</th>
        </tr>
        </thead>

        <tbody>
        <tr
            v-for="post in posts"
            :key="post.id"
        >
          <td>{{ post.id }}</td>

          <td>
            {{ post.categoryName ?? '-' }}
          </td>

          <td class="post-list__title">
              <span
                  v-if="post.pinned"
                  class="badge badge--pinned"
              >
                고정
              </span>

            <span
                v-if="post.secret"
                class="badge badge--secret"
            >
                비밀
              </span>

            <RouterLink
                :to="{
                  name: 'post-detail',
                  params: {
                    boardSlug,
                    postId: post.id,
                  },
                  query: {
                    page: page > 1
                      ? String(page)
                      : undefined,
                  },
                }"
                class="post-title-link"
            >
              {{ post.title }}
            </RouterLink>

            <span
                v-if="post.commentCount > 0"
                class="comment-count"
            >
                [{{ post.commentCount }}]
              </span>
          </td>

          <td>{{ post.authorDisplayName }}</td>
          <td>{{ formatDate(post.createdAt) }}</td>
          <td>{{ post.viewCount }}</td>
        </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.post-title-link {
  color: #0f172a;
  font-weight: 600;
  text-decoration: none;
}

.post-title-link:hover {
  color: #2563eb;
  text-decoration: underline;
}

.post-list__empty {
  margin: 0;
  padding: 3rem 1rem;
  color: #64748b;
  text-align: center;
}

.post-list__table-wrapper {
  overflow-x: auto;
}

.post-list__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}

.post-list__table th,
.post-list__table td {
  padding: 0.85rem 0.75rem;
  border-bottom: 1px solid #e2e8f0;
  color: #475569;
  text-align: center;
  white-space: nowrap;
}

.post-list__table th {
  color: #334155;
  background: #f8fafc;
  font-weight: 700;
}

.post-list__table .post-list__title {
  width: 100%;
  text-align: left;
  white-space: normal;
}

.badge {
  display: inline-block;
  margin-right: 0.35rem;
  padding: 0.15rem 0.4rem;
  border-radius: 0.35rem;
  font-size: 0.7rem;
  font-weight: 700;
}

.badge--pinned {
  color: #1d4ed8;
  background: #dbeafe;
}

.badge--secret {
  color: #7c3aed;
  background: #ede9fe;
}

.comment-count {
  margin-left: 0.3rem;
  color: #2563eb;
  font-size: 0.8rem;
}
</style>