package io.github.kwp6zgnrsh.boardkitapi.report.dto.response;

import java.time.LocalDateTime;

public record AdminBlindedPostSummaryResponse(
	Long postId,
	String boardSlug,
	String boardName,
	String boardStatus,
	String title,
	Long authorId,
	String authorDisplayName,
	LocalDateTime blindedAt,
	Integer version
) {
}