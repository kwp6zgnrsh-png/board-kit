package io.github.kwp6zgnrsh.boardkitapi.comment.api;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.kwp6zgnrsh.boardkitapi.auth.mapper.RevokedTokenMapper;
import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtTokenProvider;
import io.github.kwp6zgnrsh.boardkitapi.comment.exception.CommentNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.comment.service.CommentLikeService;
import io.github.kwp6zgnrsh.boardkitapi.config.SecurityConfig;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.SecretPostAccessDeniedException;

@WebMvcTest(CommentLikeApi.class)
@Import(SecurityConfig.class)
class CommentLikeApiTest {

	private static final String URL = "/api/boards/free/posts/100/comments/50/like";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CommentLikeService commentLikeService;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private RevokedTokenMapper revokedTokenMapper;

	@MockitoBean
	private MemberMapper memberMapper;

	@Test
	@DisplayName("회원의 댓글 좋아요 요청과 비밀글 비밀번호를 서비스에 전달한다")
	void likeCommentWithMemberAuthentication() throws Exception {
		mockMvc.perform(put(URL).header("X-Post-Password", "secret")
				.with(csrf())
				.with(authentication(createAuthentication(10L, "ROLE_MEMBER"))))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		verify(commentLikeService).likeComment("free", 100L, 50L, 10L, false, "secret");
	}

	@Test
	@DisplayName("회원의 댓글 좋아요 취소 요청과 비밀글 비밀번호를 서비스에 전달한다")
	void unlikeCommentWithMemberAuthentication() throws Exception {
		mockMvc.perform(delete(URL).header("X-Post-Password", "secret")
				.with(csrf())
				.with(authentication(createAuthentication(10L, "ROLE_MEMBER"))))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		verify(commentLikeService).unlikeComment("free", 100L, 50L, 10L, false, "secret");
	}

	@Test
	@DisplayName("관리자의 댓글 좋아요 요청은 관리자 권한을 서비스에 전달한다")
	void passAdminAuthorityToService() throws Exception {
		mockMvc.perform(put(URL).with(csrf()).with(authentication(createAuthentication(1L, "ROLE_ADMIN"))))
			.andExpect(status().isNoContent());

		verify(commentLikeService).likeComment("free", 100L, 50L, 1L, true, null);
	}

	@ParameterizedTest
	@ValueSource(strings = {"PUT", "DELETE"})
	@DisplayName("비로그인 사용자의 댓글 좋아요 등록과 취소는 거절한다")
	void rejectLikeRequestWithoutAuthentication(String method) throws Exception {
		mockMvc.perform(request(HttpMethod.valueOf(method), URL).with(csrf())).andExpect(status().isUnauthorized());

		verifyNoInteractions(commentLikeService);
	}

	@ParameterizedTest
	@ValueSource(strings = {"PUT", "DELETE"})
	@DisplayName("CSRF 토큰이 없으면 댓글 좋아요 등록과 취소를 거절한다")
	void rejectLikeRequestWithoutCsrfToken(String method) throws Exception {
		mockMvc.perform(
				request(HttpMethod.valueOf(method), URL).with(authentication(createAuthentication(10L, "ROLE_MEMBER"))))
			.andExpect(status().isForbidden());

		verifyNoInteractions(commentLikeService);
	}

	@ParameterizedTest
	@ValueSource(strings = {"PUT", "DELETE"})
	@DisplayName("CSRF 토큰이 잘못되면 댓글 좋아요 등록과 취소를 거절한다")
	void rejectLikeRequestWithInvalidCsrfToken(String method) throws Exception {
		mockMvc.perform(request(HttpMethod.valueOf(method), URL).with(csrf().useInvalidToken())
			.with(authentication(createAuthentication(10L, "ROLE_MEMBER")))).andExpect(status().isForbidden());

		verifyNoInteractions(commentLikeService);
	}

	@ParameterizedTest
	@CsvSource({"PUT, 0, 50", "PUT, -1, 50", "PUT, 100, 0", "PUT, 100, -1", "DELETE, 0, 50", "DELETE, -1, 50",
		"DELETE, 100, 0", "DELETE, 100, -1"})
	@DisplayName("게시글이나 댓글 ID가 양수가 아니면 서비스 호출 전에 거절한다")
	void rejectLikeRequestWithInvalidIds(String method, long postId, long commentId) throws Exception {
		String url = "/api/boards/free/posts/" + postId + "/comments/" + commentId + "/like";

		mockMvc.perform(request(HttpMethod.valueOf(method), url).with(csrf())
			.with(authentication(createAuthentication(10L, "ROLE_MEMBER")))).andExpect(status().isBadRequest());

		verifyNoInteractions(commentLikeService);
	}

	@Test
	@DisplayName("비밀글 접근이 거절되면 댓글 좋아요 요청은 403을 반환한다")
	void returnForbiddenWhenSecretAccessIsDenied() throws Exception {
		doThrow(new SecretPostAccessDeniedException()).when(commentLikeService)
			.likeComment("free", 100L, 50L, 10L, false, "wrong");

		mockMvc.perform(put(URL).header("X-Post-Password", "wrong")
			.with(csrf())
			.with(authentication(createAuthentication(10L, "ROLE_MEMBER")))).andExpect(status().isForbidden());

		verify(commentLikeService).likeComment("free", 100L, 50L, 10L, false, "wrong");
	}

	@Test
	@DisplayName("사용할 수 없는 댓글의 좋아요 취소 요청은 404를 반환한다")
	void returnNotFoundWhenCommentIsUnavailable() throws Exception {
		doThrow(new CommentNotFoundException()).when(commentLikeService)
			.unlikeComment("free", 100L, 50L, 10L, false, null);

		mockMvc.perform(delete(URL).with(csrf()).with(authentication(createAuthentication(10L, "ROLE_MEMBER"))))
			.andExpect(status().isNotFound());

		verify(commentLikeService).unlikeComment("free", 100L, 50L, 10L, false, null);
	}

	private Authentication createAuthentication(Long memberId, String authority) {
		return new UsernamePasswordAuthenticationToken(new LoginMember(memberId), null,
			List.of(new SimpleGrantedAuthority(authority)));
	}
}