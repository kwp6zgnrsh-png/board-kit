package io.github.kwp6zgnrsh.boardkitapi.board.dto.response;

public record BoardCategoryResponse(
	Long id,
	String name,
	Integer sortOrder
) {
}
