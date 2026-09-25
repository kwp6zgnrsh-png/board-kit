package io.github.kwp6zgnrsh.boardkitapi.comment.dto.response;

import java.util.List;

public record CommentPageResponse(
	List<CommentItemResponse> comments,
	int page,
	int size,
	long totalElements,
	int totalPages
) {}