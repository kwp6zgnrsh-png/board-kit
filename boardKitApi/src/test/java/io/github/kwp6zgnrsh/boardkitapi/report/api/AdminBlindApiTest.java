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

import io.github.kwp6zgnrsh.boardkitapi.auth.mapper.RevokedTokenMapper;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtTokenProvider;
import io.github.kwp6zgnrsh.boardkitapi.comment.exception.CommentNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.config.MethodSecurityConfig;
import io.github.kwp6zgnrsh.boardkitapi.config.SecurityConfig;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportTargetAction;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportTargetActionRequest;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminBlindedCommentPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminBlindedCommentSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminBlindedContentDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminBlindedPostPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminBlindedPostSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.exception.ReportTargetConflictException;
import io.github.kwp6zgnrsh.boardkitapi.report.service.AdminBlindService;
import io.github.kwp6zgnrsh.boardkitapi.report.service.AdminContentModerationService;
import tools.jackson.databind.node.JsonNodeFactory;

@WebMvcTest(AdminBlindApi.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class})
class AdminBlindApiTest {

	private static final String URL = "/api/admin/blinded";
	private static final List<String> LIST_URLS = List.of(URL + "/posts", URL + "/comments");
	private static final List<String> READ_URLS = List.of(URL + "/posts", URL + "/comments", URL + "/posts/100",
		URL + "/comments/300");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AdminBlindService adminBlindService;
	@MockitoBean
	private AdminContentModerationService adminContentModerationService;
	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;
	@MockitoBean
	private RevokedTokenMapper revokedTokenMapper;
	@MockitoBean
	private MemberMapper memberMapper;
	private static final List<String> TARGET_URLS = List.of(
		URL + "/posts/100",
		URL + "/posts/100/comments/300"
	);

	private static final String TARGET_BODY = """
	{"action":"UNBLIND","version":4}
	""";

	@Test
	@DisplayName("관리자는 기본 페이지 조건으로 블라인드 게시글을 조회한다")
	void getBlindedPostsWithDefaultPagination() throws Exception {
		var post = new AdminBlindedPostSummaryResponse(100L, "free", "자유게시판", "ARCHIVED", "게시글 제목", 20L, "작성자",
			LocalDateTime.of(2026, 10, 19, 10, 0), 4);
		when(adminBlindService.getBlindedPosts(1, 10, 10L, true)).thenReturn(
			new AdminBlindedPostPageResponse(List.of(post), 1, 10, 1L, 1));

		mockMvc.perform(get(URL + "/posts").with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.posts[0].postId").value(100))
			.andExpect(jsonPath("$.posts[0].boardStatus").value("ARCHIVED"))
			.andExpect(jsonPath("$.posts[0].version").value(4))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.size").value(10))
			.andExpect(jsonPath("$.totalElements").value(1));

		verify(adminBlindService).getBlindedPosts(1, 10, 10L, true);
	}

	@Test
	@DisplayName("관리자는 선택한 페이지 조건으로 블라인드 댓글을 조회한다")
	void getBlindedCommentsWithRequestedPagination() throws Exception {
		var comment = new AdminBlindedCommentSummaryResponse(300L, 200L, 100L, "free", "자유게시판", "ACTIVE", "게시글 제목",
			"댓글 미리보기", 20L, "작성자", LocalDateTime.of(2026, 10, 19, 10, 0), 7);
		when(adminBlindService.getBlindedComments(2, 20, 10L, true)).thenReturn(
			new AdminBlindedCommentPageResponse(List.of(comment), 2, 20, 21L, 2));

		mockMvc.perform(get(URL + "/comments").param("page", "2")
				.param("size", "20")
				.with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.comments[0].commentId").value(300))
			.andExpect(jsonPath("$.comments[0].parentId").value(200))
			.andExpect(jsonPath("$.comments[0].postId").value(100))
			.andExpect(jsonPath("$.comments[0].version").value(7))
			.andExpect(jsonPath("$.page").value(2))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalElements").value(21))
			.andExpect(jsonPath("$.totalPages").value(2));

		verify(adminBlindService).getBlindedComments(2, 20, 10L, true);
	}

	@Test
	@DisplayName("비로그인 사용자는 블라인드 목록을 조회할 수 없다")
	void rejectListRequestsWithoutAuthentication() throws Exception {
		for (String url : READ_URLS) {
			mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
		}

		verifyNoInteractions(adminBlindService);
	}

	@Test
	@DisplayName("일반 회원은 블라인드 목록을 조회할 수 없다")
	void rejectListRequestsByMember() throws Exception {
		for (String url : READ_URLS) {
			mockMvc.perform(get(url).with(authentication(createAuthentication(10L, "ROLE_MEMBER"))))
				.andExpect(status().isForbidden());
		}

		verifyNoInteractions(adminBlindService);
	}

	@ParameterizedTest
	@CsvSource({"page, 0", "size, 0", "size, 101", "page, abc"})
	@DisplayName("잘못된 페이지 조건은 서비스 호출 전에 거절한다")
	void rejectInvalidPagination(String parameter, String value) throws Exception {
		for (String url : LIST_URLS) {
			mockMvc.perform(get(url).param(parameter, value).with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
				.andExpect(status().isBadRequest());
		}

		verifyNoInteractions(adminBlindService);
	}

	@Test
	@DisplayName("관리자는 블라인드 게시글의 원문과 JSON 및 버전을 조회한다")
	void getBlindedPostDetailAsAdmin() throws Exception {
		when(adminBlindService.getBlindedPost(100L, 10L, true)).thenReturn(detailResponse(null));

		mockMvc.perform(get(URL + "/posts/100").with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.postId").value(100))
			.andExpect(jsonPath("$.boardStatus").value("ARCHIVED"))
			.andExpect(jsonPath("$.content").value("게시글 원문"))
			.andExpect(jsonPath("$.contentDocument.type").value("doc"))
			.andExpect(jsonPath("$.version").value(4));

		verify(adminBlindService).getBlindedPost(100L, 10L, true);
	}

	@Test
	@DisplayName("관리자는 블라인드 댓글의 전체 원문과 소속 게시글을 조회한다")
	void getBlindedCommentDetailAsAdmin() throws Exception {
		when(adminBlindService.getBlindedComment(300L, 10L, true)).thenReturn(detailResponse(300L));

		mockMvc.perform(get(URL + "/comments/300").with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.postId").value(100))
			.andExpect(jsonPath("$.commentId").value(300))
			.andExpect(jsonPath("$.content").value("댓글 원문"))
			.andExpect(jsonPath("$.contentDocument").isEmpty())
			.andExpect(jsonPath("$.version").value(4));

		verify(adminBlindService).getBlindedComment(300L, 10L, true);
	}

	@Test
	@DisplayName("해제되거나 삭제되어 조회 대상이 아니면 상세 요청에 404를 반환한다")
	void returnNotFoundForUnavailableDetails() throws Exception {
		when(adminBlindService.getBlindedPost(100L, 10L, true)).thenThrow(new PostNotFoundException());
		when(adminBlindService.getBlindedComment(300L, 10L, true)).thenThrow(new CommentNotFoundException());

		for (String url : List.of(URL + "/posts/100", URL + "/comments/300")) {
			mockMvc.perform(get(url).with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
				.andExpect(status().isNotFound());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "abc"})
	@DisplayName("올바르지 않은 상세 ID는 서비스 호출 전에 거절한다")
	void rejectInvalidDetailId(String id) throws Exception {
		for (String type : List.of("posts", "comments")) {
			mockMvc.perform(get(URL + "/" + type + "/" + id).with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
				.andExpect(status().isBadRequest());
		}

		verifyNoInteractions(adminBlindService);
	}

	@ParameterizedTest
	@EnumSource(value = ReportTargetAction.class, names = {"UNBLIND", "DELETE"})
	@DisplayName("관리자는 신고 ID 없이 게시글과 댓글을 해제하거나 삭제한다")
	void moderateTargetsWithoutReportId(ReportTargetAction action) throws Exception {
		String body = """
		{"action":"%s","version":4}
		""".formatted(action.name());

		for (String url : TARGET_URLS) {
			mockMvc.perform(patch(url)
					.contentType(MediaType.APPLICATION_JSON)
					.content(body)
					.with(csrf())
					.with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));
		}

		var request = new ReportTargetActionRequest(action, 4);

		verify(adminContentModerationService)
			.moderateTarget(100L, null, request, 10L, true);
		verify(adminContentModerationService)
			.moderateTarget(100L, 300L, request, 10L, true);
	}

	@Test
	@DisplayName("비로그인 사용자와 일반 회원은 직접 관리 조치를 할 수 없다")
	void rejectTargetActionsByNonAdmin() throws Exception {
		for (String url : TARGET_URLS) {
			mockMvc.perform(patch(url)
					.contentType(MediaType.APPLICATION_JSON)
					.content(TARGET_BODY)
					.with(csrf()))
				.andExpect(status().isUnauthorized());

			mockMvc.perform(patch(url)
					.contentType(MediaType.APPLICATION_JSON)
					.content(TARGET_BODY)
					.with(csrf())
					.with(authentication(createAuthentication(10L, "ROLE_MEMBER"))))
				.andExpect(status().isForbidden());
		}

		verifyNoInteractions(adminBlindService, adminContentModerationService);
	}

	@Test
	@DisplayName("관리자도 CSRF 토큰 없이 직접 관리 조치를 할 수 없다")
	void rejectTargetActionsWithoutCsrfToken() throws Exception {
		for (String url : TARGET_URLS) {
			mockMvc.perform(patch(url)
					.contentType(MediaType.APPLICATION_JSON)
					.content(TARGET_BODY)
					.with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
				.andExpect(status().isForbidden());
		}

		verifyNoInteractions(adminBlindService, adminContentModerationService);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{}",
		"{\"action\":\"UNBLIND\"}",
		"{\"action\":\"DELETE\",\"version\":-1}"
	})
	@DisplayName("잘못된 조치 요청은 서비스 호출 전에 거절한다")
	void rejectInvalidTargetActionRequest(String body) throws Exception {
		for (String url : TARGET_URLS) {
			mockMvc.perform(patch(url)
					.contentType(MediaType.APPLICATION_JSON)
					.content(body)
					.with(csrf())
					.with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
				.andExpect(status().isBadRequest());
		}

		verifyNoInteractions(adminBlindService, adminContentModerationService);
	}

	@Test
	@DisplayName("올바르지 않은 대상 ID는 서비스 호출 전에 거절한다")
	void rejectInvalidTargetIds() throws Exception {
		var invalidUrls = List.of(
			URL + "/posts/0",
			URL + "/posts/100/comments/0",
			URL + "/posts/abc/comments/300"
		);

		for (String url : invalidUrls) {
			mockMvc.perform(patch(url)
					.contentType(MediaType.APPLICATION_JSON)
					.content(TARGET_BODY)
					.with(csrf())
					.with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
				.andExpect(status().isBadRequest());
		}

		verifyNoInteractions(adminBlindService, adminContentModerationService);
	}

	@Test
	@DisplayName("대상 상태나 버전이 변경되면 직접 관리 조치도 409를 반환한다")
	void returnConflictForChangedTarget() throws Exception {
		var request = new ReportTargetActionRequest(ReportTargetAction.UNBLIND, 4);

		doThrow(new ReportTargetConflictException())
			.when(adminContentModerationService)
			.moderateTarget(100L, null, request, 10L, true);

		doThrow(new ReportTargetConflictException())
			.when(adminContentModerationService)
			.moderateTarget(100L, 300L, request, 10L, true);

		for (String url : TARGET_URLS) {
			mockMvc.perform(patch(url)
					.contentType(MediaType.APPLICATION_JSON)
					.content(TARGET_BODY)
					.with(csrf())
					.with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
				.andExpect(status().isConflict());
		}
	}

	private AdminBlindedContentDetailResponse detailResponse(Long commentId) {
		LocalDateTime time = LocalDateTime.of(2026, 10, 19, 10, 0);
		var document = commentId == null ? JsonNodeFactory.instance.objectNode().put("type", "doc") : null;

		return new AdminBlindedContentDetailResponse(100L, commentId, null, "free", "자유게시판", "ARCHIVED", "게시글 제목", 20L,
			"작성자", commentId == null ? "게시글 원문" : "댓글 원문", document, time.minusDays(1), time, time, 4);
	}
}
