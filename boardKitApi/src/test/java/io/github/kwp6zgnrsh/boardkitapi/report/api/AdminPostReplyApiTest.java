package io.github.kwp6zgnrsh.boardkitapi.report.api;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.kwp6zgnrsh.boardkitapi.auth.mapper.RevokedTokenMapper;
import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtTokenProvider;
import io.github.kwp6zgnrsh.boardkitapi.config.MethodSecurityConfig;
import io.github.kwp6zgnrsh.boardkitapi.config.SecurityConfig;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.api.AdminPostReplyApi;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostReplyFilter;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.AdminPostReplyPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.AdminPostReplySummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.service.PostReplyService;

@WebMvcTest(AdminPostReplyApi.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class})
class AdminPostReplyApiTest {

	private static final String URL = "/api/admin/post-replies";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PostReplyService postReplyService;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private RevokedTokenMapper revokedTokenMapper;

	@MockitoBean
	private MemberMapper memberMapper;

	@Test
	@DisplayName("기본 요청은 미답변 목록의 첫 페이지를 10개씩 조회한다")
	void getUnansweredTargetsWithDefaultPagination() throws Exception {
		var response = new AdminPostReplyPageResponse(List.of(), 1, 10, 0L, 0);

		when(postReplyService.getAdminPostReplyTargets(PostReplyFilter.UNANSWERED, 1, 10, 10L, true)).thenReturn(
			response);

		mockMvc.perform(get(URL).with(authentication(createAuthentication("ROLE_ADMIN"))))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.posts.length()").value(0))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.size").value(10))
			.andExpect(jsonPath("$.totalElements").value(0))
			.andExpect(jsonPath("$.totalPages").value(0));

		verify(postReplyService).getAdminPostReplyTargets(PostReplyFilter.UNANSWERED, 1, 10, 10L, true);
	}

	@ParameterizedTest
	@EnumSource(PostReplyFilter.class)
	@DisplayName("선택한 답변 필터와 페이지 조건을 서비스에 전달한다")
	void passFilterAndPaginationToService(PostReplyFilter filter) throws Exception {
		var post = new AdminPostReplySummaryResponse(100L, "free", "자유게시판", "게시글 제목", "작성자", true,
			LocalDateTime.of(2026, 10, 18, 12, 0), null, true);
		var response = new AdminPostReplyPageResponse(List.of(post), 2, 20, 21L, 2);

		when(postReplyService.getAdminPostReplyTargets(filter, 2, 20, 10L, true)).thenReturn(response);

		mockMvc.perform(get(URL).param("filter", filter.name())
				.param("page", "2")
				.param("size", "20")
				.with(authentication(createAuthentication("ROLE_ADMIN"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts[0].postId").value(100))
			.andExpect(jsonPath("$.posts[0].boardSlug").value("free"))
			.andExpect(jsonPath("$.posts[0].boardName").value("자유게시판"))
			.andExpect(jsonPath("$.posts[0].title").value("게시글 제목"))
			.andExpect(jsonPath("$.posts[0].authorDisplayName").value("작성자"))
			.andExpect(jsonPath("$.posts[0].secret").value(true))
			.andExpect(jsonPath("$.posts[0].officialReplyEnabled").value(true))
			.andExpect(jsonPath("$.page").value(2))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalElements").value(21))
			.andExpect(jsonPath("$.totalPages").value(2));

		verify(postReplyService).getAdminPostReplyTargets(filter, 2, 20, 10L, true);
	}

	@Test
	@DisplayName("비로그인 사용자는 공식 답변 관리 목록을 조회할 수 없다")
	void rejectAdminReplyListWithoutAuthentication() throws Exception {
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());

		verifyNoInteractions(postReplyService);
	}

	@Test
	@DisplayName("일반 회원은 공식 답변 관리 목록을 조회할 수 없다")
	void rejectAdminReplyListByMember() throws Exception {
		mockMvc.perform(get(URL).with(authentication(createAuthentication("ROLE_MEMBER"))))
			.andExpect(status().isForbidden());

		verifyNoInteractions(postReplyService);
	}

	@ParameterizedTest
	@CsvSource({"page, 0", "page, -1", "page, abc", "size, 0", "size, -1", "size, 101", "size, abc"})
	@DisplayName("잘못된 페이지 조건은 서비스 호출 전에 거절한다")
	void rejectInvalidPagination(String parameter, String value) throws Exception {
		mockMvc.perform(get(URL).param(parameter, value).with(authentication(createAuthentication("ROLE_ADMIN"))))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(postReplyService);
	}

	@Test
	@DisplayName("지원하지 않는 답변 필터는 서비스 호출 전에 거절한다")
	void rejectUnsupportedReplyFilter() throws Exception {
		mockMvc.perform(get(URL).param("filter", "INVALID").with(authentication(createAuthentication("ROLE_ADMIN"))))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(postReplyService);
	}

	@Test
	@DisplayName("서비스에서 비활성 회원으로 판단하면 401을 반환한다")
	void returnUnauthorizedForUnavailableMember() throws Exception {
		when(postReplyService.getAdminPostReplyTargets(PostReplyFilter.UNANSWERED, 1, 10, 10L, true)).thenThrow(
			new MemberUnavailableException());

		mockMvc.perform(get(URL).with(authentication(createAuthentication("ROLE_ADMIN"))))
			.andExpect(status().isUnauthorized());
	}

	private Authentication createAuthentication(String authority) {
		return new UsernamePasswordAuthenticationToken(new LoginMember(10L), null,
			List.of(new SimpleGrantedAuthority(authority)));
	}
}