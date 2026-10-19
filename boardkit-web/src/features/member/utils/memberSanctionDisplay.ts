import type {MemberSanctionStatus} from '@/features/member/types/adminMemberSanction'

export const sanctionStatusLabels: Record<MemberSanctionStatus, string> = {
    ACTIVE: '진행 중',
    EXPIRED: '만료',
    REVOKED: '해제',
}

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
})

export function formatSanctionDate(value: string | null): string {
    if (value === null) return '—'

    // 서버의 UTC LocalDateTime을 해석하고 마이크로초를 밀리초로 맞춘다.
    const normalized = value.replace(/(\.\d{3})\d+$/, '$1')
    const date = new Date(`${normalized}Z`)

    return Number.isNaN(date.getTime()) ? '—' : dateFormatter.format(date)
}