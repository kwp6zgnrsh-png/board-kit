package io.github.kwp6zgnrsh.boardkitapi.report.dto.response;

import java.util.List;

public record AdminBlindedPostPageResponse(
	List<AdminBlindedPostSummaryResponse> posts,
	int page,
	int size,
	long totalElements,
	int totalPages
) {
}