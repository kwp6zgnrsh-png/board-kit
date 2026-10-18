import {apiRequest} from '@/api/http'
import type {
    MemberCreateRequest,
    MemberCreateResponse,
    MemberDisplayNameUpdateRequest,
    MemberPasswordUpdateRequest,
    MemberProfileResponse,
    MyActivityPageOptions,
    MyCommentPageResponse,
    MyPostPageResponse,
} from '@/features/member/types/member'

export function createMember(
    request: MemberCreateRequest,
): Promise<MemberCreateResponse> {
    return apiRequest<MemberCreateResponse>('/api/members', {
        method: 'POST',
        body: JSON.stringify(request),
    })
}

export function getMemberProfile(): Promise<MemberProfileResponse> {
    return apiRequest<MemberProfileResponse>('/api/members/me')
}

export function updateMemberDisplayName(
    request: MemberDisplayNameUpdateRequest,
): Promise<void> {
    return apiRequest<void>('/api/members/me/display-name', {
        method: 'PATCH',
        body: JSON.stringify(request),
    })
}

export function updateMemberPassword(
    request: MemberPasswordUpdateRequest,
): Promise<void> {
    return apiRequest<void>('/api/members/me/password', {
        method: 'PATCH',
        body: JSON.stringify(request),
    })
}

export function getMyPosts(
    options: MyActivityPageOptions = {},
): Promise<MyPostPageResponse> {
    return apiRequest<MyPostPageResponse>(
        `/api/members/me/posts?${createPageQuery(options)}`,
    )
}

export function getMyComments(
    options: MyActivityPageOptions = {},
): Promise<MyCommentPageResponse> {
    return apiRequest<MyCommentPageResponse>(
        `/api/members/me/comments?${createPageQuery(options)}`,
    )
}

function createPageQuery(options: MyActivityPageOptions): string {
    return new URLSearchParams({
        page: String(options.page ?? 1),
        size: String(options.size ?? 10),
    }).toString()
}