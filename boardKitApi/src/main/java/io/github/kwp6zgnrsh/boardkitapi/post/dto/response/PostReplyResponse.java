package io.github.kwp6zgnrsh.boardkitapi.post.dto.response;

import java.time.LocalDateTime;

import tools.jackson.databind.JsonNode;

public record PostReplyResponse(
	Long id,
	Long postId,
	Long authorId,
	String authorDisplayName,
	String content,
	JsonNode contentDocument,
	Integer version,
	LocalDateTime createdAt,
	LocalDateTime updatedAt
) {
}