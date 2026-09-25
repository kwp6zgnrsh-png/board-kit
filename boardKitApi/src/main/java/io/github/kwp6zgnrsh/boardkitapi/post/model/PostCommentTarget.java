package io.github.kwp6zgnrsh.boardkitapi.post.model;

public record PostCommentTarget(
	Long authorId,
	boolean secret,
	String secretPasswordHash,
	boolean commentsLocked
) {
}