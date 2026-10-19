package io.github.kwp6zgnrsh.boardkitapi.report.api;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.auth.mapper.RevokedTokenMapper;
import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtTokenProvider;
import io.github.kwp6zgnrsh.boardkitapi.board.exception.BoardNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.comment.exception.CommentNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.config.SecurityConfig;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.SecretPostAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.report.exception.DuplicateReportException;
import io.github.kwp6zgnrsh.boardkitapi.report.model.ReportReason;
import io.github.kwp6zgnrsh.boardkitapi.report.service.ReportService;

@WebMvcTest(ReportApi.class)
@Import(SecurityConfig.class)
class ReportApiTest {

	private static final String POST_URL = "/api/boards/free/posts/100/reports";
	private static final String COMMENT_URL = "/api/boards/free/posts/100/comments/50/reports";

	private static final String VALID_REQUEST = """
		{
		  "reason": "SPAM",
		  "detail": "광고가 반복됩니다."
		}
		""";

	private static final ReportCreateRequest REQUEST = new ReportCreateRequest(ReportReason.SPAM, "광고가 반복됩니다.");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ReportService reportService;
	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;
	@MockitoBean
	private RevokedTokenMapper revokedTokenMapper;
	@MockitoBean
	private MemberMapper memberMapper;

	@Test
	@DisplayName("회원은 게시글을 신고하고 생성된 신고 ID를 받는다")
	void createPostReportWithMemberAuthentication() throws Exception {
		when(reportService.createPostReport("free", 100L, REQUEST, 10L, false, "secret")).thenReturn(300L);

		mockMvc.perform(post(POST_URL).contentType(MediaType.APPLICATION_JSON)
				.content(VALID_REQUEST)
				.header("X-Post-Password", "secret")
				.with(csrf())
				.with(authentication(createAuthentication("ROLE_MEMBER"))))
			.andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.id").value(300));

		verify(reportService).createPostReport("free", 100L, REQUEST, 10L, false, "secret");
	}

	@Test
	@DisplayName("회원의 댓글 신고 요청에 소속과 비밀글 비밀번호를 전달한다")
	void createCommentReportWithMemberAuthentication() throws Exception {
		when(reportService.createCommentReport("free", 100L, 50L, REQUEST, 10L, false, "secret")).thenReturn(301L);

		mockMvc.perform(post(COMMENT_URL).contentType(MediaType.APPLICATION_JSON)
				.content(VALID_REQUEST)
				.header("X-Post-Password", "secret")
				.with(csrf())
				.with(authentication(createAuthentication("ROLE_MEMBER"))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(301));

		verify(reportService).createCommentReport("free", 100L, 50L, REQUEST, 10L, false, "secret");
	}

	@Test
	@DisplayName("관리자의 게시글과 댓글 신고에는 관리자 권한을 전달한다")
	void passAdminAuthorityToReportService() throws Exception {
		when(reportService.createPostReport("free", 100L, REQUEST, 10L, true, null)).thenReturn(300L);
		when(reportService.createCommentReport("free", 100L, 50L, REQUEST, 10L, true, null)).thenReturn(301L);

		for (String url : List.of(POST_URL, COMMENT_URL)) {
			mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
				.content(VALID_REQUEST)
				.with(csrf())
				.with(authentication(createAuthentication("ROLE_ADMIN")))).andExpect(status().isCreated());
		}

		verify(reportService).createPostReport("free", 100L, REQUEST, 10L, true, null);
		verify(reportService).createCommentReport("free", 100L, 50L, REQUEST, 10L, true, null);
	}

	@ParameterizedTest
	@ValueSource(strings = {POST_URL, COMMENT_URL})
	@DisplayName("비로그인 사용자는 신고할 수 없다")
	void rejectReportWithoutAuthentication(String url) throws Exception {
		mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST).with(csrf()))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(reportService);
	}

	@ParameterizedTest
	@ValueSource(strings = {POST_URL, COMMENT_URL})
	@DisplayName("CSRF 토큰이 없으면 신고를 거절한다")
	void rejectReportWithoutCsrfToken(String url) throws Exception {
		mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
			.content(VALID_REQUEST)
			.with(authentication(createAuthentication("ROLE_MEMBER")))).andExpect(status().isForbidden());

		verifyNoInteractions(reportService);
	}

	@ParameterizedTest
	@ValueSource(strings = {POST_URL, COMMENT_URL})
	@DisplayName("잘못된 CSRF 토큰으로 신고할 수 없다")
	void rejectReportWithInvalidCsrfToken(String url) throws Exception {
		mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
			.content(VALID_REQUEST)
			.with(csrf().useInvalidToken())
			.with(authentication(createAuthentication("ROLE_MEMBER")))).andExpect(status().isForbidden());

		verifyNoInteractions(reportService);
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"reason\":null}", "{\"reason\":\"INVALID\"}", "{\"reason\":\"OTHER\"}",
		"{\"reason\":\"OTHER\",\"detail\":\"   \"}"})
	@DisplayName("잘못된 신고 사유와 누락된 기타 상세 사유를 거절한다")
	void rejectInvalidReportRequest(String body) throws Exception {
		for (String url : List.of(POST_URL, COMMENT_URL)) {
			mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
				.content(body)
				.with(csrf())
				.with(authentication(createAuthentication("ROLE_MEMBER")))).andExpect(status().isBadRequest());
		}

		verifyNoInteractions(reportService);
	}

	@Test
	@DisplayName("상세 사유가 2,000자를 초과하면 서비스 호출 전에 거절한다")
	void rejectDetailExceedingMaximumLength() throws Exception {
		String body = """
			{"reason":"SPAM","detail":"%s"}
			""".formatted("가".repeat(2001));

		for (String url : List.of(POST_URL, COMMENT_URL)) {
			mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
				.content(body)
				.with(csrf())
				.with(authentication(createAuthentication("ROLE_MEMBER")))).andExpect(status().isBadRequest());
		}

		verifyNoInteractions(reportService);
	}

	@ParameterizedTest
	@ValueSource(strings = {"/api/boards/free/posts/0/reports", "/api/boards/free/posts/-1/reports",
		"/api/boards/free/posts/0/comments/50/reports", "/api/boards/free/posts/-1/comments/50/reports",
		"/api/boards/free/posts/100/comments/0/reports", "/api/boards/free/posts/100/comments/-1/reports"})
	@DisplayName("게시글과 댓글 ID가 양수가 아니면 신고를 거절한다")
	void rejectReportWithInvalidTargetId(String url) throws Exception {
		mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
			.content(VALID_REQUEST)
			.with(csrf())
			.with(authentication(createAuthentication("ROLE_MEMBER")))).andExpect(status().isBadRequest());

		verifyNoInteractions(reportService);
	}

	@ParameterizedTest
	@MethodSource("serviceFailures")
	@DisplayName("신고 서비스의 인증 권한 대상 중복 오류를 HTTP 상태로 반환한다")
	void returnStatusForServiceFailure(String url, RuntimeException failure, int expectedStatus) throws Exception {
		if (POST_URL.equals(url)) {
			when(reportService.createPostReport("free", 100L, REQUEST, 10L, false, null)).thenThrow(failure);
		} else {
			when(reportService.createCommentReport("free", 100L, 50L, REQUEST, 10L, false, null)).thenThrow(failure);
		}

		mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
			.content(VALID_REQUEST)
			.with(csrf())
			.with(authentication(createAuthentication("ROLE_MEMBER")))).andExpect(status().is(expectedStatus));
	}

	private static Stream<Arguments> serviceFailures() {
		return Stream.of(Arguments.of(POST_URL, new MemberUnavailableException(), 401),
			Arguments.of(POST_URL, new ResponseStatusException(HttpStatus.FORBIDDEN, "제재 중에는 신고할 수 없습니다."), 403),
			Arguments.of(POST_URL, new BoardNotFoundException(), 404),
			Arguments.of(POST_URL, new PostNotFoundException(), 404),
			Arguments.of(POST_URL, new DuplicateReportException(), 409),
			Arguments.of(COMMENT_URL, new SecretPostAccessDeniedException(), 403),
			Arguments.of(COMMENT_URL, new CommentNotFoundException(), 404),
			Arguments.of(COMMENT_URL, new DuplicateReportException(), 409));
	}

	private Authentication createAuthentication(String authority) {
		return new UsernamePasswordAuthenticationToken(new LoginMember(10L), null,
			List.of(new SimpleGrantedAuthority(authority)));
	}
}