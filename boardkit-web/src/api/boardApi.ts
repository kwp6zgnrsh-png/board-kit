import type {
  BoardCreateRequest,
  BoardCreateResponse,
} from '@/features/board/types/board'

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message)
  }
}

export async function createBoard(
  request: BoardCreateRequest,
): Promise<BoardCreateResponse> {
  const response = await fetch('/api/admin/boards', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    credentials: 'include',
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    const message =
      body?.message ?? `게시판 생성 요청에 실패했습니다. (${response.status})`

    throw new ApiError(response.status, message)
  }

  return response.json() as Promise<BoardCreateResponse>
}
