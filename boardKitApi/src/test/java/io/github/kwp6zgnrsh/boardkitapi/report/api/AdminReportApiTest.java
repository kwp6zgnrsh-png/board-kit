package io.github.kwp6zgnrsh.boardkitapi.report.api;

import static io.github.kwp6zgnrsh.boardkitapi.support.TestAuthentication.createAuthentication;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.github.kwp6zgnrsh.boardkitapi.auth.mapper.RevokedTokenMapper;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtTokenProvider;
import io.github.kwp6zgnrsh.boardkitapi.config.MethodSecurityConfig;
import io.github.kwp6zgnrsh.boardkitapi.config.SecurityConfig;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportReviewRequest;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportStatusFilter;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportTargetAction;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportTargetActionRequest;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminReportDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminReportPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminReportSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.exception.ReportNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.report.exception.ReportReviewConflictException;
import io.github.kwp6zgnrsh.boardkitapi.report.exception.ReportTargetConflictException;
import io.github.kwp6zgnrsh.boardkitapi.report.model.ReportReason;
import io.github.kwp6zgnrsh.boardkitapi.report.model.ReportStatus;
import io.github.kwp6zgnrsh.boardkitapi.report.service.AdminReportService;

@WebMvcTest(AdminReportApi.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class})
class AdminReportApiTest {

	private static final String URL = "/api/admin/reports";
	private static final String ITEM_URL = URL + "/100";
	private static final String REVIEW_NOTE = "확인했습니다.";

	private static final String REVIEW_TEMPLATE = """
		{
		  "status": "%s",
		  "reviewNote": "확인했습니다."
		}
		""";

	private static final String TARGET_URL = ITEM_URL + "/target";
	private static final String TARGET_TEMPLATE = """
		{"action":"%s","version":4}
		""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AdminReportService adminReportService;
	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;
	@MockitoBean
	private RevokedTokenMapper revokedTokenMapper;
	@MockitoBean
	private MemberMapper memberMapper;

	@Test
	@DisplayName("기본 요청은 대기 신고의 첫 페이지를 10개씩 조회한다")
	void getPendingReportsWithDefaultPagination() throws Exception {
		var report = new AdminReportSummaryResponse(100L, 200L, 50L, "free", "자유게시판", "ARCHIVED", "게시글 제목", "신고자",
			ReportReason.ABUSE, ReportStatus.PENDING, true, true, LocalDateTime.of(2026, 10, 18, 12, 0));
		var response = new AdminReportPageResponse(List.of(report), 1, 10, 1L, 1);

		when(adminReportService.getReports(ReportStatusFilter.PENDING, 1, 10, 10L, true)).thenReturn(response);

		mockMvc.perform(get(URL).with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.reports[0].id").value(100))
			.andExpect(jsonPath("$.reports[0].commentId").value(50))
			.andExpect(jsonPath("$.reports[0].boardStatus").value("ARCHIVED"))
			.andExpect(jsonPath("$.reports[0].reason").value("ABUSE"))
			.andExpect(jsonPath("$.reports[0].status").value("PENDING"))
			.andExpect(jsonPath("$.reports[0].postDeleted").value(true))
			.andExpect(jsonPath("$.reports[0].commentDeleted").value(true))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.size").value(10))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.totalPages").value(1));

		verify(adminReportService).getReports(ReportStatusFilter.PENDING, 1, 10, 10L, true);
	}

	@ParameterizedTest
	@EnumSource(ReportStatusFilter.class)
	@DisplayName("선택한 신고 상태 필터와 페이지 조건을 서비스에 전달한다")
	void passFilterAndPaginationToService(ReportStatusFilter filter) throws Exception {
		when(adminReportService.getReports(filter, 2, 20, 10L, true)).thenReturn(
			new AdminReportPageResponse(List.of(), 2, 20, 0L, 0));

		mockMvc.perform(get(URL).param("filter", filter.name())
				.param("page", "2")
				.param("size", "20")
				.with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(2))
			.andExpect(jsonPath("$.size").value(20));

		verify(adminReportService).getReports(filter, 2, 20, 10L, true);
	}

	@Test
	@DisplayName("관리자는 신고 상세의 현재 원문과 삭제 상태를 조회한다")
	void getReportDetailWithAdminAuthentication() throws Exception {
		when(adminReportService.getReport(100L, 10L, true)).thenReturn(detailResponse());

		mockMvc.perform(get(ITEM_URL).with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.id").value(100))
			.andExpect(jsonPath("$.reporterId").value(20))
			.andExpect(jsonPath("$.authorId").value(30))
			.andExpect(jsonPath("$.content").value("현재 댓글"))
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.postDeleted").value(true))
			.andExpect(jsonPath("$.commentDeleted").value(true))
			.andExpect(jsonPath("$.postBlinded").value(false))
			.andExpect(jsonPath("$.commentBlinded").value(false))
			.andExpect(jsonPath("$.targetVersion").value(0));

		verify(adminReportService).getReport(100L, 10L, true);
	}

	@ParameterizedTest
	@EnumSource(value = ReportStatus.class, names = {"ACCEPTED", "REJECTED"})
	@DisplayName("관리자는 인정과 반려 결과 및 메모를 전달하고 204를 받는다")
	void reviewReportWithAdminAuthentication(ReportStatus reviewStatus) throws Exception {
		mockMvc.perform(patch(ITEM_URL).contentType(MediaType.APPLICATION_JSON)
			.content(REVIEW_TEMPLATE.formatted(reviewStatus.name()))
			.with(csrf())
			.with(authentication(createAuthentication(10L, "ROLE_ADMIN")))).andExpect(status().isNoContent());

		verify(adminReportService).reviewReport(100L, new ReportReviewRequest(reviewStatus, REVIEW_NOTE), 10L, true);
	}

	@Test
	@DisplayName("비로그인 사용자는 관리자 신고 조회와 처리를 사용할 수 없다")
	void rejectAllOperationsWithoutAuthentication() throws Exception {
		for (var request : adminRequests()) {
			mockMvc.perform(request.with(csrf())).andExpect(status().isUnauthorized());
		}

		verifyNoInteractions(adminReportService);
	}

	@Test
	@DisplayName("일반 회원은 관리자 신고 조회와 처리를 사용할 수 없다")
	void rejectAllOperationsByMember() throws Exception {
		for (var request : adminRequests()) {
			mockMvc.perform(request.with(csrf()).with(authentication(createAuthentication(10L, "ROLE_MEMBER"))))
				.andExpect(status().isForbidden());
		}

		verifyNoInteractions(adminReportService);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("CSRF 토큰이 없거나 잘못되면 신고 판정과 대상 조치를 거절한다")
	void rejectReportChangesWithoutValidCsrfToken(boolean invalidToken) throws Exception {
		var requests = List.of(
			patch(ITEM_URL).contentType(MediaType.APPLICATION_JSON).content(REVIEW_TEMPLATE.formatted("ACCEPTED")),
			patch(TARGET_URL).contentType(MediaType.APPLICATION_JSON).content(TARGET_TEMPLATE.formatted("BLIND")));

		for (var request : requests) {
			request.with(authentication(createAuthentication(10L, "ROLE_ADMIN")));
			if (invalidToken) {
				request.with(csrf().useInvalidToken());
			}

			mockMvc.perform(request).andExpect(status().isForbidden());
		}

		verifyNoInteractions(adminReportService);
	}

	@ParameterizedTest
	@CsvSource({"page, 0", "page, abc", "size, 0", "size, 101", "size, abc", "filter, INVALID"})
	@DisplayName("잘못된 목록 조회 조건은 서비스 호출 전에 거절한다")
	void rejectInvalidListParameters(String parameter, String value) throws Exception {
		mockMvc.perform(get(URL).param(parameter, value).with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(adminReportService);
	}

	@Test
	@DisplayName("신고 ID가 양수가 아니면 상세 조회와 처리를 거절한다")
	void rejectInvalidReportId() throws Exception {
		String url = URL + "/0";

		mockMvc.perform(get(url).with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isBadRequest());

		mockMvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON)
			.content(REVIEW_TEMPLATE.formatted("ACCEPTED"))
			.with(csrf())
			.with(authentication(createAuthentication(10L, "ROLE_ADMIN")))).andExpect(status().isBadRequest());

		mockMvc.perform(patch(url + "/target").contentType(MediaType.APPLICATION_JSON)
			.content(TARGET_TEMPLATE.formatted("BLIND"))
			.with(csrf())
			.with(authentication(createAuthentication(10L, "ROLE_ADMIN")))).andExpect(status().isBadRequest());

		verifyNoInteractions(adminReportService);
	}

	@ParameterizedTest
	@ValueSource(strings = {"{\"status\":null,\"reviewNote\":\"메모\"}",
		"{\"status\":\"PENDING\",\"reviewNote\":\"메모\"}", "{\"status\":\"INVALID\",\"reviewNote\":\"메모\"}",
		"{\"status\":\"REJECTED\",\"reviewNote\":\"   \"}"})
	@DisplayName("잘못된 처리 상태와 누락되거나 공백뿐인 메모를 거절한다")
	void rejectInvalidReviewRequest(String body) throws Exception {
		mockMvc.perform(patch(ITEM_URL).contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.with(csrf())
			.with(authentication(createAuthentication(10L, "ROLE_ADMIN")))).andExpect(status().isBadRequest());

		verifyNoInteractions(adminReportService);
	}

	@Test
	@DisplayName("처리 메모가 2,000자를 초과하면 서비스 호출 전에 거절한다")
	void rejectReviewNoteExceedingMaximumLength() throws Exception {
		String body = """
			{"status":"ACCEPTED","reviewNote":"%s"}
			""".formatted("가".repeat(2001));

		mockMvc.perform(patch(ITEM_URL).contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.with(csrf())
			.with(authentication(createAuthentication(10L, "ROLE_ADMIN")))).andExpect(status().isBadRequest());

		verifyNoInteractions(adminReportService);
	}

	@Test
	@DisplayName("없는 신고 상세는 404를 반환한다")
	void returnNotFoundForMissingReportDetail() throws Exception {
		when(adminReportService.getReport(100L, 10L, true)).thenThrow(new ReportNotFoundException());

		mockMvc.perform(get(ITEM_URL).with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("없는 신고를 처리하면 404를 반환한다")
	void returnNotFoundForMissingReportReview() throws Exception {
		doThrow(new ReportNotFoundException()).when(adminReportService)
			.reviewReport(100L, new ReportReviewRequest(ReportStatus.ACCEPTED, REVIEW_NOTE), 10L, true);

		mockMvc.perform(patch(ITEM_URL).contentType(MediaType.APPLICATION_JSON)
			.content(REVIEW_TEMPLATE.formatted("ACCEPTED"))
			.with(csrf())
			.with(authentication(createAuthentication(10L, "ROLE_ADMIN")))).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("이미 처리된 신고를 다시 처리하면 409를 반환한다")
	void returnConflictForAlreadyReviewedReport() throws Exception {
		doThrow(new ReportReviewConflictException()).when(adminReportService)
			.reviewReport(100L, new ReportReviewRequest(ReportStatus.ACCEPTED, REVIEW_NOTE), 10L, true);

		mockMvc.perform(patch(ITEM_URL).contentType(MediaType.APPLICATION_JSON)
			.content(REVIEW_TEMPLATE.formatted("ACCEPTED"))
			.with(csrf())
			.with(authentication(createAuthentication(10L, "ROLE_ADMIN")))).andExpect(status().isConflict());
	}

	@Test
	@DisplayName("활성 회원이 아닌 관리자의 요청은 401을 반환한다")
	void returnUnauthorizedForUnavailableAdmin() throws Exception {
		when(adminReportService.getReports(ReportStatusFilter.PENDING, 1, 10, 10L, true)).thenThrow(
			new MemberUnavailableException());

		mockMvc.perform(get(URL).with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@EnumSource(ReportTargetAction.class)
	@DisplayName("관리자의 대상 조치와 버전을 서비스에 전달하고 204를 반환한다")
	void moderateTargetWithAdminAuthentication(ReportTargetAction action) throws Exception {
		mockMvc.perform(patch(TARGET_URL).contentType(MediaType.APPLICATION_JSON)
				.content(TARGET_TEMPLATE.formatted(action.name()))
				.with(csrf())
				.with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		verify(adminReportService).moderateReportTarget(100L, new ReportTargetActionRequest(action, 4), 10L, true);
	}

	@ParameterizedTest
	@ValueSource(strings = {"{\"version\":4}", "{\"action\":\"INVALID\",\"version\":4}", "{\"action\":\"BLIND\"}",
		"{\"action\":\"BLIND\",\"version\":-1}"})
	@DisplayName("조치와 버전이 올바르지 않으면 서비스 호출 전에 거절한다")
	void rejectInvalidTargetActionRequest(String body) throws Exception {
		mockMvc.perform(patch(TARGET_URL).contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.with(csrf())
			.with(authentication(createAuthentication(10L, "ROLE_ADMIN")))).andExpect(status().isBadRequest());

		verifyNoInteractions(adminReportService);
	}

	@Test
	@DisplayName("대상 상태나 버전이 변경됐으면 409를 반환한다")
	void returnConflictWhenTargetStateHasChanged() throws Exception {
		doThrow(new ReportTargetConflictException()).when(adminReportService)
			.moderateReportTarget(100L, new ReportTargetActionRequest(ReportTargetAction.BLIND, 4), 10L, true);

		mockMvc.perform(patch(TARGET_URL).contentType(MediaType.APPLICATION_JSON)
			.content(TARGET_TEMPLATE.formatted("BLIND"))
			.with(csrf())
			.with(authentication(createAuthentication(10L, "ROLE_ADMIN")))).andExpect(status().isConflict());
	}

	private List<MockHttpServletRequestBuilder> adminRequests() {
		return List.of(get(URL), get(ITEM_URL),
			patch(ITEM_URL).contentType(MediaType.APPLICATION_JSON).content(REVIEW_TEMPLATE.formatted("ACCEPTED")),
			patch(TARGET_URL).contentType(MediaType.APPLICATION_JSON).content(TARGET_TEMPLATE.formatted("BLIND")));
	}

	private AdminReportDetailResponse detailResponse() {
		LocalDateTime time = LocalDateTime.of(2026, 10, 18, 12, 0);

		return new AdminReportDetailResponse(100L, 20L, "신고자", 200L, 50L, "free", "자유게시판", "ARCHIVED", "게시글 제목", 30L,
			"댓글 작성자", ReportReason.ABUSE, "욕설 신고", ReportStatus.PENDING, "현재 댓글", null, time.minusDays(1),
			time.plusHours(1), true, true, null, null, null, time, null, false, false, 0);
	}
}
