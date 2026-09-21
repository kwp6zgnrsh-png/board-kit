package io.github.kwp6zgnrsh.boardkitapi.post.api;

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
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.service.PostService;

@WebMvcTest(PostApi.class)
@Import(PostApiTest.SecurityTestConfig.class)
class PostApiTest {

	private static final String VALID_REQUEST = """
        {
          "title": "게시글 제목",
          "content": "게시글 본문",
          "categoryId": null,
          "isSecret": false,
          "secretPassword": null,
          "commentsLocked": false
        }
        """;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PostService postService;

	@TestConfiguration(proxyBeanMethods = false)
	static class SecurityTestConfig {

		@Bean
		SecurityFilterChain securityFilterChain(
			HttpSecurity http
		) throws Exception {
			return http
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)

				.exceptionHandling(exception -> exception
					.authenticationEntryPoint(
						(request, response, cause) ->
							response.setStatus(
								HttpStatus.UNAUTHORIZED.value()
							)
					)
				)

				.authorizeHttpRequests(authorize -> authorize
					.requestMatchers(
						HttpMethod.GET,
						"/api/boards/**"
					).permitAll()
					.anyRequest().authenticated()
				)

				.build();
		}
	}

	@Test
	@DisplayName("로그인한 회원은 게시글을 작성할 수 있다")
	void createPostWithMemberAuthentication() throws Exception {
		when(postService.createPost(
			eq("free"),
			any(PostCreateRequest.class),
			eq(10L),
			eq(false)
		)).thenReturn(new PostCreateResponse(100L));

		mockMvc.perform(post("/api/boards/free/posts")
				.with(csrf())
				.with(authentication(
					createAuthentication(10L, "ROLE_MEMBER")
				))
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_REQUEST))
			.andExpect(status().isCreated())
			.andExpect(header().string(
				"Location",
				"/api/boards/free/posts/100"
			))
			.andExpect(jsonPath("$.id").value(100));

		verify(postService).createPost(
			eq("free"),
			any(PostCreateRequest.class),
			eq(10L),
			eq(false)
		);
	}

	@Test
	@DisplayName("관리자 권한 여부를 서비스에 전달한다")
	void passAdminAccessToService() throws Exception {
		when(postService.createPost(
			eq("free"),
			any(PostCreateRequest.class),
			eq(1L),
			eq(true)
		)).thenReturn(new PostCreateResponse(101L));

		mockMvc.perform(post("/api/boards/free/posts")
				.with(csrf())
				.with(authentication(
					createAuthentication(1L, "ROLE_ADMIN")
				))
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_REQUEST))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(101));

		verify(postService).createPost(
			eq("free"),
			any(PostCreateRequest.class),
			eq(1L),
			eq(true)
		);
	}

	@Test
	@DisplayName("비로그인 사용자는 게시글을 작성할 수 없다")
	void rejectPostCreationWithoutAuthentication() throws Exception {
		mockMvc.perform(post("/api/boards/free/posts")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_REQUEST))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(postService);
	}

	@Test
	@DisplayName("잘못된 게시글 작성 요청은 거절한다")
	void rejectInvalidPostCreationRequest() throws Exception {
		String invalidRequest = VALID_REQUEST.replace("게시글 제목", "");

		mockMvc.perform(post("/api/boards/free/posts")
				.with(csrf())
				.with(authentication(
					createAuthentication(10L, "ROLE_MEMBER")
				))
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(postService);
	}

	@Test
	@DisplayName("비로그인 사용자는 공개 게시판 목록을 조회할 수 있다")
	void getPublicBoardPostsWithoutAuthentication() throws Exception {
		PostSummaryResponse post = new PostSummaryResponse(
			1L,
			"게시글 제목",
			10L,
			"작성자",
			null,
			null,
			false,
			false,
			3L,
			2L,
			LocalDateTime.of(2026, 9, 21, 10, 0)
		);

		PostPageResponse response = new PostPageResponse(
			List.of(post),
			1,
			10,
			1L,
			1
		);

		when(postService.getPosts(
			"free",
			1,
			10,
			false,
			false
		)).thenReturn(response);

		mockMvc.perform(get("/api/boards/free/posts"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts.length()").value(1))
			.andExpect(jsonPath("$.posts[0].id").value(1))
			.andExpect(jsonPath("$.posts[0].title").value("게시글 제목"))
			.andExpect(jsonPath("$.posts[0].authorDisplayName").value("작성자"))
			.andExpect(jsonPath("$.posts[0].viewCount").value(3))
			.andExpect(jsonPath("$.posts[0].commentCount").value(2))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.size").value(10))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.totalPages").value(1));

		verify(postService).getPosts(
			"free",
			1,
			10,
			false,
			false
		);
	}

	@Test
	@DisplayName("관리자는 관리자 권한으로 게시글 목록을 조회한다")
	void getPostsWithAdminAuthentication() throws Exception {
		PostPageResponse response = new PostPageResponse(
			List.of(),
			2,
			5,
			0L,
			0
		);

		when(postService.getPosts(
			"admin",
			2,
			5,
			true,
			true
		)).thenReturn(response);

		mockMvc.perform(get("/api/boards/admin/posts")
				.param("page", "2")
				.param("size", "5")
				.with(authentication(
					createAuthentication(1L, "ROLE_ADMIN")
				)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.posts.length()").value(0))
			.andExpect(jsonPath("$.page").value(2))
			.andExpect(jsonPath("$.size").value(5));

		verify(postService).getPosts(
			"admin",
			2,
			5,
			true,
			true
		);
	}

	@Test
	@DisplayName("페이지 번호가 1보다 작으면 요청을 거절한다")
	void rejectPageNumberBelowOne() throws Exception {
		mockMvc.perform(get("/api/boards/free/posts")
				.param("page", "0"))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(postService);
	}

	private Authentication createAuthentication(Long memberId, String authority) {
		return new UsernamePasswordAuthenticationToken(
			new LoginMember(memberId),
			null,
			List.of(new SimpleGrantedAuthority(authority))
		);
	}
}