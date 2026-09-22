import type { BoardSummaryResponse } from '@/features/board/types/board'
import type {
  PostPageResponse,
  PostSummaryResponse,
} from '@/features/post/types/post'

export interface HomeBoardPreview {
  board: BoardSummaryResponse
  postPage: PostPageResponse
}

export interface HomeRecentPost {
  board: BoardSummaryResponse
  post: PostSummaryResponse
}
