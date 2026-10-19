import type { JSONContent } from '@tiptap/core'
import type { BoardStatus } from '@/features/board/types/board'

export type ReportReason = 'SPAM' | 'ABUSE' | 'OTHER'
export type ReportStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED'
export type ReportStatusFilter = 'ALL' | ReportStatus
export type ReportReviewStatus = 'ACCEPTED' | 'REJECTED'

export interface ReportCreateRequest {
    reason: ReportReason
    detail: string | null
}

export interface ReportCreateResponse {
    id: number
}

export interface AdminReportPageOptions {
    filter?: ReportStatusFilter
    page?: number
    size?: number
}

export interface AdminReportSummaryResponse {
    id: number
    postId: number
    commentId: number | null
    boardSlug: string
    boardName: string
    boardStatus: BoardStatus
    postTitle: string
    reporterDisplayName: string
    reason: ReportReason
    status: ReportStatus
    postDeleted: boolean
    commentDeleted: boolean
    createdAt: string
}

export interface AdminReportPageResponse {
    reports: AdminReportSummaryResponse[]
    page: number
    size: number
    totalElements: number
    totalPages: number
}

export interface AdminReportDetailResponse {
    id: number
    reporterId: number
    reporterDisplayName: string
    postId: number
    commentId: number | null
    boardSlug: string
    boardName: string
    boardStatus: BoardStatus
    postTitle: string
    authorId: number
    authorDisplayName: string
    reason: ReportReason
    detail: string | null
    status: ReportStatus
    content: string
    contentDocument: JSONContent | null
    contentCreatedAt: string
    contentUpdatedAt: string | null
    postDeleted: boolean
    commentDeleted: boolean
    reviewedBy: number | null
    reviewerDisplayName: string | null
    reviewNote: string | null
    createdAt: string
    reviewedAt: string | null
    postBlinded: boolean
    commentBlinded: boolean
    targetVersion: number
}

export interface ReportReviewRequest {
    status: ReportReviewStatus
    reviewNote: string
}

export type ReportTargetAction = 'BLIND' | 'UNBLIND' | 'DELETE'

export interface ReportTargetActionRequest {
    action: ReportTargetAction
    version: number
}