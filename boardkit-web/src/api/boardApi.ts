import { apiRequest } from '@/api/http'
import type {
  BoardCreateRequest,
  BoardCreateResponse,
} from '@/features/board/types/board'

export function createBoard(
    request: BoardCreateRequest,
): Promise<BoardCreateResponse> {
  return apiRequest<BoardCreateResponse>('/api/admin/boards', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}