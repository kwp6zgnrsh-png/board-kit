import type { MemberRole } from '@/features/auth/types/auth'
import type { BoardStatus } from '@/features/board/types/board'

export interface MemberCreateRequest {
    loginId: string
    password: string
    passwordConfirm: string
    displayName: string
}

export interface MemberCreateResponse {
    id: number
    loginId: string
    displayName: string
}

export interface MemberProfileResponse {
    memberId: number
    loginId: string
    displayName: string
    role: MemberRole
    createdAt: string
}

export interface MemberDisplayNameUpdateRequest {
    displayName: string
}

export interface MemberPasswordUpdateRequest {
    currentPassword: string
    newPassword: string
    newPasswordConfirm: string
}

export interface MyActivityPageOptions {
    page?: number
    size?: number
}

export interface MyPostSummaryResponse {
    id: number
    boardSlug: string
    boardName: string
    boardStatus: BoardStatus
    title: string
    secret: boolean
    viewCount: number
    createdAt: string
}

export interface MyPostPageResponse {
    posts: MyPostSummaryResponse[]
    page: number
    size: number
    totalElements: number
    totalPages: number
}

export interface MyCommentSummaryResponse {
    id: number
    postId: number
    parentId: number | null
    boardSlug: string
    boardName: string
    boardStatus: BoardStatus
    postDeleted: boolean
    content: string
    createdAt: string
}

export interface MyCommentPageResponse {
    comments: MyCommentSummaryResponse[]
    page: number
    size: number
    totalElements: number
    totalPages: number
}