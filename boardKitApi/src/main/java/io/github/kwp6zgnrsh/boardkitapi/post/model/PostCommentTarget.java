package io.github.kwp6zgnrsh.boardkitapi.post.model;

import java.time.LocalDateTime;

public record PostCommentTarget(
	Long authorId,
	boolean secret,
	String secretPasswordHash,
	boolean commentsLocked,
	LocalDateTime blindedAt
) {
}