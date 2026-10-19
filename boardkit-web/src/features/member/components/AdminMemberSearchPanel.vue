<script setup lang="ts">
import {computed, onBeforeUnmount, ref, useId, watch} from 'vue'

import {getAdminMembers} from '@/api/adminMemberSanctionApi'
import {ApiError} from '@/api/http'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BasePagination from '@/components/ui/BasePagination.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import {getAuthSession} from '@/features/auth/services/authSession'
import type {
  AdminMemberPageResponse,
  AdminMemberSummaryResponse,
  MemberSearchField,
} from '@/features/member/types/adminMemberSanction'

const props = withDefaults(defineProps<{
  selectedMemberId?: number | null
  disabled?: boolean
  refreshKey?: number
}>(), {
  selectedMemberId: null,
  disabled: false,
  refreshKey: 0,
})

const emit = defineEmits<{
  select: [member: AdminMemberSummaryResponse]
}>()

const formId = useId()
const field = ref<MemberSearchField>('LOGIN_ID')
const keyword = ref('')
const result = ref<AdminMemberPageResponse | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const appliedSearch = ref<{
  field: MemberSearchField
  keyword: string
} | null>(null)

let requestSequence = 0

const fieldOptions = [
  {value: 'LOGIN_ID', label: '로그인 ID'},
  {value: 'DISPLAY_NAME', label: '닉네임'},
] as const

const adminId = computed(() => {
  const session = getAuthSession()
  return session?.role === 'ADMIN' ? session.memberId : null
})

function changeField(value: string) {
  if (value === 'LOGIN_ID' || value === 'DISPLAY_NAME') {
    field.value = value
  }
}

function search() {
  if (props.disabled || loading.value) return

  const normalizedKeyword = keyword.value.trim()
  errorMessage.value = ''

  if (!normalizedKeyword) {
    result.value = null
    appliedSearch.value = null
    errorMessage.value = '검색어를 입력해 주세요.'
    return
  }

  appliedSearch.value = {
    field: field.value,
    keyword: normalizedKeyword,
  }

  void loadMembers(1)
}

async function loadMembers(page: number) {
  const query = appliedSearch.value
  const requesterId = adminId.value

  if (!query || requesterId === null || props.disabled) return

  const sequence = ++requestSequence
  loading.value = true
  errorMessage.value = ''
  result.value = null

  try {
    const response = await getAdminMembers({
      ...query,
      page,
      size: 10,
    })

    if (sequence !== requestSequence || adminId.value !== requesterId) return

    const lastPage = Math.max(1, response.totalPages)

    if (page > lastPage) {
      await loadMembers(lastPage)
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
                : '회원 검색 결과를 불러오지 못했습니다.'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

function changePage(page: number) {
  if (
      props.disabled ||
      loading.value ||
      !result.value ||
      page < 1 ||
      page > result.value.totalPages ||
      page === result.value.page
  ) return

  void loadMembers(page)
}

function selectMember(member: AdminMemberSummaryResponse) {
  if (props.disabled || loading.value || adminId.value === null) return
  emit('select', member)
}

watch(adminId, (value) => {
  requestSequence++
  result.value = null
  appliedSearch.value = null
  loading.value = false
  errorMessage.value = value === null ? '관리자 계정으로 로그인해 주세요.' : ''
}, {immediate: true})

watch(() => props.refreshKey, () => {
  if (appliedSearch.value && adminId.value !== null) {
    void loadMembers(result.value?.page ?? 1)
  }
})

onBeforeUnmount(() => {
  requestSequence++
})
</script>

<template>
  <section
      class="overflow-hidden rounded-lg border border-slate-200 bg-white"
      :aria-busy="loading"
      :aria-labelledby="`${formId}-heading`"
  >
    <header class="border-b border-slate-200 px-5 py-4">
      <h2 :id="`${formId}-heading`" class="text-base font-semibold text-slate-900">
        회원 검색
      </h2>
      <p class="mt-1 text-sm text-slate-500">
        관리할 회원을 로그인 ID 또는 닉네임으로 검색하세요.
      </p>
    </header>

    <form class="p-5" @submit.prevent="search">
      <fieldset
          class="m-0 grid min-w-0 gap-3 border-0 p-0 sm:grid-cols-[140px_minmax(0,1fr)_auto] sm:items-end"
          :disabled="disabled || loading || adminId === null"
      >
        <BaseSelect
            :id="`${formId}-field`"
            :model-value="field"
            label="검색 항목"
            :options="fieldOptions"
            @update:model-value="changeField"
        />

        <BaseInput
            :id="`${formId}-keyword`"
            v-model="keyword"
            label="검색어"
            placeholder="검색어를 입력하세요"
            required
        />

        <BaseButton type="submit" class="h-10">
          {{ loading ? '검색 중...' : '검색' }}
        </BaseButton>
      </fieldset>
    </form>

    <p
        v-if="errorMessage"
        role="alert"
        class="mx-5 mb-5 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
    >
      {{ errorMessage }}
    </p>

    <p v-else-if="loading" class="px-5 pb-8 text-center text-sm text-slate-500">
      회원을 검색하는 중입니다.
    </p>

    <template v-else-if="result">
      <p class="border-t border-slate-100 px-5 py-3 text-sm text-slate-500">
        검색 결과 {{ result.totalElements.toLocaleString() }}명
      </p>

      <p
          v-if="result.members.length === 0"
          class="px-5 pb-8 text-center text-sm text-slate-500"
      >
        검색 조건에 맞는 회원이 없습니다.
      </p>

      <template v-else>
        <div class="overflow-x-auto">
          <table class="w-full min-w-[480px] text-sm">
            <caption class="sr-only">회원 검색 결과</caption>
            <thead class="border-y border-slate-200 bg-slate-50 text-slate-600">
            <tr>
              <th scope="col" class="px-5 py-3 text-left font-medium">로그인 ID</th>
              <th scope="col" class="px-3 py-3 text-left font-medium">닉네임</th>
              <th scope="col" class="px-3 py-3 text-left font-medium">제재 상태</th>
              <th scope="col" class="px-5 py-3 text-right font-medium">선택</th>
            </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
            <tr
                v-for="member in result.members"
                :key="member.memberId"
                :class="selectedMemberId === member.memberId ? 'bg-blue-50' : 'hover:bg-slate-50'"
            >
              <td class="break-all px-5 py-3 text-slate-700">
                {{ member.loginId }}
              </td>
              <td class="px-3 py-3 font-medium text-slate-800">
                {{ member.displayName }}
              </td>
              <td class="px-3 py-3">
                  <span
                      class="inline-block whitespace-nowrap rounded px-2 py-1 text-xs"
                      :class="member.sanctioned
                      ? 'bg-amber-50 text-amber-700'
                      : 'bg-slate-100 text-slate-500'"
                  >
                    {{ member.sanctioned ? '제재 중' : '제재 없음' }}
                  </span>
              </td>
              <td class="px-5 py-3 text-right">
                <BaseButton
                    variant="secondary"
                    size="sm"
                    :disabled="disabled || selectedMemberId === member.memberId"
                    @click="selectMember(member)"
                >
                  {{ selectedMemberId === member.memberId ? '선택됨' : '선택' }}
                </BaseButton>
              </td>
            </tr>
            </tbody>
          </table>
        </div>

        <BasePagination
            aria-label="회원 검색 결과 페이지"
            :page="result.page"
            :total-pages="result.totalPages"
            @change="changePage"
        />
      </template>
    </template>

    <p v-else class="px-5 pb-8 text-center text-sm text-slate-500">
      검색어를 입력하고 검색 버튼을 눌러 주세요.
    </p>
  </section>
</template>