import { apiRequest } from '@/api/http'
import type {
    PostCreateRequest,
    PostCreateResponse,
    PostDetailResponse,
    PostPageResponse,
    PostUpdateRequest,
} from '@/features/post/types/post'

export function createPost(
    boardSlug: string,
    request: PostCreateRequest,
): Promise<PostCreateResponse> {
    return apiRequest<PostCreateResponse>(
        `/api/boards/${encodeURIComponent(boardSlug)}/posts`,
        {
            method: 'POST',
            body: JSON.stringify(request),
        },
    )
}

export function getPosts(
    boardSlug: string,
    page = 1,
    size = 10,
): Promise<PostPageResponse> {
    const params = new URLSearchParams({
        page: String(page),
        size: String(size),
    })

    return apiRequest<PostPageResponse>(
        `/api/boards/${encodeURIComponent(boardSlug)}/posts?${params}`,
    )
}

export function getPostDetail(
    boardSlug: string,
    postId: number,
    secretPassword?: string,
): Promise<PostDetailResponse> {
    const headers = new Headers()

    if (secretPassword !== undefined) {
        headers.set(
            'X-Post-Password',
            secretPassword,
        )
    }

    return apiRequest<PostDetailResponse>(
        `/api/boards/${encodeURIComponent(boardSlug)}/posts/${postId}`,
        {
            headers,
        },
    )
}

export function updatePost(
    boardSlug: string,
    postId: number,
    request: PostUpdateRequest,
): Promise<void> {
    return apiRequest<void>(
        `/api/boards/${encodeURIComponent(boardSlug)}/posts/${postId}`,
        {
            method: 'PATCH',
            body: JSON.stringify(request),
        },
    )
}

export function deletePost(
    boardSlug: string,
    postId: number,
    version: number,
): Promise<void> {
    const params = new URLSearchParams({
        version: String(version),
    })

    return apiRequest<void>(
        `/api/boards/${encodeURIComponent(boardSlug)}/posts/${postId}?${params}`,
        {
            method: 'DELETE',
        },
    )
}