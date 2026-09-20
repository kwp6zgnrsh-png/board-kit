package io.github.kwp6zgnrsh.boardkitapi.board.api;

import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.service.BoardService;
import io.github.kwp6zgnrsh.boardkitapi.config.MethodSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminBoardApi.class)
@Import(MethodSecurityConfig.class)
class AdminBoardApiTest {

	private static final String VALID_REQUEST = """
		{
		  "name": "자유게시판",
		  "slug": "free",
		  "description": "자유롭게 이야기하는 공간입니다.",
		  "displayType": "LIST",
		  "sortOrder": 0,
		  "policy": {
		    "readPermission": "PUBLIC",
		    "writePermission": "MEMBER",
		    "commentPermission": "MEMBER",
		    "secretMode": "OPTIONAL",
		    "categoriesEnabled": true,
		    "categoryRequired": false,
		    "attachmentsEnabled": false,
		    "uploadProfile": "IMAGE",
		    "maxFileCount": 0,
		    "maxFileSizeBytes": 0,
		    "imageRequired": false,
		    "thumbnailEnabled": false,
		    "representativeImageMode": "FIRST_IMAGE",
		    "commentsEnabled": true,
		    "commentRepliesEnabled": true,
		    "postLikesEnabled": true,
		    "commentLikesEnabled": true,
		    "officialReplyEnabled": false
		  },
		  "categories": [
		    {
		      "name": "잡담",
		      "sortOrder": 0
		    }
		  ]
		}
		""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BoardService boardService;

	@Test
	@DisplayName("관리자는 게시판을 생성할 수 있다")
	void createBoardWithAdminRole() throws Exception {
		when(boardService.createBoard(any(BoardCreateRequest.class), eq(1L)))
			.thenReturn(new BoardCreateResponse(1L, "자유게시판", "free"));

		mockMvc.perform(post("/api/admin/boards")
				.with(csrf())
				.with(authentication(createAuthentication(1L, "ROLE_ADMIN")))
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_REQUEST))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/api/boards/free"))
			.andExpect(jsonPath("$.id").value(1))
			.andExpect(jsonPath("$.name").value("자유게시판"))
			.andExpect(jsonPath("$.slug").value("free"));
	}

	@Test
	@DisplayName("비로그인 사용자는 게시판을 생성할 수 없다")
	void rejectBoardCreationWithoutAuthentication() throws Exception {
		mockMvc.perform(post("/api/admin/boards")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_REQUEST))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(boardService);
	}

	@Test
	@DisplayName("일반 회원은 게시판을 생성할 수 없다")
	void rejectBoardCreationWithMemberRole() throws Exception {
		mockMvc.perform(post("/api/admin/boards")
				.with(csrf())
				.with(authentication(createAuthentication(2L, "ROLE_MEMBER")))
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_REQUEST))
			.andExpect(status().isForbidden());

		verifyNoInteractions(boardService);
	}

	@Test
	@DisplayName("잘못된 게시판 생성 요청은 거절한다")
	void rejectInvalidBoardCreationRequest() throws Exception {
		String invalidRequest = VALID_REQUEST.replace("\"free\"", "\"Free Board\"");

		mockMvc.perform(post("/api/admin/boards")
				.with(csrf())
				.with(authentication(createAuthentication(1L, "ROLE_ADMIN")))
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(boardService);
	}

	private Authentication createAuthentication(Long memberId, String authority) {
		return new UsernamePasswordAuthenticationToken(
			new LoginMember(memberId),
			null,
			List.of(new SimpleGrantedAuthority(authority))
		);
	}
}
