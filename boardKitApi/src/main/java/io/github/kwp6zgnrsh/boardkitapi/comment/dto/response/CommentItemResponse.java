package io.github.kwp6zgnrsh.boardkitapi.comment.dto.response;

import java.time.LocalDateTime;

public record CommentItemResponse(
	Long id,
	Long parentId,
	Long authorId,
	String authorDisplayName,
	String content,
	boolean deleted,
	Integer version,
	LocalDateTime createdAt,
	LocalDateTime updatedAt,
	long likeCount,
	boolean likedByMe
) {}