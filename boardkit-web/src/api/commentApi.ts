import { apiRequest } from '@/api/http'
import type {
    CommentCreateRequest,
    CommentCreateResponse,
    CommentPageResponse,
    CommentUpdateRequest,
    GetCommentsOptions,
} from '@/features/comment/types/comment'

function createSecretHeaders(secretPassword?: string): Headers {
    const headers = new Headers()

    if (secretPassword !== undefined) {
        headers.set('X-Post-Password', secretPassword)
    }

    return headers
}

export function createComment(
    boardSlug: string,
    postId: number,
    request: CommentCreateRequest,
    secretPassword?: string,
): Promise<CommentCreateResponse> {
    return apiRequest<CommentCreateResponse>(
        `/api/boards/${encodeURIComponent(boardSlug)}/posts/${postId}/comments`,
        {
            method: 'POST',
            headers: createSecretHeaders(secretPassword),
            body: JSON.stringify(request),
        },
    )
}

export function getComments(
    boardSlug: string,
    postId: number,
    options: GetCommentsOptions = {},
): Promise<CommentPageResponse> {
    const params = new URLSearchParams({
        page: String(options.page ?? 1),
        size: String(options.size ?? 20),
    })

    return apiRequest<CommentPageResponse>(
        `/api/boards/${encodeURIComponent(boardSlug)}/posts/${postId}/comments?${params}`,
        {
            headers: createSecretHeaders(options.secretPassword),
        },
    )
}

export function updateComment(
    boardSlug: string,
    postId: number,
    commentId: number,
    request: CommentUpdateRequest,
    secretPassword?: string,
): Promise<void> {
    return apiRequest<void>(
        `/api/boards/${encodeURIComponent(boardSlug)}/posts/${postId}/comments/${commentId}`,
        {
            method: 'PATCH',
            headers: createSecretHeaders(secretPassword),
            body: JSON.stringify(request),
        },
    )
}

export function deleteComment(
    boardSlug: string,
    postId: number,
    commentId: number,
    version: number,
): Promise<void> {
    const params = new URLSearchParams({
        version: String(version),
    })

    return apiRequest<void>(
        `/api/boards/${encodeURIComponent(boardSlug)}/posts/${postId}/comments/${commentId}?${params}`,
        {
            method: 'DELETE',
        },
    )
}

export function likeComment(
    boardSlug: string,
    postId: number,
    commentId: number,
    secretPassword?: string,
): Promise<void> {
    return apiRequest<void>(
        `/api/boards/${encodeURIComponent(boardSlug)}` +
        `/posts/${postId}/comments/${commentId}/like`,
        {
            method: 'PUT',
            headers: createSecretHeaders(secretPassword),
        },
    )
}

export function unlikeComment(
    boardSlug: string,
    postId: number,
    commentId: number,
    secretPassword?: string,
): Promise<void> {
    return apiRequest<void>(
        `/api/boards/${encodeURIComponent(boardSlug)}` +
        `/posts/${postId}/comments/${commentId}/like`,
        {
            method: 'DELETE',
            headers: createSecretHeaders(secretPassword),
        },
    )
}