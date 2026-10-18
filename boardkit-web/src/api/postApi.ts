import { apiBlobRequest, apiRequest } from '@/api/http'
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
    files: File[] = [],
): Promise<PostCreateResponse> {
    return apiRequest<PostCreateResponse>(
        `/api/boards/${encodeURIComponent(boardSlug)}/posts`,
        {
            method: 'POST',
            body: createMultipartBody(request, files),
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

export function likePost(
    boardSlug: string,
    postId: number,
    secretPassword?: string,
): Promise<void> {
    const headers = new Headers()

    if (secretPassword !== undefined) {
        headers.set('X-Post-Password', secretPassword)
    }

    return apiRequest<void>(
        `/api/boards/${encodeURIComponent(boardSlug)}/posts/${postId}/like`,
        { method: 'PUT', headers },
    )
}

export function unlikePost(
    boardSlug: string,
    postId: number,
    secretPassword?: string,
): Promise<void> {
    const headers = new Headers()

    if (secretPassword !== undefined) {
        headers.set('X-Post-Password', secretPassword)
    }

    return apiRequest<void>(
        `/api/boards/${encodeURIComponent(boardSlug)}/posts/${postId}/like`,
        { method: 'DELETE', headers },
    )
}

export function updatePost(
    boardSlug: string,
    postId: number,
    request: PostUpdateRequest,
    files: File[] = [],
): Promise<void> {
    return apiRequest<void>(
        `/api/boards/${encodeURIComponent(boardSlug)}/posts/${postId}`,
        {
            method: 'PATCH',
            body: createMultipartBody(request, files),
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

function createMultipartBody(
    request: object,
    files: File[],
): FormData {
    const formData = new FormData()

    formData.append(
        'request',
        new Blob(
            [JSON.stringify(request)],
            { type: 'application/json' },
        ),
        'request.json',
    )

    for (const file of files) {
        formData.append('files', file)
    }

    return formData
}

export interface GetPostFileOptions {
    thumbnail?: boolean
    secretPassword?: string
}

export function getPostFileBlob(
    boardSlug: string,
    postId: number,
    fileId: number,
    options: GetPostFileOptions = {},
): Promise<Blob> {
    const params = new URLSearchParams()
    const headers = new Headers()

    if (options.thumbnail) {
        params.set('thumbnail', 'true')
    }

    if (options.secretPassword !== undefined) {
        headers.set('X-Post-Password', options.secretPassword)
    }

    const query = params.size > 0
        ? `?${params.toString()}`
        : ''

    return apiBlobRequest(
        `/api/boards/${encodeURIComponent(boardSlug)}` +
        `/posts/${postId}/files/${fileId}${query}`,
        { headers },
    )
}