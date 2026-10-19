import {apiRequest} from '@/api/http'
import type {
    AdminMemberPageResponse,
    AdminMemberSearchOptions,
    AdminMemberSanctionDetailResponse,
    AdminMemberSanctionPageOptions,
    AdminMemberSanctionPageResponse,
    MemberSanctionCreateRequest,
    MemberSanctionCreateResponse,
    MemberSanctionRevokeRequest,
} from '@/features/member/types/adminMemberSanction'

export function getAdminMembers(
    options: AdminMemberSearchOptions,
): Promise<AdminMemberPageResponse> {
    const params = new URLSearchParams({
        field: options.field,
        keyword: options.keyword,
        page: String(options.page ?? 1),
        size: String(options.size ?? 10),
    })

    return apiRequest<AdminMemberPageResponse>(
        `/api/admin/members?${params}`,
    )
}

export function getAdminMemberSanctions(
    options: AdminMemberSanctionPageOptions = {},
): Promise<AdminMemberSanctionPageResponse> {
    const params = new URLSearchParams({
        filter: options.filter ?? 'ACTIVE',
        page: String(options.page ?? 1),
        size: String(options.size ?? 10),
    })

    if (options.memberId !== undefined) {
        params.set('memberId', String(options.memberId))
    }

    return apiRequest<AdminMemberSanctionPageResponse>(
        `/api/admin/member-sanctions?${params}`,
    )
}

export function getAdminMemberSanction(
    sanctionId: number,
): Promise<AdminMemberSanctionDetailResponse> {
    return apiRequest<AdminMemberSanctionDetailResponse>(
        `/api/admin/member-sanctions/${sanctionId}`,
    )
}

export function createMemberSanction(
    request: MemberSanctionCreateRequest,
): Promise<MemberSanctionCreateResponse> {
    return apiRequest<MemberSanctionCreateResponse>(
        '/api/admin/member-sanctions',
        {
            method: 'POST',
            body: JSON.stringify(request),
        },
    )
}

export function revokeMemberSanction(
    sanctionId: number,
    request: MemberSanctionRevokeRequest,
): Promise<void> {
    return apiRequest<void>(
        `/api/admin/member-sanctions/${sanctionId}/revoke`,
        {
            method: 'PATCH',
            body: JSON.stringify(request),
        },
    )
}