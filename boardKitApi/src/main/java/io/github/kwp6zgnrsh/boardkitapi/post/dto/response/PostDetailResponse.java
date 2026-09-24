package io.github.kwp6zgnrsh.boardkitapi.post.dto.response;

import java.time.LocalDateTime;

public record PostDetailResponse(
	Long id,
	Long boardId,
	String title,
	String content,
	Long authorId,
	String authorDisplayName,
	Long categoryId,
	String categoryName,
	boolean secret,
	boolean pinned,
	boolean commentsLocked,
	long viewCount,
	Integer version,
	LocalDateTime createdAt,
	LocalDateTime updatedAt
) {
}