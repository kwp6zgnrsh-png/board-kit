package io.github.kwp6zgnrsh.boardkitapi.board.dto.response;

public record BoardCreateResponse(
	Long id,
	String name,
	String slug
) {
}