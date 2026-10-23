package io.github.kwp6zgnrsh.boardkitapi.board.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardActivityResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardDailyActivityResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardStatisticsResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardStatisticsSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.exception.BoardNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.board.mapper.BoardStatisticsMapper;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoardStatisticsService {

	private static final ZoneId STATISTICS_ZONE = ZoneId.of("Asia/Seoul");

	private final BoardStatisticsMapper statisticsMapper;
	private final MemberMapper memberMapper;
	private final Clock clock;

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public BoardStatisticsResponse getStatistics(Long boardId, int days, Long adminId, boolean adminAccess) {
		if (!adminAccess) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
		}
		if (adminId == null || !memberMapper.existsActiveMemberById(adminId)) {
			throw new MemberUnavailableException();
		}
		if (days != 7 && days != 30 && days != 90) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 기간은 7일, 30일, 90일만 선택할 수 있습니다.");
		}
		if (boardId != null && boardId <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "게시판 정보를 확인해 주세요.");
		}

		LocalDate today = LocalDate.now(clock.withZone(STATISTICS_ZONE));
		LocalDate startDate = today.minusDays(days - 1L);

		LocalDateTime startUtc = startDate.atStartOfDay(STATISTICS_ZONE)
			.withZoneSameInstant(ZoneOffset.UTC)
			.toLocalDateTime();

		LocalDateTime todayStartUtc = today.atStartOfDay(STATISTICS_ZONE)
			.withZoneSameInstant(ZoneOffset.UTC)
			.toLocalDateTime();

		LocalDateTime endUtc = today.plusDays(1)
			.atStartOfDay(STATISTICS_ZONE)
			.withZoneSameInstant(ZoneOffset.UTC)
			.toLocalDateTime();

		List<BoardActivityResponse> boardActivities = statisticsMapper.selectBoardActivities(startUtc, endUtc);

		if (boardId != null && boardActivities.stream().noneMatch(board -> boardId.equals(board.boardId()))) {
			throw new BoardNotFoundException();
		}

		BoardStatisticsSummaryResponse summary = statisticsMapper.selectSummary(boardId, todayStartUtc, endUtc);

		Map<LocalDate, BoardDailyActivityResponse> activitiesByDate = statisticsMapper.selectDailyActivities(boardId,
				startUtc, endUtc)
			.stream()
			.collect(Collectors.toMap(BoardDailyActivityResponse::date, activity -> activity));

		List<BoardDailyActivityResponse> dailyActivities = new ArrayList<>(days);
		for (int i = 0; i < days; i++) {
			LocalDate date = startDate.plusDays(i);
			dailyActivities.add(activitiesByDate.getOrDefault(date, new BoardDailyActivityResponse(date, 0, 0)));
		}

		return new BoardStatisticsResponse(boardId, days, startDate, today, summary, List.copyOf(dailyActivities),
			boardActivities);
	}
}