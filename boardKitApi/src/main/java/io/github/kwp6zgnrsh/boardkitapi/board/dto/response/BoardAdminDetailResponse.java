package io.github.kwp6zgnrsh.boardkitapi.board.dto.response;

import java.util.List;

public record BoardAdminDetailResponse(
	Long id,
	String name,
	String slug,
	String description,
	String displayType,
	Integer sortOrder,
	Integer version,
	BoardPolicyResponse policy,
	List<BoardCategoryResponse> categories
) {
}
