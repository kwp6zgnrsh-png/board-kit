<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { getBoards } from '@/api/boardApi'
import { getPosts } from '@/api/postApi'
import { getAuthSession } from '@/features/auth/services/authSession'
import HomeBoardCard from '@/features/home/components/HomeBoardCard.vue'
import HomeHero from '@/features/home/components/HomeHero.vue'
import HomeRecentPanel from '@/features/home/components/HomeRecentPanel.vue'
import type {
  HomeBoardPreview,
  HomeRecentPost,
} from '@/features/home/types/home'

const session = getAuthSession()
const previews = ref<HomeBoardPreview[]>([])
const loading = ref(true)
const errorMessage = ref('')

const recentPosts = computed<HomeRecentPost[]>(() =>
  previews.value
    .flatMap(({ board, postPage }) =>
      postPage.posts.map((post) => ({ board, post })),
    )
    .sort(
      (left, right) =>
        Date.parse(right.post.createdAt) - Date.parse(left.post.createdAt),
    )
    .slice(0, 5),
)

onMounted(async () => {
  try {
    const boards = await getBoards()
    const previewResults = await Promise.allSettled(
      boards.map(async (board) => ({
        board,
        postPage: await getPosts(board.slug, 1, 10),
      })),
    )

    previews.value = previewResults.flatMap((result) =>
      result.status === 'fulfilled' ? [result.value] : [],
    )

    if (boards.length > 0 && previews.value.length === 0) {
      errorMessage.value = '게시글 목록을 불러오지 못했습니다.'
    }
  } catch (error) {
    errorMessage.value =
      error instanceof Error
        ? error.message
        : '홈 화면을 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="grid gap-5">
    <section class="grid gap-4 xl:grid-cols-[minmax(0,2.4fr)_minmax(320px,1fr)]">
      <HomeHero />
      <HomeRecentPanel :posts="recentPosts" />
    </section>

    <p
      v-if="loading"
      class="rounded-lg border-[3px] border-slate-950 bg-white px-6 py-16 text-center font-black text-slate-600"
    >
      커뮤니티 소식을 불러오는 중입니다.
    </p>

    <p
      v-else-if="errorMessage"
      class="rounded-lg border-[3px] border-red-950 bg-red-100 px-6 py-12 text-center font-black text-red-800"
      role="alert"
    >
      {{ errorMessage }}
    </p>

    <section
      v-else-if="previews.length > 0"
      class="grid gap-4 md:grid-cols-2 xl:grid-cols-4"
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
      class="rounded-lg border-[3px] border-slate-950 bg-white px-6 py-16 text-center"
    >
      <h2 class="m-0 text-2xl font-black text-slate-950">아직 이용할 수 있는 게시판이 없습니다.</h2>
      <RouterLink
        v-if="session?.role === 'ADMIN'"
        :to="{ name: 'admin-board-create' }"
        class="mt-5 inline-flex rounded border-[3px] border-slate-950 bg-blue-500 px-4 py-2 font-black text-white no-underline shadow-[2px_2px_0_#0f172a]"
      >
        게시판 만들기
      </RouterLink>
    </section>
  </div>
</template>
