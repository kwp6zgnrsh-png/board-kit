package io.github.kwp6zgnrsh.boardkitapi.board.dto.response;

public record BoardSummaryResponse(
	Long id,
	String name,
	String slug,
	String description,
	String displayType,
	Integer sortOrder
) {
}