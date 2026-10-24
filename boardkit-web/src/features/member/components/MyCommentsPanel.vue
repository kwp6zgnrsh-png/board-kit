<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { getMyComments } from '@/api/memberApi'
import BaseButton from '@/components/ui/BaseButton.vue'
import BasePagination from '@/components/ui/BasePagination.vue'
import { getAuthSession } from '@/features/auth/services/authSession'
import type { MyCommentPageResponse } from '@/features/member/types/member'

const props = defineProps<{
  memberId: number
}>()

const route = useRoute()
const router = useRouter()

const commentPage = ref<MyCommentPageResponse | null>(null)
const loading = ref(false)
const errorMessage = ref('')
let requestSequence = 0

const requestedPage = computed(() => {
  const value = route.query.commentPage
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return 1

  const page = Number(value)
  return Number.isSafeInteger(page) && page <= 2_147_483_647 ? page : 1
})

async function loadComments(memberId: number, page: number) {
  const sequence = ++requestSequence
  loading.value = true
  errorMessage.value = ''
  commentPage.value = null

  try {
    const response = await getMyComments({ page, size: 10 })

    if (
        sequence !== requestSequence ||
        getAuthSession()?.memberId !== memberId
    ) return

    const lastPage = Math.max(1, response.totalPages)

    if (page > lastPage) {
      await router.replace({
        query: {
          ...route.query,
          commentPage: lastPage === 1 ? undefined : String(lastPage),
        },
      })
      return
    }

    commentPage.value = response
  } catch (error) {
    if (
        sequence !== requestSequence ||
        getAuthSession()?.memberId !== memberId
    ) return

    errorMessage.value =
        error instanceof Error
            ? error.message
            : '내 댓글을 불러오지 못했습니다.'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

function reloadComments() {
  if (loading.value) return
  void loadComments(props.memberId, requestedPage.value)
}

function changePage(page: number) {
  if (
      loading.value ||
      !commentPage.value ||
      page < 1 ||
      page > commentPage.value.totalPages ||
      page === requestedPage.value
  ) return

  void router.push({
    query: {
      ...route.query,
      commentPage: page === 1 ? undefined : String(page),
    },
  })
}

watch(
    [() => props.memberId, requestedPage],
    ([memberId, page]) => {
      void loadComments(memberId, page)
    },
    { immediate: true },
)

onBeforeUnmount(() => {
  requestSequence++
})
</script>

<template>
  <section
      class="overflow-hidden rounded-xl border border-border bg-card shadow-xs"
      :aria-busy="loading"
  >
    <header class="flex items-center justify-between gap-3 border-b border-zinc-200 px-5 py-4">
      <div>
        <h2 class="text-base font-semibold text-zinc-900">내가 쓴 댓글</h2>
        <p v-if="commentPage" class="mt-1 text-sm text-zinc-500">
          전체 {{ commentPage.totalElements.toLocaleString() }}개
        </p>
      </div>

      <BaseButton
          variant="secondary"
          size="sm"
          :disabled="loading"
          @click="reloadComments"
      >
        새로고침
      </BaseButton>
    </header>

    <div
        v-if="errorMessage"
        role="alert"
        class="m-5 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
    >
      {{ errorMessage }}
    </div>

    <p v-else-if="loading" class="px-5 py-12 text-center text-sm text-zinc-500">
      댓글을 불러오는 중입니다.
    </p>

    <template v-else-if="commentPage">
      <p
          v-if="commentPage.comments.length === 0"
          class="px-5 py-12 text-center text-sm text-zinc-500"
      >
        작성한 댓글이 없습니다.
      </p>

      <template v-else>
        <ul class="divide-y divide-zinc-100">
          <li
              v-for="comment in commentPage.comments"
              :key="comment.id"
              class="px-5 py-4"
          >
            <div class="mb-2 flex flex-wrap items-center gap-2 text-xs text-zinc-500">
              <span>{{ comment.boardName }}</span>

              <span
                  v-if="comment.parentId !== null"
                  class="rounded bg-accent px-1.5 py-0.5 text-primary"
              >
                대댓글
              </span>

              <span
                  v-if="comment.boardStatus === 'ARCHIVED'"
                  class="rounded bg-zinc-100 px-1.5 py-0.5"
              >
                보관
              </span>
            </div>

            <p class="line-clamp-3 whitespace-pre-wrap break-words text-sm leading-6 text-zinc-800">{{ comment.content }}</p>

            <div class="mt-3 flex flex-wrap items-center justify-between gap-2">
              <time :datetime="comment.createdAt" class="text-xs text-zinc-500">
                {{ comment.createdAt.replace('T', ' ').slice(0, 16) }}
              </time>

              <span v-if="comment.postDeleted" class="text-xs text-zinc-400">
                원문이 삭제되었습니다.
              </span>

              <RouterLink
                  v-else
                  :to="{
                  name: 'post-detail',
                  params: {
                    boardSlug: comment.boardSlug,
                    postId: comment.postId,
                  },
                }"
                  class="text-xs font-medium text-primary hover:underline"
              >
                원문 보기
              </RouterLink>
            </div>
          </li>
        </ul>

        <BasePagination
            aria-label="내 댓글 페이지"
            :page="commentPage.page"
            :total-pages="commentPage.totalPages"
            @change="changePage"
        />
      </template>

      <p class="border-t border-zinc-100 px-5 py-3 text-xs text-zinc-500">
        게시판 설정에 따라 원문 열람이 제한될 수 있습니다.
      </p>
    </template>
  </section>
</template>