package io.github.kwp6zgnrsh.boardkitapi.member.dto.response;

import java.util.List;

public record MyCommentPageResponse(
	List<MyCommentSummaryResponse> comments,
	int page,
	int size,
	long totalElements,
	int totalPages
) {}