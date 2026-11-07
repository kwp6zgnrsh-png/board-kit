<script setup lang="ts">
import {computed, onBeforeUnmount, ref, useId, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'

import {getAdminMemberSanctions} from '@/api/adminMemberSanctionApi'
import {ApiError} from '@/api/http'
import BaseButton from '@/components/ui/BaseButton.vue'
import BasePagination from '@/components/ui/BasePagination.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import {getAuthSession} from '@/features/auth/services/authSession'
import type {
  AdminMemberSanctionPageResponse,
  MemberSanctionStatus,
  MemberSanctionStatusFilter,
} from '@/features/member/types/adminMemberSanction'
import {
  formatSanctionDate,
  sanctionStatusLabels,
} from '@/features/member/utils/memberSanctionDisplay'

const props = withDefaults(defineProps<{
  memberId?: number | null
  selectedSanctionId?: number | null
  refreshKey?: number
  disabled?: boolean
}>(), {
  memberId: null,
  selectedSanctionId: null,
  refreshKey: 0,
  disabled: false,
})

const emit = defineEmits<{
  select: [sanctionId: number]
}>()

const route = useRoute()
const router = useRouter()
const panelId = useId()

const result = ref<AdminMemberSanctionPageResponse | null>(null)
const loading = ref(false)
const errorMessage = ref('')
let requestSequence = 0

const filterOptions = [
  {value: 'ACTIVE', label: '진행 중'},
  {value: 'ALL', label: '전체'},
  {value: 'EXPIRED', label: '만료'},
  {value: 'REVOKED', label: '해제'},
] as const

const statusClasses: Record<MemberSanctionStatus, string> = {
  ACTIVE: 'bg-amber-50 text-amber-700',
  EXPIRED: 'bg-zinc-100 text-zinc-600',
  REVOKED: 'bg-accent text-primary',
}

const adminId = computed(() => {
  const session = getAuthSession()
  return session?.role === 'ADMIN' ? session.memberId : null
})

const selectedFilter = computed<MemberSanctionStatusFilter>(() => {
  const value = route.query.filter

  return value === 'ALL' || value === 'EXPIRED' || value === 'REVOKED'
      ? value
      : 'ACTIVE'
})

const requestedPage = computed(() => {
  const value = route.query.page
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return 1

  const page = Number(value)
  return Number.isSafeInteger(page) && page <= 2_147_483_647 ? page : 1
})

async function loadSanctions() {
  const sequence = ++requestSequence
  const requesterId = adminId.value
  const page = requestedPage.value

  result.value = null
  errorMessage.value = ''
  loading.value = false

  if (requesterId === null) {
    errorMessage.value = '관리자 계정으로 로그인해 주세요.'
    return
  }

  loading.value = true

  try {
    const response = await getAdminMemberSanctions({
      filter: selectedFilter.value,
      memberId: props.memberId ?? undefined,
      page,
      size: 10,
    })

    if (sequence !== requestSequence || adminId.value !== requesterId) return

    const lastPage = Math.max(1, response.totalPages)

    if (page > lastPage) {
      await router.replace({
        query: {
          ...route.query,
          page: lastPage === 1 ? undefined : String(lastPage),
        },
      })
      return
    }

    result.value = response
  } catch (error) {
    if (sequence !== requestSequence || adminId.value !== requesterId) return

    errorMessage.value =
        error instanceof ApiError && error.status === 403
            ? '관리자 권한이 필요합니다.'
            : error instanceof Error
                ? error.message
                : '제재 이력을 불러오지 못했습니다.'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

function changeFilter(value: string) {
  if (props.disabled || loading.value) return
  if (
      value !== 'ACTIVE' &&
      value !== 'ALL' &&
      value !== 'EXPIRED' &&
      value !== 'REVOKED'
  ) return
  if (value === selectedFilter.value) return

  void router.push({
    query: {
      ...route.query,
      filter: value === 'ACTIVE' ? undefined : value,
      page: undefined,
    },
  })
}

function changePage(page: number) {
  if (
      props.disabled ||
      loading.value ||
      !result.value ||
      page < 1 ||
      page > result.value.totalPages ||
      page === requestedPage.value
  ) return

  void router.push({
    query: {
      ...route.query,
      page: page === 1 ? undefined : String(page),
    },
  })
}

function refresh() {
  if (props.disabled || loading.value) return
  void loadSanctions()
}

function selectSanction(sanctionId: number) {
  if (props.disabled || loading.value || adminId.value === null) return
  emit('select', sanctionId)
}

watch(
    [
      selectedFilter,
      requestedPage,
      () => props.memberId,
      () => props.refreshKey,
      adminId,
    ],
    () => {
      void loadSanctions()
    },
    {immediate: true},
)

onBeforeUnmount(() => {
  requestSequence++
})
</script>

<template>
  <section
      class="overflow-hidden surface-panel"
      :aria-busy="loading"
      :aria-labelledby="`${panelId}-heading`"
  >
    <header class="flex flex-wrap items-center justify-between gap-3 border-b border-zinc-200 px-5 py-4">
      <div>
        <h2 :id="`${panelId}-heading`" class="text-base font-medium text-zinc-900">
          제재 이력
        </h2>
        <p class="mt-1 text-sm text-zinc-500">
          {{ memberId === null ? '전체 회원의 이력입니다.' : '선택한 회원의 이력입니다.' }}
        </p>
      </div>

      <BaseButton
          variant="secondary"
          size="sm"
          :disabled="disabled || loading"
          @click="refresh"
      >
        새로고침
      </BaseButton>
    </header>

    <fieldset
        class="m-0 border-0 px-5 py-4"
        :disabled="disabled || loading"
    >
      <div class="max-w-40">
        <BaseSelect
            :id="`${panelId}-filter`"
            label="제재 상태"
            :model-value="selectedFilter"
            :options="filterOptions"
            @update:model-value="changeFilter"
        />
      </div>
    </fieldset>

    <p
        v-if="errorMessage"
        role="alert"
        class="mx-5 mb-5 rounded-none border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
    >
      {{ errorMessage }}
    </p>

    <p v-else-if="loading" class="px-5 pb-8 text-center text-sm text-zinc-500">
      제재 이력을 불러오는 중입니다.
    </p>

    <template v-else-if="result">
      <p class="px-5 pb-3 text-sm text-zinc-500">
        전체 {{ result.totalElements.toLocaleString() }}건
      </p>

      <p
          v-if="result.sanctions.length === 0"
          class="px-5 pb-8 text-center text-sm text-zinc-500"
      >
        해당 조건의 제재 이력이 없습니다.
      </p>

      <template v-else>
        <div class="overflow-x-auto">
          <table class="w-full min-w-[800px] text-sm">
            <caption class="sr-only">회원 제재 이력 목록</caption>
            <thead class="border-y border-zinc-200 bg-zinc-50 text-zinc-600">
            <tr>
              <th scope="col" class="px-5 py-3 text-left font-medium">회원</th>
              <th scope="col" class="px-3 py-3 text-left font-medium">상태</th>
              <th scope="col" class="px-3 py-3 text-left font-medium">사유</th>
              <th scope="col" class="px-3 py-3 text-left font-medium">기간</th>
              <th scope="col" class="px-5 py-3 text-right font-medium">관리</th>
            </tr>
            </thead>
            <tbody class="divide-y divide-zinc-100">
            <tr
                v-for="sanction in result.sanctions"
                :key="sanction.sanctionId"
                :class="selectedSanctionId === sanction.sanctionId
                  ? 'bg-accent'
                  : 'hover:bg-zinc-50'"
            >
              <td class="px-5 py-3">
                <p class="font-medium text-zinc-800">{{ sanction.displayName }}</p>
                <p class="mt-1 break-all text-xs text-zinc-500">{{ sanction.loginId }}</p>
                <span
                    v-if="sanction.memberDeleted"
                    class="mt-1 inline-block text-xs text-zinc-500"
                >
                    탈퇴 회원
                  </span>
              </td>

              <td class="whitespace-nowrap px-3 py-3">
                  <span
                      class="rounded-none px-2 py-1 text-xs"
                      :class="statusClasses[sanction.status]"
                  >
                    {{ sanctionStatusLabels[sanction.status] }}
                  </span>
              </td>

              <td class="max-w-64 px-3 py-3 text-zinc-700">
                <p class="line-clamp-2 break-words">{{ sanction.reason }}</p>
              </td>

              <td class="whitespace-nowrap px-3 py-3 text-xs leading-6 text-zinc-500">
                <p>시작 {{ formatSanctionDate(sanction.startsAt) }}</p>
                <p>
                  종료 {{ sanction.endsAt === null ? '영구' : formatSanctionDate(sanction.endsAt) }}
                </p>
                <p v-if="sanction.revokedAt">
                  해제 {{ formatSanctionDate(sanction.revokedAt) }}
                </p>
              </td>

              <td class="px-5 py-3 text-right">
                <BaseButton
                    variant="secondary"
                    size="sm"
                    :disabled="disabled"
                    @click="selectSanction(sanction.sanctionId)"
                >
                  상세
                </BaseButton>
              </td>
            </tr>
            </tbody>
          </table>
        </div>

        <fieldset class="m-0 min-w-0 border-0 p-0" :disabled="disabled">
          <BasePagination
              aria-label="제재 이력 페이지"
              :page="result.page"
              :total-pages="result.totalPages"
              @change="changePage"
          />
        </fieldset>
      </template>
    </template>
  </section>
</template>