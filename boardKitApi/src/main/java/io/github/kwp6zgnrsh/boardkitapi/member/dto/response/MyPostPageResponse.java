package io.github.kwp6zgnrsh.boardkitapi.member.dto.response;

import java.util.List;

public record MyPostPageResponse(
	List<MyPostSummaryResponse> posts,
	int page,
	int size,
	long totalElements,
	int totalPages
) {}