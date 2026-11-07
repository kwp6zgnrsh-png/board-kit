<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {getBoards} from '@/api/boardApi'
import {getPosts} from '@/api/postApi'
import {getAuthSession} from '@/features/auth/services/authSession'
import HomeBoardCard from '@/features/home/components/HomeBoardCard.vue'
import HomeRecentPanel from '@/features/home/components/HomeRecentPanel.vue'
import type {
  HomeBoardPreview,
  HomeRecentPost,
} from '@/features/home/types/home'

const session = computed(() => getAuthSession())
const previews = ref<HomeBoardPreview[]>([])
const loading = ref(true)
const errorMessage = ref('')

const recentPosts = computed<HomeRecentPost[]>(() =>
    previews.value
        .flatMap(({board, postPage}) =>
            postPage.posts.map((post) => ({board, post})),
        )
        .sort(
            (left, right) =>
                Date.parse(right.post.createdAt) - Date.parse(left.post.createdAt),
        )
        .slice(0, 5),
)

let requestSequence = 0

async function loadHome() {
  const sequence = ++requestSequence
  loading.value = true
  errorMessage.value = ''
  previews.value = []

  try {
    const boards = await getBoards()
    if (sequence !== requestSequence) return

    const results = await Promise.allSettled(
        boards.map(async (board) => ({
          board,
          postPage: await getPosts(board.slug, {page: 1, size: 10}),
        })),
    )

    if (sequence !== requestSequence) return

    previews.value = results.flatMap((result) =>
        result.status === 'fulfilled' ? [result.value] : [],
    )

    if (boards.length > 0 && previews.value.length === 0) {
      errorMessage.value = '게시글 목록을 불러오지 못했습니다.'
    }
  } catch (error) {
    if (sequence !== requestSequence) return

    errorMessage.value =
        error instanceof Error ? error.message : '홈 화면을 불러오지 못했습니다.'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

watch(
    session,
    () => {
      void loadHome()
    },
    { immediate: true, flush: 'sync' },
)
</script>

<template>
  <div class="grid gap-12 sm:gap-16">
    <section class="grid gap-10 sm:gap-12">
      <HomeRecentPanel :posts="recentPosts"/>
    </section>

    <p
        v-if="loading"
        class="rounded-none border border-zinc-200 bg-white px-6 py-16 text-center font-semibold text-zinc-600"
    >
      커뮤니티 소식을 불러오는 중입니다.
    </p>

    <p
        v-else-if="errorMessage"
        class="rounded-none border border-red-200 bg-red-50 px-6 py-12 text-center font-semibold text-red-800"
        role="alert"
    >
      {{ errorMessage }}
    </p>

    <section
        v-else-if="previews.length > 0"
        class="grid items-stretch gap-6 md:grid-cols-2 2xl:grid-cols-3"
        aria-label="게시판별 최신 글"
    >
      <HomeBoardCard
          v-for="(preview, index) in previews"
          :key="preview.board.id"
          :preview="preview"
          :index="index"
      />
    </section>

    <section
        v-else
        class="border border-dashed border-border bg-card px-6 py-20 text-center"
    >
      <h2 class="m-0 text-2xl font-medium text-zinc-900">아직 이용할 수 있는 게시판이 없습니다.</h2>
      <RouterLink
          v-if="session?.role === 'ADMIN'"
          :to="{ name: 'admin-board-create' }"
          class="mt-5 inline-flex rounded-none border border-zinc-200 bg-primary px-4 py-2 font-semibold text-white no-underline"
      >
        게시판 만들기
      </RouterLink>
    </section>
  </div>
</template>
