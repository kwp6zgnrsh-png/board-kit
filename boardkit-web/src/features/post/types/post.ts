import type { JSONContent } from '@tiptap/core'

export type PostSort = 'LATEST' | 'OLDEST' | 'VIEWS'

export interface GetPostsOptions {
    page?: number
    size?: number
    keyword?: string
    categoryId?: number
    sort?: PostSort
}

export interface PostCreateRequest {
    title: string
    contentDocument: JSONContent
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
    representativeFileId: number | null
    thumbnailAvailable: boolean
    likeCount: number
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
    contentDocument: JSONContent | null
    authorId: number
    authorDisplayName: string
    categoryId: number | null
    categoryName: string | null
    secret: boolean
    pinned: boolean
    commentsLocked: boolean
    viewCount: number
    version: number
    createdAt: string
    updatedAt: string
    representativeFileId: number | null
    files: PostFileResponse[]
    likeCount: number
    likedByMe: boolean
}

export interface PostUpdateRequest {
    title: string
    contentDocument: JSONContent
    categoryId: number | null
    isSecret: boolean
    secretPassword: string | null
    clearSecretPassword: boolean
    commentsLocked: boolean
    version: number
    retainedFileIds: number[]
}

export interface PostFileResponse {
    id: number
    originalName: string
    contentType: string
    fileUsage: 'BODY_IMAGE' | 'ATTACHMENT'
    sizeBytes: number
    sortOrder: number
    thumbnailAvailable: boolean
}

interface PostEditorImageBase {
    key: string
    name: string
    sizeBytes: number
    previewUrl: string
}

export type PostEditorImage = PostEditorImageBase & (
    | { kind: 'existing'; fileId: number }
    | { kind: 'new'; file: File }
)

export interface SelectedPostAttachment {
    key: string
    file: File
}