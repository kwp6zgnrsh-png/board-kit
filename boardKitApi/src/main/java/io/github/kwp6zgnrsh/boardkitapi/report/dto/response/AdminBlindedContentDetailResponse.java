package io.github.kwp6zgnrsh.boardkitapi.report.dto.response;

import java.time.LocalDateTime;

import tools.jackson.databind.JsonNode;

public record AdminBlindedContentDetailResponse(
	Long postId,
	Long commentId,
	Long parentId,
	String boardSlug,
	String boardName,
	String boardStatus,
	String postTitle,
	Long authorId,
	String authorDisplayName,
	String content,
	JsonNode contentDocument,
	LocalDateTime createdAt,
	LocalDateTime updatedAt,
	LocalDateTime blindedAt,
	Integer version
) {
}