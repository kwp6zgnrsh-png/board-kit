import type {BoardStatus} from '@/features/board/types/board'

export interface AdminBlindPageOptions {
    page?: number
    size?: number
}

export interface AdminBlindedPostSummaryResponse {
    postId: number
    boardSlug: string
    boardName: string
    boardStatus: BoardStatus
    title: string
    authorId: number
    authorDisplayName: string
    blindedAt: string
    version: number
}

export interface AdminBlindedCommentSummaryResponse {
    commentId: number
    parentId: number | null
    postId: number
    boardSlug: string
    boardName: string
    boardStatus: BoardStatus
    postTitle: string
    contentPreview: string
    authorId: number
    authorDisplayName: string
    blindedAt: string
    version: number
}

export interface AdminBlindedPostPageResponse {
    posts: AdminBlindedPostSummaryResponse[]
    page: number
    size: number
    totalElements: number
    totalPages: number
}

export interface AdminBlindedCommentPageResponse {
    comments: AdminBlindedCommentSummaryResponse[]
    page: number
    size: number
    totalElements: number
    totalPages: number
}