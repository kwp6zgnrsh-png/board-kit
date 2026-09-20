package io.github.kwp6zgnrsh.boardkitapi.board.dto.response;

import java.util.List;

public record BoardDetailResponse(
	Long id,
	String name,
	String slug,
	String description,
	String displayType,
	Integer sortOrder,
	BoardPolicyResponse policy,
	List<BoardCategoryResponse> categories
) {
}