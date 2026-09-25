import { apiRequest } from '@/api/http'
import type {
  BoardCreateRequest,
  BoardCreateResponse,
  BoardDetailResponse,
  BoardSummaryResponse,
  BoardAdminDetailResponse,
  BoardUpdateRequest,
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

export function getAdminBoard(
    boardSlug: string,
): Promise<BoardAdminDetailResponse> {
  return apiRequest<BoardAdminDetailResponse>(
      `/api/admin/boards/${encodeURIComponent(boardSlug)}`,
  )
}

export function updateBoard(
    boardSlug: string,
    request: BoardUpdateRequest,
): Promise<void> {
  return apiRequest<void>(
      `/api/admin/boards/${encodeURIComponent(boardSlug)}`,
      {
        method: 'PATCH',
        body: JSON.stringify(request),
      },
  )
}

export function archiveBoard(
    boardSlug: string,
): Promise<void> {
  return apiRequest<void>(
      `/api/admin/boards/${encodeURIComponent(boardSlug)}`,
      {
        method: 'DELETE',
      },
  )
}