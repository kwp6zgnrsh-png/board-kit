<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {RouterLink, useRoute, useRouter} from 'vue-router'

import {getMyPosts} from '@/api/memberApi'
import BaseButton from '@/components/ui/BaseButton.vue'
import BasePagination from '@/components/ui/BasePagination.vue'
import {getAuthSession} from '@/features/auth/services/authSession'
import type {MyPostPageResponse} from '@/features/member/types/member'

const props = defineProps<{
  memberId: number
}>()

const route = useRoute()
const router = useRouter()

const postPage = ref<MyPostPageResponse | null>(null)
const loading = ref(false)
const errorMessage = ref('')
let requestSequence = 0

const requestedPage = computed(() => {
  const value = route.query.postPage
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return 1

  const page = Number(value)
  return Number.isSafeInteger(page) && page <= 2_147_483_647 ? page : 1
})

async function loadPosts(memberId: number, page: number) {
  const sequence = ++requestSequence
  loading.value = true
  errorMessage.value = ''
  postPage.value = null

  try {
    const response = await getMyPosts({page, size: 10})

    if (
        sequence !== requestSequence ||
        getAuthSession()?.memberId !== memberId
    ) return

    const lastPage = Math.max(1, response.totalPages)

    if (page > lastPage) {
      await router.replace({
        query: {
          ...route.query,
          postPage: lastPage === 1 ? undefined : String(lastPage),
        },
      })
      return
    }

    postPage.value = response
  } catch (error) {
    if (
        sequence !== requestSequence ||
        getAuthSession()?.memberId !== memberId
    ) return

    errorMessage.value =
        error instanceof Error
            ? error.message
            : '내 게시글을 불러오지 못했습니다.'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

function reloadPosts() {
  if (loading.value) return
  void loadPosts(props.memberId, requestedPage.value)
}

function changePage(page: number) {
  if (
      loading.value ||
      !postPage.value ||
      page < 1 ||
      page > postPage.value.totalPages ||
      page === requestedPage.value
  ) return

  void router.push({
    query: {
      ...route.query,
      postPage: page === 1 ? undefined : String(page),
    },
  })
}

watch(
    [() => props.memberId, requestedPage],
    ([memberId, page]) => {
      void loadPosts(memberId, page)
    },
    {immediate: true},
)

onBeforeUnmount(() => {
  requestSequence++
})
</script>

<template>
  <section
      class="overflow-hidden rounded-lg border border-slate-200 bg-white"
      :aria-busy="loading"
  >
    <header class="flex items-center justify-between gap-3 border-b border-slate-200 px-5 py-4">
      <div>
        <h2 class="text-base font-semibold text-slate-900">내가 쓴 게시글</h2>
        <p v-if="postPage" class="mt-1 text-sm text-slate-500">
          전체 {{ postPage.totalElements.toLocaleString() }}개
        </p>
      </div>

      <BaseButton
          variant="secondary"
          size="sm"
          :disabled="loading"
          @click="reloadPosts"
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

    <p v-else-if="loading" class="px-5 py-12 text-center text-sm text-slate-500">
      게시글을 불러오는 중입니다.
    </p>

    <template v-else-if="postPage">
      <p
          v-if="postPage.posts.length === 0"
          class="px-5 py-12 text-center text-sm text-slate-500"
      >
        작성한 게시글이 없습니다.
      </p>

      <template v-else>
        <div class="overflow-x-auto">
          <table class="w-full min-w-[620px] table-fixed text-sm">
            <caption class="sr-only">내가 작성한 게시글 목록</caption>
            <thead class="border-b border-slate-200 bg-slate-50 text-slate-600">
            <tr>
              <th scope="col" class="px-5 py-3 text-left font-medium">제목</th>
              <th scope="col" class="w-32 px-3 py-3 text-left font-medium">게시판</th>
              <th scope="col" class="w-36 px-3 py-3 text-left font-medium">작성일</th>
              <th scope="col" class="w-20 px-5 py-3 text-right font-medium">조회</th>
            </tr>
            </thead>

            <tbody class="divide-y divide-slate-100">
            <tr
                v-for="post in postPage.posts"
                :key="post.id"
                class="hover:bg-slate-50"
            >
              <td class="px-5 py-3">
                <RouterLink
                    :to="{
                      name: 'post-detail',
                      params: {
                        boardSlug: post.boardSlug,
                        postId: post.id,
                      },
                    }"
                    class="block truncate font-medium text-slate-800 hover:text-blue-600 hover:underline"
                >
                    <span v-if="post.secret" class="mr-1 text-xs text-slate-500">
                      [비밀]
                    </span>
                  {{ post.title }}
                </RouterLink>
              </td>

              <td class="px-3 py-3 text-slate-600">
                <span class="block truncate">{{ post.boardName }}</span>
                <span
                    v-if="post.boardStatus === 'ARCHIVED'"
                    class="mt-1 inline-block rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-500"
                >
                    보관
                  </span>
              </td>

              <td class="px-3 py-3 text-xs text-slate-500">
                {{ post.createdAt.replace('T', ' ').slice(0, 16) }}
              </td>

              <td class="px-5 py-3 text-right tabular-nums text-slate-500">
                {{ post.viewCount.toLocaleString() }}
              </td>
            </tr>
            </tbody>
          </table>
        </div>

        <BasePagination
            aria-label="내 게시글 페이지"
            :page="postPage.page"
            :total-pages="postPage.totalPages"
            @change="changePage"
        />
      </template>

      <p class="border-t border-slate-100 px-5 py-3 text-xs text-slate-500">
        게시판 설정에 따라 게시글 상세 열람이 제한될 수 있습니다.
      </p>
    </template>
  </section>
</template>