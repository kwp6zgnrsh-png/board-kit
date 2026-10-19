export type MemberSearchField = 'LOGIN_ID' | 'DISPLAY_NAME'

export type MemberSanctionStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED'

export type MemberSanctionStatusFilter = MemberSanctionStatus | 'ALL'

export interface AdminMemberSearchOptions {
    field: MemberSearchField
    keyword: string
    page?: number
    size?: number
}

export interface AdminMemberSummaryResponse {
    memberId: number
    loginId: string
    displayName: string
    sanctioned: boolean
}

export interface AdminMemberPageResponse {
    members: AdminMemberSummaryResponse[]
    page: number
    size: number
    totalElements: number
    totalPages: number
}

export interface AdminMemberSanctionPageOptions {
    filter?: MemberSanctionStatusFilter
    memberId?: number
    page?: number
    size?: number
}

export interface AdminMemberSanctionSummaryResponse {
    sanctionId: number
    memberId: number
    loginId: string
    displayName: string
    memberDeleted: boolean
    reason: string
    status: MemberSanctionStatus
    startsAt: string
    endsAt: string | null
    revokedAt: string | null
    createdAt: string
}

export interface AdminMemberSanctionPageResponse {
    sanctions: AdminMemberSanctionSummaryResponse[]
    page: number
    size: number
    totalElements: number
    totalPages: number
}

export interface AdminMemberSanctionDetailResponse
    extends AdminMemberSanctionSummaryResponse {
    adminId: number
    adminDisplayName: string
    revokedBy: number | null
    revokerDisplayName: string | null
    revokeReason: string | null
}

export interface MemberSanctionCreateRequest {
    memberId: number
    reason: string
    endsAt: string | null
}

export interface MemberSanctionCreateResponse {
    id: number
}

export interface MemberSanctionRevokeRequest {
    revokeReason: string
}