package io.github.kwp6zgnrsh.boardkitapi.report.dto.response;

import java.time.LocalDateTime;

public record AdminBlindedCommentSummaryResponse(
	Long commentId,
	Long parentId,
	Long postId,
	String boardSlug,
	String boardName,
	String boardStatus,
	String postTitle,
	String contentPreview,
	Long authorId,
	String authorDisplayName,
	LocalDateTime blindedAt,
	Integer version
) {
}