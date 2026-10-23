export type BoardStatisticsDays = 7 | 30 | 90

export interface BoardStatisticsOptions {
    boardId?: number
    days?: BoardStatisticsDays
}

export interface BoardStatisticsSummaryResponse {
    currentPostCount: number
    currentCommentCount: number
    todayPostCount: number
    todayCommentCount: number
}

export interface BoardDailyActivityResponse {
    date: string
    postCount: number
    commentCount: number
}

export interface BoardActivityResponse {
    boardId: number
    boardName: string
    boardSlug: string
    postCount: number
    commentCount: number
}

export interface BoardStatisticsResponse {
    boardId: number | null
    days: BoardStatisticsDays
    startDate: string
    endDate: string
    summary: BoardStatisticsSummaryResponse
    dailyActivities: BoardDailyActivityResponse[]
    boardActivities: BoardActivityResponse[]
}