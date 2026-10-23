package io.github.kwp6zgnrsh.boardkitapi.board.dto.response;

public record BoardActivityResponse(
	Long boardId,
	String boardName,
	String boardSlug,
	long postCount,
	long commentCount
) {}