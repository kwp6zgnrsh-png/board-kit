<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {RouterLink, useRoute, useRouter} from 'vue-router'

import {getMemberProfile} from '@/api/memberApi'
import BaseButton from '@/components/ui/BaseButton.vue'
import {
  clearAuthSession,
  getAuthSession,
  saveAuthSession,
} from '@/features/auth/services/authSession'
import MemberProfileCard from '@/features/member/components/MemberProfileCard.vue'
import MemberDisplayNameForm from '@/features/member/components/MemberDisplayNameForm.vue'
import MemberPasswordForm from '@/features/member/components/MemberPasswordForm.vue'
import MyPostsPanel from '@/features/member/components/MyPostsPanel.vue'
import MyCommentsPanel from '@/features/member/components/MyCommentsPanel.vue'
import type {MemberProfileResponse} from '@/features/member/types/member'

const route = useRoute()
const router = useRouter()
const session = computed(() => getAuthSession())
const profile = ref<MemberProfileResponse | null>(null)
const loading = ref(false)
const errorMessage = ref('')
let requestSequence = 0

async function loadProfile(memberId: number) {
  const sequence = ++requestSequence
  loading.value = true
  errorMessage.value = ''

  try {
    const response = await getMemberProfile()

    if (
        sequence !== requestSequence ||
        session.value?.memberId !== memberId
    ) return

    if (response.memberId !== memberId) {
      profile.value = null
      errorMessage.value = '로그인 정보가 변경되었습니다. 페이지를 새로고침해 주세요.'
      return
    }

    profile.value = response
    saveAuthSession(response)
  } catch (error) {
    if (
        sequence !== requestSequence ||
        session.value?.memberId !== memberId
    ) return

    errorMessage.value =
        error instanceof Error
            ? error.message
            : '회원 정보를 불러오지 못했습니다.'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

function reloadProfile() {
  const memberId = session.value?.memberId
  if (memberId !== undefined) void loadProfile(memberId)
}

function handlePasswordChanged() {
  const redirect = route.fullPath

  clearAuthSession()

  void router.replace({
    name: 'login',
    query: {
      passwordChanged: 'true',
      redirect,
    },
  })
}

watch(
    () => session.value?.memberId,
    (memberId) => {
      profile.value = null

      if (memberId === undefined) {
        requestSequence++
        loading.value = false
        errorMessage.value = ''
        return
      }

      void loadProfile(memberId)
    },
    {immediate: true},
)

onBeforeUnmount(() => {
  requestSequence++
})
</script>

<template>
  <div class="mx-auto grid w-full max-w-4xl gap-6" :aria-busy="loading">
    <header class="page-heading">
      <div>
        <h1 class="page-title">
          마이페이지
        </h1>
        <p class="mt-2 text-sm text-zinc-500">
          내 계정의 기본 정보를 확인합니다.
        </p>
      </div>

      <BaseButton
          v-if="session"
          variant="secondary"
          :disabled="loading"
          @click="reloadProfile"
      >
        {{ loading ? '조회 중...' : '새로고침' }}
      </BaseButton>
    </header>

    <div
        v-if="!session"
        class="rounded-none border border-zinc-200 bg-zinc-50 px-5 py-8 text-center"
    >
      <p class="text-sm text-zinc-600">다시 로그인해 주세요.</p>
      <RouterLink
          :to="{ name: 'login', query: { redirect: route.fullPath } }"
          class="mt-3 inline-block text-sm font-medium text-primary hover:underline"
      >
        로그인
      </RouterLink>
    </div>

    <template v-else>
      <p
          v-if="errorMessage"
          role="alert"
          class="rounded-none border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
      >
        {{ errorMessage }}
      </p>

      <p
          v-if="loading && !profile"
          class="rounded-none border border-zinc-200 bg-zinc-50 px-5 py-12 text-center text-sm text-zinc-500"
      >
        회원 정보를 불러오는 중입니다.
      </p>

      <template v-if="profile">
        <MemberProfileCard :profile="profile"/>

        <MemberDisplayNameForm
            :key="`display-name-${profile.memberId}`"
            :profile="profile"
            @updated="reloadProfile"
        />

        <MemberPasswordForm
            :key="`password-${profile.memberId}`"
            @changed="handlePasswordChanged"
        />

        <MyPostsPanel
            :key="`my-posts-${profile.memberId}`"
            :member-id="profile.memberId"
        />

        <MyCommentsPanel
            :key="`my-comments-${profile.memberId}`"
            :member-id="profile.memberId"
        />
      </template>
    </template>
  </div>
</template>