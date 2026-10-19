package io.github.kwp6zgnrsh.boardkitapi.comment.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.comment.dto.request.CommentCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.comment.dto.response.CommentCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.comment.dto.response.CommentItemResponse;
import io.github.kwp6zgnrsh.boardkitapi.comment.dto.response.CommentPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.comment.service.CommentService;

@WebMvcTest(CommentApi.class)
@Import(CommentApiTest.SecurityTestConfig.class)
class CommentApiTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CommentService commentService;

	@TestConfiguration(proxyBeanMethods = false)
	static class SecurityTestConfig {
		@Bean
		SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
			return http
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.exceptionHandling(exception -> exception
					.authenticationEntryPoint((request, response, cause) ->
						response.setStatus(HttpStatus.UNAUTHORIZED.value())
					)
				)
				.authorizeHttpRequests(authorize -> authorize
					.requestMatchers(HttpMethod.GET, "/api/boards/**").permitAll()
					.anyRequest().authenticated()
				)
				.build();
		}
	}

	@Test
	@DisplayName("로그인한 회원은 댓글을 작성할 수 있다")
	void createCommentWithMemberAuthentication() throws Exception {
		when(commentService.createComment(
			eq("free"), eq(100L), any(CommentCreateRequest.class),
			eq(10L), eq(false), isNull()
		)).thenReturn(new CommentCreateResponse(50L));

		mockMvc.perform(post("/api/boards/free/posts/100/comments")
				.with(authentication(member(10L)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
                    {"content":"첫 댓글","parentId":null}
                    """))
			.andExpect(status().isCreated())
			.andExpect(header().string(
				"Location", "/api/boards/free/posts/100/comments/50"
			))
			.andExpect(jsonPath("$.id").value(50));
	}

	@Test
	@DisplayName("비로그인 사용자는 댓글을 작성할 수 없다")
	void rejectCommentCreationWithoutAuthentication() throws Exception {
		mockMvc.perform(post("/api/boards/free/posts/100/comments")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
                    {"content":"첫 댓글","parentId":null}
                    """))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(commentService);
	}

	@Test
	@DisplayName("비로그인 사용자도 공개 게시글의 댓글을 조회할 수 있다")
	void getCommentsWithoutAuthentication() throws Exception {
		CommentItemResponse item = new CommentItemResponse(
			50L, null, 10L, "작성자", "첫 댓글", false, 0,
			LocalDateTime.of(2026, 9, 25, 10, 0),
			LocalDateTime.of(2026, 9, 25, 10, 0),
			3L, false, false
		);

		when(commentService.getComments(
			"free", 100L, 1, 20, null, false, false, null
		)).thenReturn(new CommentPageResponse(List.of(item), 1, 20, 1, 1));

		mockMvc.perform(get("/api/boards/free/posts/100/comments"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.comments.length()").value(1))
			.andExpect(jsonPath("$.comments[0].content").value("첫 댓글"))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.comments[0].likeCount").value(3))
			.andExpect(jsonPath("$.comments[0].likedByMe").value(false));

		verify(commentService).getComments(
			"free", 100L, 1, 20, null, false, false, null
		);
	}

	@Test
	@DisplayName("댓글 페이지 조건과 비밀글 비밀번호를 서비스에 전달한다")
	void passCommentPageQueryAndPasswordToService() throws Exception {
		when(commentService.getComments(
			"free", 100L, 2, 5, 10L,
			true, false, "secret"
		)).thenReturn(new CommentPageResponse(List.of(), 2, 5, 0, 0));

		mockMvc.perform(get("/api/boards/free/posts/100/comments")
				.param("page", "2")
				.param("size", "5")
				.header("X-Post-Password", "secret")
				.with(authentication(member(10L))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.comments.length()").value(0));

		verify(commentService).getComments(
			"free", 100L, 2, 5, 10L,
			true, false, "secret"
		);
	}

	@Test
	@DisplayName("로그인한 작성자의 댓글 수정 요청은 204를 반환한다")
	void updateCommentWithMemberAuthentication() throws Exception {
		mockMvc.perform(patch("/api/boards/free/posts/100/comments/50")
				.header("X-Post-Password", "secret")
				.with(authentication(member(10L)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
                {
                  "content": "수정된 댓글",
                  "version": 0
                }
                """))
			.andExpect(status().isNoContent());

		verify(commentService).updateComment(
			eq("free"),
			eq(100L),
			eq(50L),
			argThat(request ->
				request.content().equals("수정된 댓글")
					&& request.version().equals(0)
			),
			eq(10L),
			eq(false),
			eq("secret")
		);
	}

	@Test
	@DisplayName("비로그인 사용자는 댓글을 수정할 수 없다")
	void rejectCommentUpdateWithoutAuthentication() throws Exception {
		mockMvc.perform(patch("/api/boards/free/posts/100/comments/50")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
                {
                  "content": "수정된 댓글",
                  "version": 0
                }
                """))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(commentService);
	}

	@Test
	@DisplayName("음수 버전으로 댓글을 수정할 수 없다")
	void rejectCommentUpdateWithNegativeVersion() throws Exception {
		mockMvc.perform(patch("/api/boards/free/posts/100/comments/50")
				.with(authentication(member(10L)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
                {
                  "content": "수정된 댓글",
                  "version": -1
                }
                """))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(commentService);
	}

	@Test
	@DisplayName("로그인한 작성자의 댓글 삭제 요청은 204를 반환한다")
	void deleteCommentWithMemberAuthentication() throws Exception {
		mockMvc.perform(delete(
				"/api/boards/free/posts/100/comments/50"
			)
				.param("version", "2")
				.with(authentication(member(10L))))
			.andExpect(status().isNoContent());

		verify(commentService).deleteComment(
			"free",
			100L,
			50L,
			2,
			10L,
			false
		);
	}

	@Test
	@DisplayName("비로그인 사용자는 댓글을 삭제할 수 없다")
	void rejectCommentDeletionWithoutAuthentication() throws Exception {
		mockMvc.perform(delete(
				"/api/boards/free/posts/100/comments/50"
			)
				.param("version", "0"))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(commentService);
	}

	@Test
	@DisplayName("음수 버전으로 댓글을 삭제할 수 없다")
	void rejectCommentDeletionWithNegativeVersion() throws Exception {
		mockMvc.perform(delete(
				"/api/boards/free/posts/100/comments/50"
			)
				.param("version", "-1")
				.with(authentication(member(10L))))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(commentService);
	}

	private Authentication member(Long id) {
		return new UsernamePasswordAuthenticationToken(
			new LoginMember(id),
			null,
			List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
		);
	}
}