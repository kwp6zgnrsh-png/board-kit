package io.github.kwp6zgnrsh.boardkitapi.board.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardActivityResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardDailyActivityResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardStatisticsResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardStatisticsSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.exception.BoardNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.board.mapper.BoardStatisticsMapper;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;

@ExtendWith(MockitoExtension.class)
class BoardStatisticsServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);
	private static final LocalDateTime TODAY_START = LocalDateTime.of(2026, 10, 23, 15, 0);
	private static final LocalDateTime END = LocalDateTime.of(2026, 10, 24, 15, 0);

	private static final List<BoardActivityResponse> BOARDS = List.of(
		new BoardActivityResponse(1L, "자유게시판", "free", 3, 7));

	@Mock
	private BoardStatisticsMapper statisticsMapper;

	@Mock
	private MemberMapper memberMapper;

	private BoardStatisticsService service;

	@BeforeEach
	void setUp() {
		// UTC는 23일이지만 한국은 24일 자정이다.
		Clock clock = Clock.fixed(Instant.parse("2026-10-23T15:00:00Z"), ZoneOffset.UTC);
		service = new BoardStatisticsService(statisticsMapper, memberMapper, clock);
	}

	@ParameterizedTest
	@CsvSource({"7, 2026-10-18, 2026-10-17T15:00:00", "30, 2026-09-25, 2026-09-24T15:00:00",
		"90, 2026-07-27, 2026-07-26T15:00:00"})
	@DisplayName("한국 날짜로 조회 기간을 계산하고 빠진 날짜를 0건으로 채운다")
	void calculatePeriodAndFillMissingDates(int days, LocalDate startDate, LocalDateTime startUtc) {
		allowAdmin();

		BoardStatisticsSummaryResponse summary = new BoardStatisticsSummaryResponse(10, 20, 1, 3);

		when(statisticsMapper.selectBoardActivities(startUtc, END)).thenReturn(BOARDS);
		when(statisticsMapper.selectSummary(null, TODAY_START, END)).thenReturn(summary);
		when(statisticsMapper.selectDailyActivities(null, startUtc, END)).thenReturn(
			List.of(new BoardDailyActivityResponse(startDate, 2, 4), new BoardDailyActivityResponse(TODAY, 1, 3)));

		BoardStatisticsResponse response = service.getStatistics(null, days, 10L, true);

		assertAll(() -> assertNull(response.boardId()), () -> assertEquals(days, response.days()),
			() -> assertEquals(startDate, response.startDate()), () -> assertEquals(TODAY, response.endDate()),
			() -> assertEquals(summary, response.summary()), () -> assertEquals(BOARDS, response.boardActivities()),
			() -> assertEquals(days, response.dailyActivities().size()),
			() -> assertEquals(new BoardDailyActivityResponse(startDate, 2, 4), response.dailyActivities().getFirst()),
			() -> assertEquals(new BoardDailyActivityResponse(startDate.plusDays(1), 0, 0),
				response.dailyActivities().get(1)),
			() -> assertEquals(new BoardDailyActivityResponse(TODAY, 1, 3), response.dailyActivities().getLast()));
	}

	@Test
	@DisplayName("선택한 게시판을 현황과 추이 조회에 전달한다")
	void getStatisticsForSelectedBoard() {
		allowAdmin();

		when(statisticsMapper.selectBoardActivities(any(), any())).thenReturn(BOARDS);
		when(statisticsMapper.selectSummary(1L, TODAY_START, END)).thenReturn(
			new BoardStatisticsSummaryResponse(3, 7, 3, 7));
		when(statisticsMapper.selectDailyActivities(eq(1L), any(), any())).thenReturn(
			List.of(new BoardDailyActivityResponse(TODAY, 3, 7)));

		BoardStatisticsResponse response = service.getStatistics(1L, 7, 10L, true);

		assertEquals(1L, response.boardId());
		assertEquals(BOARDS, response.boardActivities());

		verify(statisticsMapper).selectSummary(1L, TODAY_START, END);
		verify(statisticsMapper).selectDailyActivities(1L, LocalDateTime.of(2026, 10, 17, 15, 0), END);
	}

	@Test
	@DisplayName("지원하지 않는 조회 기간은 거절한다")
	void rejectUnsupportedPeriod() {
		allowAdmin();

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
			() -> service.getStatistics(null, 14, 10L, true));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
		verifyNoInteractions(statisticsMapper);
	}

	@Test
	@DisplayName("양수가 아닌 게시판 ID는 거절한다")
	void rejectInvalidBoardId() {
		allowAdmin();

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
			() -> service.getStatistics(0L, 7, 10L, true));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
		verifyNoInteractions(statisticsMapper);
	}

	@Test
	@DisplayName("통계 대상에 없는 게시판을 선택하면 거절한다")
	void rejectBoardOutsideStatisticsScope() {
		allowAdmin();

		when(statisticsMapper.selectBoardActivities(any(), any())).thenReturn(BOARDS);

		assertThrows(BoardNotFoundException.class, () -> service.getStatistics(2L, 7, 10L, true));

		verify(statisticsMapper, never()).selectSummary(any(), any(), any());
		verify(statisticsMapper, never()).selectDailyActivities(any(), any(), any());
	}

	@Test
	@DisplayName("관리자가 아니면 통계 조회를 거절한다")
	void rejectStatisticsForNonAdmin() {
		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
			() -> service.getStatistics(null, 7, 10L, false));

		assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
		verifyNoInteractions(memberMapper, statisticsMapper);
	}

	@Test
	@DisplayName("탈퇴한 관리자는 통계를 조회할 수 없다")
	void rejectStatisticsForUnavailableAdmin() {
		assertThrows(MemberUnavailableException.class, () -> service.getStatistics(null, 7, 10L, true));

		verify(memberMapper).existsActiveMemberById(10L);
		verifyNoInteractions(statisticsMapper);
	}

	private void allowAdmin() {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);
	}
}