import { apiRequest } from '@/api/http'
import type {
    GetPostsOptions,
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
    options: GetPostsOptions = {},
): Promise<PostPageResponse> {
    const params = new URLSearchParams({
        page: String(options.page ?? 1),
        size: String(options.size ?? 10),
        sort: options.sort ?? 'LATEST',
    })

    const keyword = options.keyword?.trim()

    if (keyword) {
        params.set('keyword', keyword)
    }

    if (options.categoryId !== undefined) {
        params.set('categoryId', String(options.categoryId))
    }

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