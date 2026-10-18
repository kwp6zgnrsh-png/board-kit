package io.github.kwp6zgnrsh.boardkitapi.post.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import io.github.kwp6zgnrsh.boardkitapi.file.dto.response.PostFileResponse;
import tools.jackson.databind.JsonNode;

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
	LocalDateTime updatedAt,
	Long representativeFileId,
	List<PostFileResponse> files,
	JsonNode contentDocument,
	long likeCount,
	boolean likedByMe
) {
}