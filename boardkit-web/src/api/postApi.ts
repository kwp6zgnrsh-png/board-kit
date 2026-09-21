import { apiRequest } from '@/api/http'
import type {
    PostCreateRequest,
    PostCreateResponse,
    PostDetailResponse,
    PostPageResponse,
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