import {apiRequest} from '@/api/http'
import type {
    AdminBlindPageOptions,
    AdminBlindedCommentPageResponse,
    AdminBlindedPostPageResponse,
} from '@/features/report/types/adminBlind'
import type {
    ReportTargetActionRequest,
} from '@/features/report/types/report'

export function getAdminBlindedPosts(
    options: AdminBlindPageOptions = {},
): Promise<AdminBlindedPostPageResponse> {
    const params = new URLSearchParams({
        page: String(options.page ?? 1),
        size: String(options.size ?? 10),
    })

    return apiRequest<AdminBlindedPostPageResponse>(
        `/api/admin/blinded/posts?${params}`,
    )
}

export function getAdminBlindedComments(
    options: AdminBlindPageOptions = {},
): Promise<AdminBlindedCommentPageResponse> {
    const params = new URLSearchParams({
        page: String(options.page ?? 1),
        size: String(options.size ?? 10),
    })

    return apiRequest<AdminBlindedCommentPageResponse>(
        `/api/admin/blinded/comments?${params}`,
    )
}

export function moderateBlindedPost(
    postId: number,
    request: ReportTargetActionRequest,
): Promise<void> {
    return apiRequest<void>(`/api/admin/blinded/posts/${postId}`, {
        method: 'PATCH',
        body: JSON.stringify(request),
    })
}

export function moderateBlindedComment(
    postId: number,
    commentId: number,
    request: ReportTargetActionRequest,
): Promise<void> {
    return apiRequest<void>(
        `/api/admin/blinded/posts/${postId}/comments/${commentId}`,
        {
            method: 'PATCH',
            body: JSON.stringify(request),
        },
    )
}