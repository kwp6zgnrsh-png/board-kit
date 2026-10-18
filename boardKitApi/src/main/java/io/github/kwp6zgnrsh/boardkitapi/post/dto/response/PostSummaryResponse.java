package io.github.kwp6zgnrsh.boardkitapi.post.dto.response;

import java.time.LocalDateTime;

public record PostSummaryResponse(
	Long id,
	String title,
	Long authorId,
	String authorDisplayName,
	Long categoryId,
	String categoryName,
	boolean secret,
	boolean pinned,
	long viewCount,
	long commentCount,
	LocalDateTime createdAt,
	Long representativeFileId,
	boolean thumbnailAvailable,
	long likeCount
) {
}