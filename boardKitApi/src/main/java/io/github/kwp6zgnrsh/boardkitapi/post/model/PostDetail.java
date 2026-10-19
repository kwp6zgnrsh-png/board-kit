package io.github.kwp6zgnrsh.boardkitapi.post.model;

import java.time.LocalDateTime;

import tools.jackson.databind.JsonNode;

public record PostDetail(
	Long id,
	Long boardId,
	String title,
	String content,
	Long authorId,
	String authorDisplayName,
	Long categoryId,
	String categoryName,
	boolean secret,
	String secretPasswordHash,
	boolean pinned,
	boolean commentsLocked,
	long viewCount,
	Integer version,
	LocalDateTime createdAt,
	LocalDateTime updatedAt,
	Long representativeFileId,
	JsonNode contentDocument,
	LocalDateTime blindedAt
) {
}