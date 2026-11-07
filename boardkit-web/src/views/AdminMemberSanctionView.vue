<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {RouterLink, useRoute, useRouter} from 'vue-router'

import {
  createMemberSanction,
  revokeMemberSanction,
} from '@/api/adminMemberSanctionApi'
import {ApiError} from '@/api/http'
import BaseButton from '@/components/ui/BaseButton.vue'
import {getAuthSession} from '@/features/auth/services/authSession'
import AdminMemberSearchPanel from '@/features/member/components/AdminMemberSearchPanel.vue'
import AdminMemberSanctionCreateForm from '@/features/member/components/AdminMemberSanctionCreateForm.vue'
import AdminMemberSanctionListPanel from '@/features/member/components/AdminMemberSanctionListPanel.vue'
import AdminMemberSanctionDetailPanel from '@/features/member/components/AdminMemberSanctionDetailPanel.vue'
import type {
  AdminMemberSummaryResponse,
  AdminMemberSanctionDetailResponse,
  MemberSanctionCreateRequest,
  MemberSanctionRevokeRequest,
} from '@/features/member/types/adminMemberSanction'

const route = useRoute()
const router = useRouter()

const selectedMember = ref<AdminMemberSummaryResponse | null>(null)
const submitting = ref<'create' | 'revoke' | null>(null)
const createError = ref('')
const revokeError = ref('')
const resultMessage = ref('')
const refreshKey = ref(0)
let contextSequence = 0

const busy = computed(() => submitting.value !== null)

const adminId = computed(() => {
  const session = getAuthSession()
  return session?.role === 'ADMIN' ? session.memberId : null
})

const memberId = computed(() => readId(route.query.memberId))
const sanctionId = computed(() => readId(route.query.sanctionId))

function readId(value: unknown): number | null {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return null

  const id = Number(value)
  return Number.isSafeInteger(id) ? id : null
}

function clearMessages() {
  createError.value = ''
  revokeError.value = ''
  resultMessage.value = ''
}

async function selectMember(member: AdminMemberSummaryResponse) {
  if (busy.value || adminId.value === null) return

  const requesterId = adminId.value
  clearMessages()

  await router.push({
    query: {
      ...route.query,
      memberId: String(member.memberId),
      sanctionId: undefined,
      page: undefined,
    },
  })

  if (adminId.value === requesterId && memberId.value === member.memberId) {
    selectedMember.value = member
  }
}

function clearMember() {
  if (busy.value) return

  selectedMember.value = null
  clearMessages()

  void router.push({
    query: {
      ...route.query,
      memberId: undefined,
      sanctionId: undefined,
      page: undefined,
    },
  })
}

function openSanction(id: number) {
  if (busy.value) return

  clearMessages()
  void router.push({
    query: {...route.query, sanctionId: String(id)},
  })
}

function closeSanction() {
  if (busy.value) return

  revokeError.value = ''
  void router.push({
    query: {...route.query, sanctionId: undefined},
  })
}

function refreshAll() {
  if (busy.value) return

  clearMessages()
  refreshKey.value++
}

async function submitCreation(request: MemberSanctionCreateRequest) {
  const member = selectedMember.value

  if (
      busy.value ||
      adminId.value === null ||
      !member ||
      request.memberId !== member.memberId ||
      memberId.value !== member.memberId
  ) return

  const context = contextSequence
  submitting.value = 'create'
  clearMessages()

  try {
    const response = await createMemberSanction(request)
    if (context !== contextSequence) return

    selectedMember.value = null
    resultMessage.value = '제재가 등록되었습니다.'
    refreshKey.value++

    void router.replace({
      query: {
        ...route.query,
        filter: undefined,
        page: undefined,
        sanctionId: String(response.id),
      },
    })
  } catch (error) {
    if (context !== contextSequence) return

    createError.value = getActionError(error, '등록')
    refreshKey.value++
  } finally {
    if (context === contextSequence) submitting.value = null
  }
}

async function submitRevocation(
    sanction: AdminMemberSanctionDetailResponse,
    request: MemberSanctionRevokeRequest,
) {
  if (
      busy.value ||
      adminId.value === null ||
      sanctionId.value !== sanction.sanctionId ||
      sanction.status !== 'ACTIVE' ||
      sanction.memberDeleted
  ) return

  const context = contextSequence
  submitting.value = 'revoke'
  clearMessages()

  try {
    await revokeMemberSanction(sanction.sanctionId, request)
    if (context !== contextSequence) return

    if (selectedMember.value?.memberId === sanction.memberId) {
      selectedMember.value = {
        ...selectedMember.value,
        sanctioned: false,
      }
    }

    resultMessage.value = '제재가 해제되었습니다.'
    refreshKey.value++
  } catch (error) {
    if (context !== contextSequence) return

    revokeError.value = getActionError(error, '해제')
    refreshKey.value++
  } finally {
    if (context === contextSequence) submitting.value = null
  }
}

function getActionError(error: unknown, action: '등록' | '해제'): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return '다시 로그인해 주세요.'
    if (error.status === 403) {
      return '요청이 거절되었습니다. 새로고침 후 다시 시도해 주세요.'
    }
    if (error.status === 404) {
      return '회원 또는 제재 이력을 찾을 수 없습니다. 최신 이력을 확인해 주세요.'
    }
    if (error.status === 409) {
      return action === '등록'
          ? '이미 제재 중인 회원입니다. 기존 제재를 확인해 주세요.'
          : '제재 상태가 변경되었습니다. 최신 이력을 확인해 주세요.'
    }
    if (error.status === 400) {
      return action === '등록'
          ? '회원 상태와 제재 사유·종료 일시를 확인해 주세요.'
          : '해제 사유를 확인해 주세요.'
    }
    if (error.status < 500) return error.message
  }

  return `${action} 결과를 확인하지 못했습니다. 이력을 새로고침해 확인해 주세요.`
}

watch(
    [adminId, () => route.fullPath],
    ([currentAdmin], [previousAdmin]) => {
      contextSequence++
      submitting.value = null
      createError.value = ''
      revokeError.value = ''

      if (currentAdmin !== previousAdmin) {
        selectedMember.value = null
        resultMessage.value = ''
      }

      if (
          selectedMember.value &&
          selectedMember.value.memberId !== memberId.value
      ) {
        selectedMember.value = null
        resultMessage.value = ''
      }
    },
    {flush: 'sync'},
)

onBeforeUnmount(() => {
  contextSequence++
})
</script>

<template>
  <div class="grid gap-8">
    <header class="page-heading">
      <div>
        <p class="text-xs font-medium text-primary">관리자</p>
        <h1 class="page-title">
          회원 제재 관리
        </h1>
        <p class="mt-2 text-sm text-zinc-500">
          회원 제재를 등록하고 이력을 조회하거나 해제합니다.
        </p>
      </div>

      <div class="flex items-center gap-2">
        <RouterLink
            :to="{ name: 'admin-dashboard' }"
            class="text-sm text-zinc-600 hover:text-primary"
        >
          관리자 메뉴
        </RouterLink>

        <BaseButton
            variant="secondary"
            size="sm"
            :disabled="busy || adminId === null"
            @click="refreshAll"
        >
          전체 새로고침
        </BaseButton>
      </div>
    </header>

    <p
        v-if="resultMessage"
        role="status"
        class="rounded-none border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700"
    >
      {{ resultMessage }}
    </p>

    <template v-if="adminId !== null">
      <div class="grid items-start gap-6 lg:grid-cols-2">
        <AdminMemberSearchPanel
            :selected-member-id="selectedMember?.memberId ?? null"
            :refresh-key="refreshKey"
            :disabled="busy"
            @select="selectMember"
        />

        <fieldset
            class="m-0 min-w-0 border-0 p-0"
            :disabled="submitting === 'revoke'"
        >
          <AdminMemberSanctionCreateForm
              v-if="selectedMember"
              :member="selectedMember"
              :submitting="submitting === 'create'"
              :error-message="createError"
              @submit="submitCreation"
              @cancel="clearMember"
          />

          <section
              v-else
              class="surface-panel px-5 py-10 text-center text-sm text-zinc-500 "
          >
            제재를 등록하려면 검색 결과에서 회원을 선택하세요.
          </section>
        </fieldset>
      </div>

      <div
          v-if="memberId !== null"
          class="flex flex-wrap items-center justify-between gap-3 rounded-none bg-zinc-50 px-4 py-3"
      >
        <p class="text-sm text-zinc-600">
          선택한 회원의 제재 이력을 조회 중입니다.
        </p>

        <BaseButton
            variant="secondary"
            size="sm"
            :disabled="busy"
            @click="clearMember"
        >
          전체 회원 보기
        </BaseButton>
      </div>

      <div
          class="grid items-start gap-6"
          :class="sanctionId !== null
          ? 'xl:grid-cols-[minmax(0,1fr)_380px]'
          : ''"
      >
        <AdminMemberSanctionListPanel
            class="min-w-0"
            :member-id="memberId"
            :selected-sanction-id="sanctionId"
            :refresh-key="refreshKey"
            :disabled="busy"
            @select="openSanction"
        />

        <AdminMemberSanctionDetailPanel
            v-if="sanctionId !== null"
            :sanction-id="sanctionId"
            :refresh-key="refreshKey"
            :submitting="submitting === 'revoke'"
            :disabled="submitting === 'create'"
            :error-message="revokeError"
            @revoke="submitRevocation"
            @close="closeSanction"
        />
      </div>
    </template>

    <p
        v-else
        role="alert"
        class="rounded-none border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
    >
      관리자 계정으로 다시 로그인해 주세요.
    </p>
  </div>
</template>