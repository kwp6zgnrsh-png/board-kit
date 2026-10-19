import {apiRequest} from '@/api/http'
import type {
    AdminReportDetailResponse,
    AdminReportPageOptions,
    AdminReportPageResponse,
    ReportCreateRequest,
    ReportCreateResponse,
    ReportReviewRequest,
    ReportTargetActionRequest,
} from '@/features/report/types/report'

export function createPostReport(
    boardSlug: string,
    postId: number,
    request: ReportCreateRequest,
    secretPassword?: string,
): Promise<ReportCreateResponse> {
    return sendReport(
        `/api/boards/${encodeURIComponent(boardSlug)}/posts/${postId}/reports`,
        request,
        secretPassword,
    )
}

export function createCommentReport(
    boardSlug: string,
    postId: number,
    commentId: number,
    request: ReportCreateRequest,
    secretPassword?: string,
): Promise<ReportCreateResponse> {
    return sendReport(
        `/api/boards/${encodeURIComponent(boardSlug)}/posts/${postId}/comments/${commentId}/reports`,
        request,
        secretPassword,
    )
}

function sendReport(
    url: string,
    request: ReportCreateRequest,
    secretPassword?: string,
): Promise<ReportCreateResponse> {
    const headers = new Headers()

    if (secretPassword !== undefined) {
        headers.set('X-Post-Password', secretPassword)
    }

    return apiRequest<ReportCreateResponse>(url, {
        method: 'POST',
        headers,
        body: JSON.stringify(request),
    })
}

export function getAdminReports(
    options: AdminReportPageOptions = {},
): Promise<AdminReportPageResponse> {
    const params = new URLSearchParams({
        filter: options.filter ?? 'PENDING',
        page: String(options.page ?? 1),
        size: String(options.size ?? 10),
    })

    return apiRequest<AdminReportPageResponse>(
        `/api/admin/reports?${params}`,
    )
}

export function getAdminReport(
    reportId: number,
): Promise<AdminReportDetailResponse> {
    return apiRequest<AdminReportDetailResponse>(
        `/api/admin/reports/${reportId}`,
    )
}

export function reviewReport(
    reportId: number,
    request: ReportReviewRequest,
): Promise<void> {
    return apiRequest<void>(`/api/admin/reports/${reportId}`, {
        method: 'PATCH',
        body: JSON.stringify(request),
    })
}

export function moderateReportTarget(
    reportId: number,
    request: ReportTargetActionRequest,
): Promise<void> {
    return apiRequest<void>(`/api/admin/reports/${reportId}/target`, {
        method: 'PATCH',
        body: JSON.stringify(request),
    })
}