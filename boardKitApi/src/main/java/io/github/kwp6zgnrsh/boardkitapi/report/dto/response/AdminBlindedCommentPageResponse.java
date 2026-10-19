package io.github.kwp6zgnrsh.boardkitapi.report.dto.response;

import java.util.List;

public record AdminBlindedCommentPageResponse(
	List<AdminBlindedCommentSummaryResponse> comments,
	int page,
	int size,
	long totalElements,
	int totalPages
) {
}