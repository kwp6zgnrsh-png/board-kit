package io.github.kwp6zgnrsh.boardkitapi.board.api;

import static io.github.kwp6zgnrsh.boardkitapi.support.TestAuthentication.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.auth.mapper.RevokedTokenMapper;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtTokenProvider;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardActivityResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardDailyActivityResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardStatisticsResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardStatisticsSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.exception.BoardNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.board.service.BoardStatisticsService;
import io.github.kwp6zgnrsh.boardkitapi.config.MethodSecurityConfig;
import io.github.kwp6zgnrsh.boardkitapi.config.SecurityConfig;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;

@WebMvcTest(AdminBoardStatisticsApi.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class})
class AdminBoardStatisticsApiTest {

	private static final String URL = "/api/admin/board-statistics";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BoardStatisticsService statisticsService;

	// 실제 SecurityConfig 구성에 필요한 의존성이다.
	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private RevokedTokenMapper revokedTokenMapper;

	@MockitoBean
	private MemberMapper memberMapper;

	@Test
	@DisplayName("관리자는 기본 전체 게시판의 30일 통계를 조회한다")
	void getStatisticsWithDefaultParameters() throws Exception {
		when(statisticsService.getStatistics(null, 30, 10L, true)).thenReturn(response(null, 30));

		mockMvc.perform(get(URL).param("adminId", "999").with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.days").value(30))
			.andExpect(jsonPath("$.startDate").value("2026-09-25"))
			.andExpect(jsonPath("$.endDate").value("2026-10-24"))
			.andExpect(jsonPath("$.summary.currentPostCount").value(10))
			.andExpect(jsonPath("$.summary.currentCommentCount").value(20))
			.andExpect(jsonPath("$.summary.todayPostCount").value(2))
			.andExpect(jsonPath("$.summary.todayCommentCount").value(4))
			.andExpect(jsonPath("$.dailyActivities.length()").value(30))
			.andExpect(jsonPath("$.dailyActivities[0].date").value("2026-09-25"))
			.andExpect(jsonPath("$.dailyActivities[29].postCount").value(2))
			.andExpect(jsonPath("$.boardActivities[0].boardId").value(1))
			.andExpect(jsonPath("$.boardActivities[0].boardName").value("자유게시판"))
			.andExpect(jsonPath("$.boardActivities[0].postCount").value(2))
			.andExpect(jsonPath("$.boardActivities[0].commentCount").value(4));

		verify(statisticsService).getStatistics(null, 30, 10L, true);
	}

	@Test
	@DisplayName("관리자는 게시판과 조회 기간을 지정할 수 있다")
	void getStatisticsWithSelectedBoardAndPeriod() throws Exception {
		when(statisticsService.getStatistics(1L, 7, 10L, true)).thenReturn(response(1L, 7));

		mockMvc.perform(get(URL).param("boardId", "1")
				.param("days", "7")
				.with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.boardId").value(1))
			.andExpect(jsonPath("$.days").value(7))
			.andExpect(jsonPath("$.startDate").value("2026-10-18"))
			.andExpect(jsonPath("$.dailyActivities.length()").value(7));

		verify(statisticsService).getStatistics(1L, 7, 10L, true);
	}

	@Test
	@DisplayName("비로그인 사용자는 통계를 조회할 수 없다")
	void rejectStatisticsWithoutAuthentication() throws Exception {
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());

		verifyNoInteractions(statisticsService);
	}

	@Test
	@DisplayName("일반 회원은 통계를 조회할 수 없다")
	void rejectStatisticsByMember() throws Exception {
		mockMvc.perform(get(URL).with(authentication(createAuthentication(10L, "ROLE_MEMBER"))))
			.andExpect(status().isForbidden());

		verifyNoInteractions(statisticsService);
	}

	@ParameterizedTest
	@CsvSource({"boardId, 0", "boardId, abc", "days, abc"})
	@DisplayName("잘못된 파라미터는 서비스 호출 전에 거절한다")
	void rejectInvalidParameters(String parameter, String value) throws Exception {
		mockMvc.perform(get(URL).param(parameter, value).with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(statisticsService);
	}

	@Test
	@DisplayName("지원하지 않는 기간에 대한 서비스 오류를 400으로 반환한다")
	void returnBadRequestForUnsupportedPeriod() throws Exception {
		when(statisticsService.getStatistics(null, 14, 10L, true)).thenThrow(
			new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 기간은 7일, 30일, 90일만 선택할 수 있습니다."));

		mockMvc.perform(get(URL).param("days", "14").with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isBadRequest());

		verify(statisticsService).getStatistics(null, 14, 10L, true);
	}

	@Test
	@DisplayName("통계 대상에 없는 게시판은 404로 반환한다")
	void returnNotFoundForUnavailableBoard() throws Exception {
		when(statisticsService.getStatistics(2L, 30, 10L, true)).thenThrow(new BoardNotFoundException());

		mockMvc.perform(get(URL).param("boardId", "2").with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isNotFound());

		verify(statisticsService).getStatistics(2L, 30, 10L, true);
	}

	private BoardStatisticsResponse response(Long boardId, int days) {
		LocalDate today = LocalDate.of(2026, 10, 24);
		LocalDate startDate = today.minusDays(days - 1L);

		List<BoardDailyActivityResponse> dailyActivities = IntStream.range(0, days)
			.mapToObj(index -> new BoardDailyActivityResponse(startDate.plusDays(index), index == days - 1 ? 2 : 0,
				index == days - 1 ? 4 : 0))
			.toList();

		return new BoardStatisticsResponse(boardId, days, startDate, today,
			new BoardStatisticsSummaryResponse(10, 20, 2, 4), dailyActivities,
			List.of(new BoardActivityResponse(1L, "자유게시판", "free", 2, 4)));
	}
}