package io.github.kwp6zgnrsh.boardkitapi.board.dto.response;

public record BoardStatisticsSummaryResponse(
	long currentPostCount,
	long currentCommentCount,
	long todayPostCount,
	long todayCommentCount
) {}