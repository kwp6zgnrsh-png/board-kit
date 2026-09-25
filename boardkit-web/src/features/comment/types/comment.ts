export interface CommentCreateRequest {
    content: string
    parentId: number | null
}

export interface CommentCreateResponse {
    id: number
}

export interface CommentUpdateRequest {
    content: string
    version: number
}

export interface CommentItemResponse {
    id: number
    parentId: number | null
    authorId: number
    authorDisplayName: string
    content: string | null
    deleted: boolean
    version: number
    createdAt: string
    updatedAt: string
}

export interface CommentPageResponse {
    comments: CommentItemResponse[]
    page: number
    size: number
    totalElements: number
    totalPages: number
}

export interface GetCommentsOptions {
    page?: number
    size?: number
    secretPassword?: string
}