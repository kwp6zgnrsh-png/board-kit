package io.github.kwp6zgnrsh.boardkitapi.board.dto.response;

import java.time.LocalDate;
import java.util.List;

public record BoardStatisticsResponse(
	Long boardId,
	int days,
	LocalDate startDate,
	LocalDate endDate,
	BoardStatisticsSummaryResponse summary,
	List<BoardDailyActivityResponse> dailyActivities,
	List<BoardActivityResponse> boardActivities
) {}