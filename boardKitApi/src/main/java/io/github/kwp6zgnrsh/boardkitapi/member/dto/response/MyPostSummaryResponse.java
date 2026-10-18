package io.github.kwp6zgnrsh.boardkitapi.member.dto.response;

import java.time.LocalDateTime;

public record MyPostSummaryResponse(
	Long id,
	String boardSlug,
	String boardName,
	String boardStatus,
	String title,
	boolean secret,
	long viewCount,
	LocalDateTime createdAt
) {}