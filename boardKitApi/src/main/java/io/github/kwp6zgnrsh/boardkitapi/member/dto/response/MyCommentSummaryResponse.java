package io.github.kwp6zgnrsh.boardkitapi.member.dto.response;

import java.time.LocalDateTime;

public record MyCommentSummaryResponse(
	Long id,
	Long postId,
	Long parentId,
	String boardSlug,
	String boardName,
	String boardStatus,
	boolean postDeleted,
	String content,
	LocalDateTime createdAt
) {}