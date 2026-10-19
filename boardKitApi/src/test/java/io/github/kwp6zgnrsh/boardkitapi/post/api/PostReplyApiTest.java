package io.github.kwp6zgnrsh.boardkitapi.post.api;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.github.kwp6zgnrsh.boardkitapi.auth.mapper.RevokedTokenMapper;
import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtTokenProvider;
import io.github.kwp6zgnrsh.boardkitapi.config.MethodSecurityConfig;
import io.github.kwp6zgnrsh.boardkitapi.config.SecurityConfig;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostReplyCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostReplyUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostReplyResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostReplyConflictException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.SecretPostAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.post.service.PostReplyService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

@WebMvcTest(PostReplyApi.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class})
class PostReplyApiTest {

	private static final String URL = "/api/boards/free/posts/100/reply";
	private static final String ITEM_URL = URL + "/200";

	private static final String VALID_CREATE_REQUEST = """
		{
		  "contentDocument": {
		    "type": "doc",
		    "content": [
		      {
		        "type": "paragraph",
		        "content": [{"type": "text", "text": "공식 답변"}]
		      }
		    ]
		  }
		}
		""";

	private static final String VALID_UPDATE_REQUEST = """
		{
		  "contentDocument": {
		    "type": "doc",
		    "content": [
		      {
		        "type": "paragraph",
		        "content": [{"type": "text", "text": "공식 답변"}]
		      }
		    ]
		  },
		  "version": 0
		}
		""";

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
	@DisplayName("비로그인 사용자도 공개 게시글의 공식 답변을 조회할 수 있다")
	void getPublicReplyWithoutAuthentication() throws Exception {
		when(postReplyService.getPostReply("free", 100L, null, false, null)).thenReturn(Optional.of(replyResponse()));

		mockMvc.perform(get(URL))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.id").value(200))
			.andExpect(jsonPath("$.postId").value(100))
			.andExpect(jsonPath("$.authorDisplayName").value("관리자"))
			.andExpect(jsonPath("$.content").value("공식 답변"))
			.andExpect(jsonPath("$.version").value(0))
			.andExpect(jsonPath("$.contentDocument.type").value("doc"));

		verify(postReplyService).getPostReply("free", 100L, null, false, null);
	}

	@Test
	@DisplayName("회원 정보와 비밀글 비밀번호를 공식 답변 조회 서비스에 전달한다")
	void passMemberAndSecretPasswordToService() throws Exception {
		when(postReplyService.getPostReply("free", 100L, 10L, false, "secret")).thenReturn(
			Optional.of(replyResponse()));

		mockMvc.perform(
				get(URL).header("X-Post-Password", "secret").with(authentication(createAuthentication(10L, "ROLE_MEMBER"))))
			.andExpect(status().isOk());

		verify(postReplyService).getPostReply("free", 100L, 10L, false, "secret");
	}

	@Test
	@DisplayName("공식 답변이 없으면 본문 없는 204를 반환한다")
	void returnNoContentWhenReplyIsAbsent() throws Exception {
		when(postReplyService.getPostReply("free", 100L, null, false, null)).thenReturn(Optional.empty());

		mockMvc.perform(get(URL))
			.andExpect(status().isNoContent())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(content().string(""));
	}

	@Test
	@DisplayName("관리자는 공식 답변을 등록하고 조회 경로와 응답을 받는다")
	void createReplyWithAdminAuthentication() throws Exception {
		PostReplyCreateRequest request = new PostReplyCreateRequest(document());

		when(postReplyService.createPostReply("free", 100L, request, 1L, true)).thenReturn(replyResponse());

		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
				.content(VALID_CREATE_REQUEST)
				.with(csrf())
				.with(authentication(createAuthentication(1L, "ROLE_ADMIN"))))
			.andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.LOCATION, URL))
			.andExpect(jsonPath("$.id").value(200))
			.andExpect(jsonPath("$.version").value(0));

		verify(postReplyService).createPostReply("free", 100L, request, 1L, true);
	}

	@Test
	@DisplayName("관리자의 공식 답변 수정 요청을 서비스에 전달한다")
	void updateReplyWithAdminAuthentication() throws Exception {
		mockMvc.perform(patch(ITEM_URL).contentType(MediaType.APPLICATION_JSON)
			.content(VALID_UPDATE_REQUEST)
			.with(csrf())
			.with(authentication(createAuthentication(1L, "ROLE_ADMIN")))).andExpect(status().isNoContent());

		verify(postReplyService).updatePostReply("free", 100L, 200L, new PostReplyUpdateRequest(document(), 0), 1L,
			true);
	}

	@Test
	@DisplayName("관리자의 공식 답변 삭제 요청에 답변 ID와 버전을 전달한다")
	void deleteReplyWithAdminAuthentication() throws Exception {
		mockMvc.perform(delete(ITEM_URL).param("version", "3")
			.with(csrf())
			.with(authentication(createAuthentication(1L, "ROLE_ADMIN")))).andExpect(status().isNoContent());

		verify(postReplyService).deletePostReply("free", 100L, 200L, 3, 1L, true);
	}

	@Test
	@DisplayName("비로그인 사용자의 공식 답변 등록과 수정 및 삭제를 거절한다")
	void rejectChangesWithoutAuthentication() throws Exception {
		for (MockHttpServletRequestBuilder request : changeRequests()) {
			mockMvc.perform(request.with(csrf())).andExpect(status().isUnauthorized());
		}

		verifyNoInteractions(postReplyService);
	}

	@Test
	@DisplayName("일반 회원의 공식 답변 등록과 수정 및 삭제를 거절한다")
	void rejectChangesByMember() throws Exception {
		for (MockHttpServletRequestBuilder request : changeRequests()) {
			mockMvc.perform(request.with(csrf()).with(authentication(createAuthentication(10L, "ROLE_MEMBER"))))
				.andExpect(status().isForbidden());
		}

		verifyNoInteractions(postReplyService);
	}

	@Test
	@DisplayName("관리자라도 CSRF 토큰이 없으면 공식 답변 변경을 거절한다")
	void rejectChangesWithoutCsrfToken() throws Exception {
		for (MockHttpServletRequestBuilder request : changeRequests()) {
			mockMvc.perform(request.with(authentication(createAuthentication(1L, "ROLE_ADMIN"))))
				.andExpect(status().isForbidden());
		}

		verifyNoInteractions(postReplyService);
	}

	@Test
	@DisplayName("잘못된 CSRF 토큰을 가진 공식 답변 변경 요청을 거절한다")
	void rejectChangesWithInvalidCsrfToken() throws Exception {
		for (MockHttpServletRequestBuilder request : changeRequests()) {
			mockMvc.perform(
					request.with(csrf().useInvalidToken()).with(authentication(createAuthentication(1L, "ROLE_ADMIN"))))
				.andExpect(status().isForbidden());
		}

		verifyNoInteractions(postReplyService);
	}

	@Test
	@DisplayName("공식 답변 등록 요청의 문서가 없으면 서비스 호출 전에 거절한다")
	void rejectCreationWithoutDocument() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
			.content("{}")
			.with(csrf())
			.with(authentication(createAuthentication(1L, "ROLE_ADMIN")))).andExpect(status().isBadRequest());

		verifyNoInteractions(postReplyService);
	}

	@ParameterizedTest
	@ValueSource(strings = {"-1", "null"})
	@DisplayName("공식 답변 수정 버전이 없거나 음수이면 서비스 호출 전에 거절한다")
	void rejectUpdateWithInvalidVersion(String version) throws Exception {
		String request = VALID_UPDATE_REQUEST.replace("\"version\": 0", "\"version\": " + version);

		mockMvc.perform(patch(ITEM_URL).contentType(MediaType.APPLICATION_JSON)
			.content(request)
			.with(csrf())
			.with(authentication(createAuthentication(1L, "ROLE_ADMIN")))).andExpect(status().isBadRequest());

		verifyNoInteractions(postReplyService);
	}

	@Test
	@DisplayName("공식 답변 삭제 버전이 없으면 서비스 호출 전에 거절한다")
	void rejectDeletionWithoutVersion() throws Exception {
		mockMvc.perform(delete(ITEM_URL).with(csrf()).with(authentication(createAuthentication(1L, "ROLE_ADMIN"))))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(postReplyService);
	}

	@ParameterizedTest
	@ValueSource(longs = {0, -1})
	@DisplayName("게시글 ID가 양수가 아니면 공식 답변 조회를 거절한다")
	void rejectReadWithInvalidPostId(long postId) throws Exception {
		mockMvc.perform(get("/api/boards/free/posts/" + postId + "/reply")).andExpect(status().isBadRequest());

		verifyNoInteractions(postReplyService);
	}

	@ParameterizedTest
	@ValueSource(longs = {0, -1})
	@DisplayName("답변 ID가 양수가 아니면 공식 답변 수정을 거절한다")
	void rejectUpdateWithInvalidReplyId(long replyId) throws Exception {
		mockMvc.perform(patch(URL + "/" + replyId).contentType(MediaType.APPLICATION_JSON)
			.content(VALID_UPDATE_REQUEST)
			.with(csrf())
			.with(authentication(createAuthentication(1L, "ROLE_ADMIN")))).andExpect(status().isBadRequest());

		verifyNoInteractions(postReplyService);
	}

	@Test
	@DisplayName("원본 게시글이 없으면 공식 답변 조회는 404를 반환한다")
	void returnNotFoundWhenPostIsUnavailable() throws Exception {
		when(postReplyService.getPostReply("free", 100L, null, false, null)).thenThrow(new PostNotFoundException());

		mockMvc.perform(get(URL)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("비밀글 접근이 거절되면 공식 답변 조회는 403을 반환한다")
	void returnForbiddenWhenSecretAccessIsDenied() throws Exception {
		when(postReplyService.getPostReply("free", 100L, null, false, "wrong")).thenThrow(
			new SecretPostAccessDeniedException());

		mockMvc.perform(get(URL).header("X-Post-Password", "wrong")).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("공식 답변 수정 충돌은 409를 반환한다")
	void returnConflictWhenReplyHasChanged() throws Exception {
		PostReplyUpdateRequest request = new PostReplyUpdateRequest(document(), 0);

		doThrow(new PostReplyConflictException()).when(postReplyService)
			.updatePostReply("free", 100L, 200L, request, 1L, true);

		mockMvc.perform(patch(ITEM_URL).contentType(MediaType.APPLICATION_JSON)
			.content(VALID_UPDATE_REQUEST)
			.with(csrf())
			.with(authentication(createAuthentication(1L, "ROLE_ADMIN")))).andExpect(status().isConflict());
	}

	private List<MockHttpServletRequestBuilder> changeRequests() {
		return List.of(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_REQUEST),
			patch(ITEM_URL).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE_REQUEST),
			delete(ITEM_URL).param("version", "0"));
	}

	private Authentication createAuthentication(Long memberId, String authority) {
		return new UsernamePasswordAuthenticationToken(new LoginMember(memberId), null,
			List.of(new SimpleGrantedAuthority(authority)));
	}

	private PostReplyResponse replyResponse() {
		LocalDateTime time = LocalDateTime.of(2026, 10, 18, 0, 0);

		return new PostReplyResponse(200L, 100L, 1L, "관리자", "공식 답변", document(), 0, time, time);
	}

	private JsonNode document() {
		var root = JsonNodeFactory.instance.objectNode().put("type", "doc");
		var paragraph = root.putArray("content").addObject().put("type", "paragraph");

		paragraph.putArray("content").addObject().put("type", "text").put("text", "공식 답변");

		return root;
	}
}