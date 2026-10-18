package io.github.kwp6zgnrsh.boardkitapi.post.api;

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
import io.github.kwp6zgnrsh.boardkitapi.config.SecurityConfig;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.SecretPostAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.post.service.PostLikeService;

@WebMvcTest(PostLikeApi.class)
@Import(SecurityConfig.class)
class PostLikeApiTest {

	private static final String URL = "/api/boards/free/posts/100/like";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PostLikeService postLikeService;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private RevokedTokenMapper revokedTokenMapper;

	@MockitoBean
	private MemberMapper memberMapper;

	@Test
	@DisplayName("회원의 좋아요 등록 요청과 비밀글 비밀번호를 서비스에 전달한다")
	void likePostWithMemberAuthentication() throws Exception {
		mockMvc.perform(put(URL).header("X-Post-Password", "secret")
				.with(csrf())
				.with(authentication(createAuthentication(10L, "ROLE_MEMBER"))))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		verify(postLikeService).likePost("free", 100L, 10L, false, "secret");
	}

	@Test
	@DisplayName("회원의 좋아요 취소 요청과 비밀글 비밀번호를 서비스에 전달한다")
	void unlikePostWithMemberAuthentication() throws Exception {
		mockMvc.perform(delete(URL).header("X-Post-Password", "secret")
				.with(csrf())
				.with(authentication(createAuthentication(10L, "ROLE_MEMBER"))))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		verify(postLikeService).unlikePost("free", 100L, 10L, false, "secret");
	}

	@Test
	@DisplayName("관리자의 좋아요 요청은 관리자 권한을 서비스에 전달한다")
	void passAdminAuthorityToService() throws Exception {
		mockMvc.perform(put(URL).with(csrf()).with(authentication(createAuthentication(1L, "ROLE_ADMIN"))))
			.andExpect(status().isNoContent());

		verify(postLikeService).likePost("free", 100L, 1L, true, null);
	}

	@ParameterizedTest
	@ValueSource(strings = {"PUT", "DELETE"})
	@DisplayName("비로그인 사용자의 좋아요 등록과 취소는 거절한다")
	void rejectLikeRequestWithoutAuthentication(String method) throws Exception {
		mockMvc.perform(request(HttpMethod.valueOf(method), URL).with(csrf()))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(postLikeService);
	}

	@ParameterizedTest
	@ValueSource(strings = {"PUT", "DELETE"})
	@DisplayName("CSRF 토큰이 없으면 좋아요 등록과 취소를 거절한다")
	void rejectLikeRequestWithoutCsrfToken(String method) throws Exception {
		mockMvc.perform(
				request(HttpMethod.valueOf(method), URL).with(authentication(createAuthentication(10L, "ROLE_MEMBER"))))
			.andExpect(status().isForbidden());

		verifyNoInteractions(postLikeService);
	}

	@ParameterizedTest
	@ValueSource(strings = {"PUT", "DELETE"})
	@DisplayName("CSRF 토큰이 잘못되면 좋아요 등록과 취소를 거절한다")
	void rejectLikeRequestWithInvalidCsrfToken(String method) throws Exception {
		mockMvc.perform(request(HttpMethod.valueOf(method), URL).with(csrf().useInvalidToken())
			.with(authentication(createAuthentication(10L, "ROLE_MEMBER")))).andExpect(status().isForbidden());

		verifyNoInteractions(postLikeService);
	}

	@ParameterizedTest
	@CsvSource({"PUT, 0", "PUT, -1", "DELETE, 0", "DELETE, -1"})
	@DisplayName("게시글 ID가 양수가 아니면 서비스 호출 전에 거절한다")
	void rejectLikeRequestWithInvalidPostId(String method, long postId) throws Exception {

		String url = "/api/boards/free/posts/" + postId + "/like";

		mockMvc.perform(request(HttpMethod.valueOf(method), url).with(csrf())
			.with(authentication(createAuthentication(10L, "ROLE_MEMBER")))).andExpect(status().isBadRequest());

		verifyNoInteractions(postLikeService);
	}

	@Test
	@DisplayName("비밀글 접근이 거절되면 좋아요 요청은 403을 반환한다")
	void returnForbiddenWhenSecretAccessIsDenied() throws Exception {
		doThrow(new SecretPostAccessDeniedException()).when(postLikeService)
			.likePost("free", 100L, 10L, false, "wrong");

		mockMvc.perform(put(URL).header("X-Post-Password", "wrong")
			.with(csrf())
			.with(authentication(createAuthentication(10L, "ROLE_MEMBER")))).andExpect(status().isForbidden());

		verify(postLikeService).likePost("free", 100L, 10L, false, "wrong");
	}

	@Test
	@DisplayName("게시글이 없으면 좋아요 취소 요청은 404를 반환한다")
	void returnNotFoundWhenPostIsMissing() throws Exception {
		doThrow(new PostNotFoundException()).when(postLikeService).unlikePost("free", 100L, 10L, false, null);

		mockMvc.perform(
				delete(URL).with(csrf()).with(authentication(createAuthentication(10L, "ROLE_MEMBER"))))
			.andExpect(status().isNotFound());

		verify(postLikeService).unlikePost("free", 100L, 10L, false, null);
	}

	private Authentication createAuthentication(Long memberId, String authority) {
		return new UsernamePasswordAuthenticationToken(new LoginMember(memberId), null,
			List.of(new SimpleGrantedAuthority(authority)));
	}
}