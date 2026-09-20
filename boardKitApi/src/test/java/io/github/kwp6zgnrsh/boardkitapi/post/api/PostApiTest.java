package io.github.kwp6zgnrsh.boardkitapi.post.api;

import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.service.PostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostApi.class)
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

	private Authentication createAuthentication(Long memberId, String authority) {
		return new UsernamePasswordAuthenticationToken(
			new LoginMember(memberId),
			null,
			List.of(new SimpleGrantedAuthority(authority))
		);
	}
}