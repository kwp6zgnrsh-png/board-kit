import { apiRequest } from '@/api/http'
import type {
  BoardCreateRequest,
  BoardCreateResponse,
  BoardDetailResponse,
  BoardSummaryResponse,
} from '@/features/board/types/board'

export function createBoard(
    request: BoardCreateRequest,
): Promise<BoardCreateResponse> {
  return apiRequest<BoardCreateResponse>('/api/admin/boards', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function getBoards(): Promise<BoardSummaryResponse[]> {
  return apiRequest<BoardSummaryResponse[]>('/api/boards')
}

export function getBoardDetail(
    boardSlug: string,
): Promise<BoardDetailResponse> {
  return apiRequest<BoardDetailResponse>(
      `/api/boards/${encodeURIComponent(boardSlug)}`,
  )
}