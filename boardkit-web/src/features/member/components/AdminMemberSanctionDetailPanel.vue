<script setup lang="ts">
import {computed, onBeforeUnmount, ref, useId, watch} from 'vue'

import {getAdminMemberSanction} from '@/api/adminMemberSanctionApi'
import {ApiError} from '@/api/http'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseTextarea from '@/components/ui/BaseTextarea.vue'
import {getAuthSession} from '@/features/auth/services/authSession'
import type {
  AdminMemberSanctionDetailResponse,
  MemberSanctionRevokeRequest,
} from '@/features/member/types/adminMemberSanction'
import {
  formatSanctionDate,
  sanctionStatusLabels,
} from '@/features/member/utils/memberSanctionDisplay'

const props = withDefaults(defineProps<{
  sanctionId: number
  refreshKey?: number
  submitting?: boolean
  disabled?: boolean
  errorMessage?: string
}>(), {
  refreshKey: 0,
  submitting: false,
  disabled: false,
  errorMessage: '',
})

const emit = defineEmits<{
  revoke: [
    sanction: AdminMemberSanctionDetailResponse,
    request: MemberSanctionRevokeRequest,
  ]
  close: []
}>()

const panelId = useId()
const detail = ref<AdminMemberSanctionDetailResponse | null>(null)
const loading = ref(false)
const loadError = ref('')
const revokeReason = ref('')
const validationError = ref('')
let requestSequence = 0

const adminId = computed(() => {
  const session = getAuthSession()
  return session?.role === 'ADMIN' ? session.memberId : null
})

const busy = computed(() =>
    loading.value || props.submitting || props.disabled,
)

const canRevoke = computed(() =>
    adminId.value !== null &&
    detail.value !== null &&
    detail.value.status === 'ACTIVE' &&
    !detail.value.memberDeleted,
)

async function loadDetail() {
  const sequence = ++requestSequence
  const targetId = props.sanctionId
  const requesterId = adminId.value

  detail.value = null
  loadError.value = ''
  revokeReason.value = ''
  validationError.value = ''
  loading.value = false

  if (requesterId === null) {
    loadError.value = '관리자 계정으로 로그인해 주세요.'
    return
  }

  loading.value = true

  try {
    const response = await getAdminMemberSanction(targetId)

    if (
        sequence !== requestSequence ||
        props.sanctionId !== targetId ||
        adminId.value !== requesterId
    ) return

    detail.value = response
  } catch (error) {
    if (
        sequence !== requestSequence ||
        props.sanctionId !== targetId ||
        adminId.value !== requesterId
    ) return

    if (error instanceof ApiError && error.status === 404) {
      loadError.value = '제재 이력을 찾을 수 없습니다.'
    } else if (error instanceof ApiError && error.status === 403) {
      loadError.value = '관리자 권한이 필요합니다.'
    } else {
      loadError.value = error instanceof Error
          ? error.message
          : '제재 상세를 불러오지 못했습니다.'
    }
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

function refresh() {
  if (busy.value) return
  void loadDetail()
}

function submitRevocation() {
  const current = detail.value

  if (
      busy.value ||
      !canRevoke.value ||
      !current ||
      current.sanctionId !== props.sanctionId
  ) return

  validationError.value = ''

  const normalizedReason = revokeReason.value.trim()

  if (!normalizedReason) {
    validationError.value = '해제 사유를 입력해 주세요.'
    return
  }

  if (normalizedReason.length > 2000) {
    validationError.value = '해제 사유는 2,000자 이하여야 합니다.'
    return
  }

  emit('revoke', current, {
    revokeReason: normalizedReason,
  })
}

watch(
    [() => props.sanctionId, () => props.refreshKey, adminId],
    () => {
      void loadDetail()
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
      :aria-busy="loading || submitting"
      :aria-labelledby="`${panelId}-heading`"
  >
    <header class="flex flex-wrap items-center justify-between gap-3 border-b border-zinc-200 px-5 py-4">
      <h2 :id="`${panelId}-heading`" class="text-base font-medium text-zinc-900">
        제재 상세
      </h2>

      <div class="flex gap-2">
        <BaseButton
            variant="secondary"
            size="sm"
            :disabled="busy"
            @click="refresh"
        >
          새로고침
        </BaseButton>

        <BaseButton
            variant="secondary"
            size="sm"
            :disabled="busy"
            @click="emit('close')"
        >
          닫기
        </BaseButton>
      </div>
    </header>

    <div class="grid gap-5 p-5">
      <p
          v-if="validationError || errorMessage"
          role="alert"
          class="rounded-none border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
      >
        {{ validationError || errorMessage }}
      </p>

      <p
          v-if="loadError"
          role="alert"
          class="rounded-none border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
      >
        {{ loadError }}
      </p>

      <p v-else-if="loading" class="py-6 text-center text-sm text-zinc-500">
        제재 상세를 불러오는 중입니다.
      </p>

      <template v-else-if="detail">
        <div class="rounded-none border border-zinc-200 bg-zinc-50 px-4 py-3">
          <div class="flex flex-wrap items-center gap-2">
            <h3 class="font-semibold text-zinc-900">{{ detail.displayName }}</h3>
            <span class="rounded-none bg-white px-2 py-1 text-xs text-zinc-600">
              {{ sanctionStatusLabels[detail.status] }}
            </span>
            <span v-if="detail.memberDeleted" class="text-xs text-zinc-500">
              탈퇴 회원
            </span>
          </div>
          <p class="mt-1 break-all text-sm text-zinc-500">
            로그인 ID: {{ detail.loginId }}
          </p>
        </div>

        <dl class="grid gap-4 text-sm sm:grid-cols-2">
          <div>
            <dt class="text-zinc-500">등록 관리자</dt>
            <dd class="mt-1 text-zinc-800">{{ detail.adminDisplayName }}</dd>
          </div>
          <div>
            <dt class="text-zinc-500">등록 일시</dt>
            <dd class="mt-1 text-zinc-800">
              {{ formatSanctionDate(detail.createdAt) }}
            </dd>
          </div>
          <div>
            <dt class="text-zinc-500">시작 일시</dt>
            <dd class="mt-1 text-zinc-800">
              {{ formatSanctionDate(detail.startsAt) }}
            </dd>
          </div>
          <div>
            <dt class="text-zinc-500">종료 일시</dt>
            <dd class="mt-1 text-zinc-800">
              {{ detail.endsAt === null ? '영구' : formatSanctionDate(detail.endsAt) }}
            </dd>
          </div>
          <div class="sm:col-span-2">
            <dt class="text-zinc-500">제재 사유</dt>
            <dd class="mt-1 whitespace-pre-wrap break-words text-zinc-800">{{ detail.reason }}</dd>
          </div>

          <template v-if="detail.revokedAt !== null">
            <div>
              <dt class="text-zinc-500">해제 관리자</dt>
              <dd class="mt-1 text-zinc-800">
                {{ detail.revokerDisplayName ?? '기록 없음' }}
              </dd>
            </div>
            <div>
              <dt class="text-zinc-500">해제 일시</dt>
              <dd class="mt-1 text-zinc-800">
                {{ formatSanctionDate(detail.revokedAt) }}
              </dd>
            </div>
            <div class="sm:col-span-2">
              <dt class="text-zinc-500">해제 사유</dt>
              <dd class="mt-1 whitespace-pre-wrap break-words text-zinc-800">{{ detail.revokeReason ?? '기록 없음' }}</dd>
            </div>
          </template>
        </dl>

        <form
            v-if="canRevoke"
            class="grid gap-4 border-t border-zinc-200 pt-5"
            @submit.prevent="submitRevocation"
        >
          <fieldset class="m-0 min-w-0 border-0 p-0" :disabled="busy">
            <BaseTextarea
                :id="`${panelId}-revoke-reason`"
                v-model="revokeReason"
                label="해제 사유 (필수)"
                placeholder="제재를 해제하는 사유를 입력하세요."
                :rows="4"
                :max-length="2000"
            />
          </fieldset>

          <div class="flex justify-end">
            <BaseButton type="submit" size="sm" :disabled="busy">
              {{ submitting ? '해제 중...' : '제재 해제' }}
            </BaseButton>
          </div>
        </form>

        <p
            v-else
            class="rounded-none bg-zinc-50 px-4 py-3 text-sm text-zinc-500"
        >
          {{
            detail.memberDeleted
                ? '탈퇴한 회원의 제재는 이력만 조회할 수 있습니다.'
                : '만료되었거나 해제된 제재는 이력만 조회할 수 있습니다.'
          }}
        </p>
      </template>
    </div>
  </section>
</template>