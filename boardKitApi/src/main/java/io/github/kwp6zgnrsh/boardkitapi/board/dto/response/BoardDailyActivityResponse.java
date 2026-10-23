package io.github.kwp6zgnrsh.boardkitapi.board.dto.response;

import java.time.LocalDate;

public record BoardDailyActivityResponse(
	LocalDate date,
	long postCount,
	long commentCount
) {}