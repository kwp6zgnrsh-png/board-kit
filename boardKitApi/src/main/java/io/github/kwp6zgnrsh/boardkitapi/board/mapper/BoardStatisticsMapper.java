package io.github.kwp6zgnrsh.boardkitapi.board.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardActivityResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardDailyActivityResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardStatisticsSummaryResponse;

@Mapper
public interface BoardStatisticsMapper {

	BoardStatisticsSummaryResponse selectSummary(
		@Param("boardId") Long boardId,
		@Param("todayStartUtc") LocalDateTime todayStartUtc,
		@Param("tomorrowStartUtc") LocalDateTime tomorrowStartUtc
	);

	List<BoardDailyActivityResponse> selectDailyActivities(
		@Param("boardId") Long boardId,
		@Param("startUtc") LocalDateTime startUtc,
		@Param("endUtc") LocalDateTime endUtc
	);

	List<BoardActivityResponse> selectBoardActivities(
		@Param("startUtc") LocalDateTime startUtc,
		@Param("endUtc") LocalDateTime endUtc
	);

}