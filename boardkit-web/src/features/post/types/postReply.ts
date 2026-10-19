import type {JSONContent} from '@tiptap/core'

export interface PostReplyResponse {
    id: number
    postId: number
    authorId: number
    authorDisplayName: string
    content: string
    contentDocument: JSONContent | null
    version: number
    createdAt: string
    updatedAt: string
}

export interface PostReplyCreateRequest {
    contentDocument: JSONContent
}

export interface PostReplyUpdateRequest extends PostReplyCreateRequest {
    version: number
}

export type PostReplyFilter = 'ALL' | 'UNANSWERED' | 'ANSWERED'

export interface AdminPostReplyPageOptions {
    filter?: PostReplyFilter
    page?: number
    size?: number
}

export interface AdminPostReplySummaryResponse {
    postId: number
    boardSlug: string
    boardName: string
    title: string
    authorDisplayName: string
    secret: boolean
    createdAt: string
    replyId: number | null
    officialReplyEnabled: boolean
}

export interface AdminPostReplyPageResponse {
    posts: AdminPostReplySummaryResponse[]
    page: number
    size: number
    totalElements: number
    totalPages: number
}