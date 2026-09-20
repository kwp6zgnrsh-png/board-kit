import { apiRequest } from '@/api/http'
import type {
    PostCreateRequest,
    PostCreateResponse,
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