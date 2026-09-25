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
import org.springframework.http.HttpHeaders;
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
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostListRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostSort;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.SecretPostAccessDeniedException;
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

	private static final String VALID_UPDATE_REQUEST = """
    {
      "title": "수정 제목",
      "content": "수정 본문",
      "categoryId": null,
      "isSecret": false,
      "secretPassword": null,
      "clearSecretPassword": false,
      "commentsLocked": false,
      "version": 0
    }
    """;

	private static final PostListRequest DEFAULT_LIST_REQUEST =
		new PostListRequest(null, null, PostSort.LATEST);

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
			DEFAULT_LIST_REQUEST,
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
			DEFAULT_LIST_REQUEST,
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

		PostListRequest request =
			new PostListRequest("spring", 2L, PostSort.VIEWS);

		when(postService.getPosts(
			"admin",
			2,
			5,
			request,
			true,
			true
		)).thenReturn(response);

		mockMvc.perform(get("/api/boards/admin/posts")
			.param("page", "2")
			.param("size", "5")
			.param("keyword", " spring ")
			.param("categoryId", "2")
			.param("sort", "VIEWS")
			.with(authentication(
				createAuthentication(1L, "ROLE_ADMIN")
			)));

		verify(postService).getPosts(
			"admin",
			2,
			5,
			request,
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

	@Test
	@DisplayName("비로그인 사용자는 공개 게시글 상세를 조회할 수 있다")
	void getPublicPostDetailWithoutAuthentication() throws Exception {
		PostDetailResponse response = postDetailResponse();

		when(postService.getPostDetail(
			"free",
			100L,
			null,
			false,
			false,
			null
		)).thenReturn(response);

		mockMvc.perform(
				get("/api/boards/free/posts/100")
			)
			.andExpect(status().isOk())
			.andExpect(header().string(
				HttpHeaders.CACHE_CONTROL,
				"no-store"
			))
			.andExpect(jsonPath("$.id").value(100))
			.andExpect(jsonPath("$.title").value("게시글 제목"))
			.andExpect(jsonPath("$.content").value("게시글 본문"))
			.andExpect(jsonPath("$.authorDisplayName").value("작성자"))
			.andExpect(jsonPath("$.viewCount").value(6))
			.andExpect(jsonPath("$.version").value(0));

		verify(postService).getPostDetail(
			"free",
			100L,
			null,
			false,
			false,
			null
		);
	}

	@Test
	@DisplayName("비밀글 비밀번호를 서비스에 전달한다")
	void passSecretPasswordToService() throws Exception {
		PostDetailResponse response = postDetailResponse();

		when(postService.getPostDetail(
			"free",
			100L,
			null,
			false,
			false,
			"1234"
		)).thenReturn(response);

		mockMvc.perform(
				get("/api/boards/free/posts/100")
					.header("X-Post-Password", "1234")
			)
			.andExpect(status().isOk());

		verify(postService).getPostDetail(
			"free",
			100L,
			null,
			false,
			false,
			"1234"
		);
	}

	@Test
	@DisplayName("비밀글 비밀번호가 일치하지 않으면 403을 반환한다")
	void rejectPostDetailWithWrongPassword() throws Exception {
		when(postService.getPostDetail(
			"free",
			100L,
			null,
			false,
			false,
			"wrong-password"
		)).thenThrow(new SecretPostAccessDeniedException());

		mockMvc.perform(
				get("/api/boards/free/posts/100")
					.header(
						"X-Post-Password",
						"wrong-password"
					)
			)
			.andExpect(status().isForbidden());
	}

	private PostDetailResponse postDetailResponse() {
		LocalDateTime createdAt = LocalDateTime.of(2026, 9, 21, 10, 0);

		return new PostDetailResponse(
			100L,
			1L,
			"게시글 제목",
			"게시글 본문",
			10L,
			"작성자",
			null,
			null,
			false,
			false,
			false,
			6L,
			0,
			createdAt,
			createdAt
		);
	}

	@Test
	@DisplayName("로그인한 작성자의 게시글 수정 요청은 204를 반환한다")
	void updatePostWithMemberAuthentication() throws Exception {
		mockMvc.perform(patch("/api/boards/free/posts/100")
				.with(authentication(
					createAuthentication(10L, "ROLE_MEMBER")
				))
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_UPDATE_REQUEST))
			.andExpect(status().isNoContent());

		verify(postService).updatePost(
			eq("free"),
			eq(100L),
			any(PostUpdateRequest.class),
			eq(10L),
			eq(false)
		);
	}

	@Test
	@DisplayName("잘못된 게시글 수정 요청은 서비스 호출 전에 거절한다")
	void rejectInvalidPostUpdateRequest() throws Exception {
		String invalidRequest = VALID_UPDATE_REQUEST.replace("수정 제목", "");

		mockMvc.perform(patch("/api/boards/free/posts/100")
				.with(authentication(
					createAuthentication(10L, "ROLE_MEMBER")
				))
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(postService);
	}

	@Test
	@DisplayName("비로그인 사용자는 게시글을 수정할 수 없다")
	void rejectPostUpdateWithoutAuthentication() throws Exception {
		mockMvc.perform(patch("/api/boards/free/posts/100")
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_UPDATE_REQUEST))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(postService);
	}

	@Test
	@DisplayName("관리자의 게시글 삭제 요청은 204를 반환한다")
	void deletePostWithAdminAuthentication() throws Exception {
		mockMvc.perform(delete("/api/boards/free/posts/100")
				.param("version", "0")
				.with(authentication(
					createAuthentication(1L, "ROLE_ADMIN")
				)))
			.andExpect(status().isNoContent());

		verify(postService).deletePost(
			"free", 100L, 0, 1L, true
		);
	}

	@Test
	@DisplayName("비로그인 사용자는 게시글을 삭제할 수 없다")
	void rejectPostDeletionWithoutAuthentication() throws Exception {
		mockMvc.perform(delete("/api/boards/free/posts/100")
				.param("version", "0"))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(postService);
	}

	@Test
	@DisplayName("지원하지 않는 게시글 정렬값은 거절한다")
	void rejectUnsupportedPostSort() throws Exception {
		mockMvc.perform(get("/api/boards/free/posts")
				.param("sort", "POPULAR"))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(postService);
	}

	@Test
	@DisplayName("100자를 초과한 게시글 검색어는 거절한다")
	void rejectKeywordLongerThanOneHundredCharacters() throws Exception {
		mockMvc.perform(get("/api/boards/free/posts")
				.param("keyword", "a".repeat(101)))
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