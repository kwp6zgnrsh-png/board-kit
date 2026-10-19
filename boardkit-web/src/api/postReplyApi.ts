import {apiRequest} from '@/api/http'
import type {
    AdminPostReplyPageOptions,
    AdminPostReplyPageResponse,
    PostReplyCreateRequest,
    PostReplyResponse,
    PostReplyUpdateRequest,
} from '@/features/post/types/postReply'

export async function getPostReply(
    boardSlug: string,
    postId: number,
    secretPassword?: string,
): Promise<PostReplyResponse | null> {
    const headers = new Headers()

    if (secretPassword !== undefined) {
        headers.set('X-Post-Password', secretPassword)
    }

    const response = await apiRequest<PostReplyResponse | undefined>(
        postReplyUrl(boardSlug, postId),
        {headers},
    )

    return response ?? null
}

export function getAdminPostReplyTargets(
    options: AdminPostReplyPageOptions = {},
): Promise<AdminPostReplyPageResponse> {
    const params = new URLSearchParams({
        filter: options.filter ?? 'UNANSWERED',
        page: String(options.page ?? 1),
        size: String(options.size ?? 10),
    })

    return apiRequest<AdminPostReplyPageResponse>(
        `/api/admin/post-replies?${params}`,
    )
}

export function createPostReply(
    boardSlug: string,
    postId: number,
    request: PostReplyCreateRequest,
): Promise<PostReplyResponse> {
    return apiRequest<PostReplyResponse>(
        postReplyUrl(boardSlug, postId),
        {
            method: 'POST',
            body: JSON.stringify(request),
        },
    )
}

export function updatePostReply(
    boardSlug: string,
    postId: number,
    replyId: number,
    request: PostReplyUpdateRequest,
): Promise<void> {
    return apiRequest<void>(
        `${postReplyUrl(boardSlug, postId)}/${replyId}`,
        {
            method: 'PATCH',
            body: JSON.stringify(request),
        },
    )
}

export function deletePostReply(
    boardSlug: string,
    postId: number,
    replyId: number,
    version: number,
): Promise<void> {
    const params = new URLSearchParams({
        version: String(version),
    })

    return apiRequest<void>(
        `${postReplyUrl(boardSlug, postId)}/${replyId}?${params}`,
        {method: 'DELETE'},
    )
}

function postReplyUrl(boardSlug: string, postId: number): string {
    return `/api/boards/${encodeURIComponent(boardSlug)}/posts/${postId}/reply`
}