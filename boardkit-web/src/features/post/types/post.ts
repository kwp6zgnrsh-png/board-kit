export interface PostCreateRequest {
    title: string
    content: string
    categoryId: number | null
    isSecret: boolean
    secretPassword: string | null
    commentsLocked: boolean
}

export interface PostCreateResponse {
    id: number
}

export interface PostSummaryResponse {
    id: number
    title: string
    authorId: number
    authorDisplayName: string
    categoryId: number | null
    categoryName: string | null
    secret: boolean
    pinned: boolean
    viewCount: number
    commentCount: number
    createdAt: string
}

export interface PostPageResponse {
    posts: PostSummaryResponse[]
    page: number
    size: number
    totalElements: number
    totalPages: number
}

export interface PostDetailResponse {
    id: number
    boardId: number
    title: string
    content: string
    authorId: number
    authorDisplayName: string
    categoryId: number | null
    categoryName: string | null
    secret: boolean
    pinned: boolean
    commentsLocked: boolean
    viewCount: number
    createdAt: string
    updatedAt: string
}