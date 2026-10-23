import {apiRequest} from '@/api/http'
import type {
    BoardStatisticsOptions,
    BoardStatisticsResponse,
} from '@/features/board/types/boardStatistics'

export function getAdminBoardStatistics(
    options: BoardStatisticsOptions = {},
): Promise<BoardStatisticsResponse> {
    const params = new URLSearchParams({
        days: String(options.days ?? 30),
    })

    if (options.boardId !== undefined) {
        params.set('boardId', String(options.boardId))
    }

    return apiRequest<BoardStatisticsResponse>(
        `/api/admin/board-statistics?${params}`,
    )
}