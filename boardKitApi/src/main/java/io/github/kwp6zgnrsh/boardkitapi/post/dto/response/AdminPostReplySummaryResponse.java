package io.github.kwp6zgnrsh.boardkitapi.post.dto.response;

import java.time.LocalDateTime;

public record AdminPostReplySummaryResponse(
	Long postId,
	String boardSlug,
	String boardName,
	String title,
	String authorDisplayName,
	boolean secret,
	LocalDateTime createdAt,
	Long replyId,
	boolean officialReplyEnabled
) {
}