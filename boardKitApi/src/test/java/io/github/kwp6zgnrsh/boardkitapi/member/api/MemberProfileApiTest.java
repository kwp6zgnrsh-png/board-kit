package io.github.kwp6zgnrsh.boardkitapi.member.api;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.kwp6zgnrsh.boardkitapi.auth.cookie.AuthCookieFactory;
import io.github.kwp6zgnrsh.boardkitapi.auth.mapper.RevokedTokenMapper;
import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtTokenProvider;
import io.github.kwp6zgnrsh.boardkitapi.config.SecurityConfig;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberDisplayNameUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberPasswordUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MemberProfileResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MyCommentPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MyCommentSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MyPostPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MyPostSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.DuplicateMemberException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.InvalidMemberRequestException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberConflictException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.member.service.MemberService;

@WebMvcTest(value = MemberApi.class, properties = "boardkit.auth.cookie-secure=true")
@Import({SecurityConfig.class, AuthCookieFactory.class})
class MemberProfileApiTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MemberService memberService;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private RevokedTokenMapper revokedTokenMapper;

	@MockitoBean
	private MemberMapper memberMapper;

	private static final String VALID_DISPLAY_NAME_UPDATE_REQUEST = """
		{
		  "displayName": "새닉네임"
		}
		""";

	private static final String VALID_PASSWORD_UPDATE_REQUEST = """
		{
		  "currentPassword": "1234",
		  "newPassword": "NewBoardkit1234!",
		  "newPasswordConfirm": "NewBoardkit1234!"
		}
		""";

	@Test
	@DisplayName("요청으로 전달한 회원 ID와 관계없이 로그인한 회원의 프로필을 조회한다")
	void getAuthenticatedMemberProfile() throws Exception {
		MemberProfileResponse response = new MemberProfileResponse(10L, "member01", "회원", "MEMBER",
			LocalDateTime.of(2026, 10, 18, 12, 0));

		when(memberService.getProfile(10L)).thenReturn(response);

		mockMvc.perform(get("/api/members/me").param("memberId", "99").with(authentication(memberAuthentication())))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(jsonPath("$.memberId").value(10))
			.andExpect(jsonPath("$.loginId").value("member01"))
			.andExpect(jsonPath("$.displayName").value("회원"))
			.andExpect(jsonPath("$.role").value("MEMBER"))
			.andExpect(jsonPath("$.createdAt").exists())
			.andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andExpect(jsonPath("$.password").doesNotExist());

		verify(memberService).getProfile(10L);
	}

	@Test
	@DisplayName("비로그인 사용자의 프로필 조회는 401을 반환한다")
	void rejectProfileWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/members/me")).andExpect(status().isUnauthorized());

		verifyNoInteractions(memberService);
	}

	@Test
	@DisplayName("인증 정보가 남아 있어도 탈퇴한 회원이면 401을 반환한다")
	void rejectProfileForUnavailableMember() throws Exception {
		when(memberService.getProfile(10L)).thenThrow(new MemberUnavailableException());

		mockMvc.perform(get("/api/members/me").with(authentication(memberAuthentication())))
			.andExpect(status().isUnauthorized());

		verify(memberService).getProfile(10L);
	}

	@Test
	@DisplayName("로그인한 회원의 닉네임을 변경하고 204를 반환한다")
	void updateAuthenticatedMemberDisplayName() throws Exception {
		mockMvc.perform(patch("/api/members/me/display-name").param("memberId", "99")
				.with(authentication(memberAuthentication()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_DISPLAY_NAME_UPDATE_REQUEST))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		verify(memberService).updateDisplayName(10L, new MemberDisplayNameUpdateRequest("새닉네임"));
	}

	@Test
	@DisplayName("비로그인 사용자의 닉네임 변경은 401을 반환한다")
	void rejectDisplayNameUpdateWithoutAuthentication() throws Exception {
		mockMvc.perform(patch("/api/members/me/display-name").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(VALID_DISPLAY_NAME_UPDATE_REQUEST)).andExpect(status().isUnauthorized());

		verifyNoInteractions(memberService);
	}

	@Test
	@DisplayName("CSRF 토큰이 없으면 닉네임 변경은 403을 반환한다")
	void rejectDisplayNameUpdateWithoutCsrfToken() throws Exception {
		mockMvc.perform(patch("/api/members/me/display-name").with(authentication(memberAuthentication()))
			.contentType(MediaType.APPLICATION_JSON)
			.content(VALID_DISPLAY_NAME_UPDATE_REQUEST)).andExpect(status().isForbidden());

		verifyNoInteractions(memberService);
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"displayName\":null}", "{\"displayName\":\"\"}", "{\"displayName\":\"   \"}"})
	@DisplayName("닉네임이 없거나 공백이면 서비스 호출 전에 400을 반환한다")
	void rejectInvalidDisplayNameUpdateRequest(String request) throws Exception {
		mockMvc.perform(patch("/api/members/me/display-name").with(authentication(memberAuthentication()))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(request)).andExpect(status().isBadRequest());

		verifyNoInteractions(memberService);
	}

	@Test
	@DisplayName("닉네임이 중복되면 409를 반환한다")
	void returnConflictForDuplicatedDisplayName() throws Exception {
		MemberDisplayNameUpdateRequest request = new MemberDisplayNameUpdateRequest("새닉네임");

		doThrow(new DuplicateMemberException("이미 사용 중인 닉네임입니다.")).when(memberService).updateDisplayName(10L, request);

		mockMvc.perform(patch("/api/members/me/display-name").with(authentication(memberAuthentication()))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(VALID_DISPLAY_NAME_UPDATE_REQUEST)).andExpect(status().isConflict());

		verify(memberService).updateDisplayName(10L, request);
	}

	@Test
	@DisplayName("탈퇴한 회원의 닉네임 변경은 401을 반환한다")
	void rejectDisplayNameUpdateForUnavailableMember() throws Exception {
		MemberDisplayNameUpdateRequest request = new MemberDisplayNameUpdateRequest("새닉네임");

		doThrow(new MemberUnavailableException()).when(memberService).updateDisplayName(10L, request);

		mockMvc.perform(patch("/api/members/me/display-name").with(authentication(memberAuthentication()))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(VALID_DISPLAY_NAME_UPDATE_REQUEST)).andExpect(status().isUnauthorized());

		verify(memberService).updateDisplayName(10L, request);
	}

	@ParameterizedTest
	@ValueSource(ints = {12, 64})
	@DisplayName("허용 길이의 새 비밀번호로 변경하면 인증 쿠키를 만료시킨다")
	void updatePasswordAndExpireAuthenticationCookie(int length) throws Exception {
		String newPassword = "a".repeat(length);
		String body = VALID_PASSWORD_UPDATE_REQUEST.replace("NewBoardkit1234!", newPassword);

		mockMvc.perform(patch("/api/members/me/password").param("memberId", "99")
				.with(authentication(memberAuthentication()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""))
			.andExpect(cookie().value("boardkit_access_token", ""))
			.andExpect(cookie().maxAge("boardkit_access_token", 0))
			.andExpect(cookie().path("boardkit_access_token", "/api"))
			.andExpect(cookie().httpOnly("boardkit_access_token", true))
			.andExpect(cookie().secure("boardkit_access_token", true))
			.andExpect(cookie().attribute("boardkit_access_token", "SameSite", "Lax"));

		verify(memberService).updatePassword(10L, new MemberPasswordUpdateRequest("1234", newPassword, newPassword));
	}

	@Test
	@DisplayName("비로그인 사용자의 비밀번호 변경은 401을 반환한다")
	void rejectPasswordUpdateWithoutAuthentication() throws Exception {
		mockMvc.perform(patch("/api/members/me/password").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_PASSWORD_UPDATE_REQUEST))
			.andExpect(status().isUnauthorized())
			.andExpect(cookie().doesNotExist("boardkit_access_token"));

		verifyNoInteractions(memberService);
	}

	@Test
	@DisplayName("CSRF 토큰이 없으면 비밀번호 변경은 403을 반환한다")
	void rejectPasswordUpdateWithoutCsrfToken() throws Exception {
		mockMvc.perform(patch("/api/members/me/password").with(authentication(memberAuthentication()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_PASSWORD_UPDATE_REQUEST))
			.andExpect(status().isForbidden())
			.andExpect(cookie().doesNotExist("boardkit_access_token"));

		verifyNoInteractions(memberService);
	}

	@ParameterizedTest
	@MethodSource("invalidPasswordUpdateRequests")
	@DisplayName("잘못된 비밀번호 변경 입력은 서비스 호출 전에 거절한다")
	void rejectInvalidPasswordUpdateRequest(String body) throws Exception {
		mockMvc.perform(patch("/api/members/me/password").with(authentication(memberAuthentication()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest())
			.andExpect(cookie().doesNotExist("boardkit_access_token"));

		verifyNoInteractions(memberService);
	}

	private static Stream<String> invalidPasswordUpdateRequests() {
		return Stream.of("{}", VALID_PASSWORD_UPDATE_REQUEST.replace("\"1234\"", "\"\""),
			VALID_PASSWORD_UPDATE_REQUEST.replace("\"1234\"", "null"),
			VALID_PASSWORD_UPDATE_REQUEST.replace("NewBoardkit1234!", "a".repeat(11)),
			VALID_PASSWORD_UPDATE_REQUEST.replace("NewBoardkit1234!", "a".repeat(65)),
			VALID_PASSWORD_UPDATE_REQUEST.replace("NewBoardkit1234!", "가".repeat(12)),
			VALID_PASSWORD_UPDATE_REQUEST.replace("NewBoardkit1234!", "a".repeat(11) + " "),
			VALID_PASSWORD_UPDATE_REQUEST.replace("\"newPassword\": \"NewBoardkit1234!\"", "\"newPassword\": null"),
			VALID_PASSWORD_UPDATE_REQUEST.replace("\"newPasswordConfirm\": \"NewBoardkit1234!\"",
				"\"newPasswordConfirm\": null"));
	}

	@ParameterizedTest
	@MethodSource("passwordUpdateFailures")
	@DisplayName("비밀번호 변경이 거절되면 오류를 반환하고 인증 쿠키를 만료시키지 않는다")
	void returnPasswordUpdateFailureWithoutExpiringCookie(RuntimeException failure, int expectedStatus) throws
		Exception {
		MemberPasswordUpdateRequest request = new MemberPasswordUpdateRequest("1234", "NewBoardkit1234!",
			"NewBoardkit1234!");

		doThrow(failure).when(memberService).updatePassword(10L, request);

		mockMvc.perform(patch("/api/members/me/password").with(authentication(memberAuthentication()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_PASSWORD_UPDATE_REQUEST))
			.andExpect(status().is(expectedStatus))
			.andExpect(cookie().doesNotExist("boardkit_access_token"));

		verify(memberService).updatePassword(10L, request);
	}

	private static Stream<Arguments> passwordUpdateFailures() {
		return Stream.of(Arguments.of(new InvalidMemberRequestException("현재 비밀번호가 올바르지 않습니다."), 400),
			Arguments.of(new MemberConflictException(), 409), Arguments.of(new MemberUnavailableException(), 401));
	}

	@Test
	@DisplayName("로그인한 회원의 작성 내역을 기본 페이지 조건으로 조회한다")
	void getMyPostsWithDefaultPagination() throws Exception {
		List<MyPostSummaryResponse> posts = List.of(
			new MyPostSummaryResponse(100L, "archived", "보관 게시판", "ARCHIVED", "내 게시글", true, 7L,
				LocalDateTime.of(2026, 10, 18, 12, 0)));
		when(memberService.getMyPosts(10L, 1, 10)).thenReturn(new MyPostPageResponse(posts, 1, 10, 1L, 1));

		mockMvc.perform(
				get("/api/members/me/posts").param("memberId", "99").with(authentication(memberAuthentication())))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(jsonPath("$.posts.length()").value(1))
			.andExpect(jsonPath("$.posts[0].id").value(100))
			.andExpect(jsonPath("$.posts[0].boardSlug").value("archived"))
			.andExpect(jsonPath("$.posts[0].boardStatus").value("ARCHIVED"))
			.andExpect(jsonPath("$.posts[0].title").value("내 게시글"))
			.andExpect(jsonPath("$.posts[0].secret").value(true))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.size").value(10))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.totalPages").value(1));

		verify(memberService).getMyPosts(10L, 1, 10);
	}

	@Test
	@DisplayName("요청한 페이지 번호와 크기를 서비스에 전달한다")
	void passMyPostsPaginationToService() throws Exception {
		when(memberService.getMyPosts(10L, 2, 20)).thenReturn(new MyPostPageResponse(List.of(), 2, 20, 0L, 0));

		mockMvc.perform(get("/api/members/me/posts").param("page", "2")
				.param("size", "20")
				.with(authentication(memberAuthentication())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(2))
			.andExpect(jsonPath("$.size").value(20));

		verify(memberService).getMyPosts(10L, 2, 20);
	}

	@Test
	@DisplayName("작성한 게시글이 없으면 빈 배열과 페이지 정보를 반환한다")
	void returnEmptyMyPostsResponse() throws Exception {
		when(memberService.getMyPosts(10L, 1, 10)).thenReturn(new MyPostPageResponse(List.of(), 1, 10, 0L, 0));

		mockMvc.perform(get("/api/members/me/posts").with(authentication(memberAuthentication())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts.length()").value(0))
			.andExpect(jsonPath("$.totalElements").value(0))
			.andExpect(jsonPath("$.totalPages").value(0));
	}

	@Test
	@DisplayName("비로그인 사용자의 작성 내역 조회는 401을 반환한다")
	void rejectMyPostsWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/members/me/posts")).andExpect(status().isUnauthorized());

		verifyNoInteractions(memberService);
	}

	@ParameterizedTest
	@CsvSource({"0, 10", "-1, 10", "1, 0", "1, -1", "1, 101", "abc, 10", "1, abc"})
	@DisplayName("잘못된 페이지 조건은 서비스 호출 전에 400을 반환한다")
	void rejectInvalidMyPostsPagination(String page, String size) throws Exception {
		mockMvc.perform(get("/api/members/me/posts").param("page", page)
			.param("size", size)
			.with(authentication(memberAuthentication()))).andExpect(status().isBadRequest());

		verifyNoInteractions(memberService);
	}

	@Test
	@DisplayName("탈퇴한 회원의 작성 내역 조회는 401을 반환한다")
	void rejectMyPostsForUnavailableMember() throws Exception {
		when(memberService.getMyPosts(10L, 1, 10)).thenThrow(new MemberUnavailableException());

		mockMvc.perform(get("/api/members/me/posts").with(authentication(memberAuthentication())))
			.andExpect(status().isUnauthorized());

		verify(memberService).getMyPosts(10L, 1, 10);
	}

	@Test
	@DisplayName("로그인한 회원의 댓글과 대댓글을 기본 페이지 조건으로 조회한다")
	void getMyCommentsWithDefaultPagination() throws Exception {
		LocalDateTime createdAt = LocalDateTime.of(2026, 10, 18, 12, 0);
		List<MyCommentSummaryResponse> comments = List.of(
			new MyCommentSummaryResponse(31L, 100L, null, "free", "자유게시판", "ACTIVE", false, "내 댓글", createdAt),
			new MyCommentSummaryResponse(30L, 99L, 20L, "archived", "보관 게시판", "ARCHIVED", true, "삭제된 글에 남긴 내 대댓글",
				createdAt.minusMinutes(1)));

		when(memberService.getMyComments(10L, 1, 10)).thenReturn(new MyCommentPageResponse(comments, 1, 10, 2L, 1));

		mockMvc.perform(
				get("/api/members/me/comments").param("memberId", "99").with(authentication(memberAuthentication())))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(jsonPath("$.comments.length()").value(2))
			.andExpect(jsonPath("$.comments[0].id").value(31))
			.andExpect(jsonPath("$.comments[0].postId").value(100))
			.andExpect(jsonPath("$.comments[0].content").value("내 댓글"))
			.andExpect(jsonPath("$.comments[0].postDeleted").value(false))
			.andExpect(jsonPath("$.comments[1].parentId").value(20))
			.andExpect(jsonPath("$.comments[1].boardStatus").value("ARCHIVED"))
			.andExpect(jsonPath("$.comments[1].postDeleted").value(true))
			.andExpect(jsonPath("$.comments[1].postTitle").doesNotExist())
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.size").value(10))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.totalPages").value(1));

		verify(memberService).getMyComments(10L, 1, 10);
	}

	@Test
	@DisplayName("내 댓글 조회의 페이지 번호와 크기를 서비스에 전달한다")
	void passMyCommentsPaginationToService() throws Exception {
		when(memberService.getMyComments(10L, 2, 20)).thenReturn(new MyCommentPageResponse(List.of(), 2, 20, 0L, 0));

		mockMvc.perform(get("/api/members/me/comments").param("page", "2")
				.param("size", "20")
				.with(authentication(memberAuthentication())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(2))
			.andExpect(jsonPath("$.size").value(20));

		verify(memberService).getMyComments(10L, 2, 20);
	}

	@Test
	@DisplayName("작성한 댓글이 없으면 빈 배열과 페이지 정보를 반환한다")
	void returnEmptyMyCommentsResponse() throws Exception {
		when(memberService.getMyComments(10L, 1, 10)).thenReturn(new MyCommentPageResponse(List.of(), 1, 10, 0L, 0));

		mockMvc.perform(get("/api/members/me/comments").with(authentication(memberAuthentication())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.comments.length()").value(0))
			.andExpect(jsonPath("$.totalElements").value(0))
			.andExpect(jsonPath("$.totalPages").value(0));
	}

	@Test
	@DisplayName("비로그인 사용자의 댓글 작성 내역 조회는 401을 반환한다")
	void rejectMyCommentsWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/members/me/comments")).andExpect(status().isUnauthorized());

		verifyNoInteractions(memberService);
	}

	@ParameterizedTest
	@CsvSource({"0, 10", "-1, 10", "1, 0", "1, -1", "1, 101", "abc, 10", "1, abc"})
	@DisplayName("잘못된 댓글 페이지 조건은 서비스 호출 전에 400을 반환한다")
	void rejectInvalidMyCommentsPagination(String page, String size) throws Exception {
		mockMvc.perform(get("/api/members/me/comments").param("page", page)
			.param("size", size)
			.with(authentication(memberAuthentication()))).andExpect(status().isBadRequest());

		verifyNoInteractions(memberService);
	}

	@Test
	@DisplayName("탈퇴한 회원의 댓글 작성 내역 조회는 401을 반환한다")
	void rejectMyCommentsForUnavailableMember() throws Exception {
		when(memberService.getMyComments(10L, 1, 10)).thenThrow(new MemberUnavailableException());

		mockMvc.perform(get("/api/members/me/comments").with(authentication(memberAuthentication())))
			.andExpect(status().isUnauthorized());

		verify(memberService).getMyComments(10L, 1, 10);
	}

	private UsernamePasswordAuthenticationToken memberAuthentication() {
		return new UsernamePasswordAuthenticationToken(new LoginMember(10L), null,
			List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
	}
}