package io.github.kwp6zgnrsh.boardkitapi.board.api;

import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.service.BoardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BoardApi.class)
@Import(BoardApiTest.SecurityTestConfig.class)
class BoardApiTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BoardService boardService;

	@Test
	@DisplayName("비로그인 사용자는 공개 게시판만 조회한다")
	void getPublicBoardsWithoutAuthentication() throws Exception {
		when(boardService.getAccessibleBoards(false, false))
			.thenReturn(List.of(
				board(1L, "자유게시판", "free")
			));

		mockMvc.perform(get("/api/boards"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(1))
			.andExpect(jsonPath("$[0].name").value("자유게시판"))
			.andExpect(jsonPath("$[0].slug").value("free"));

		verify(boardService)
			.getAccessibleBoards(false, false);
	}

	@Test
	@DisplayName("일반 회원은 공개 및 회원 게시판을 조회한다")
	void getMemberAccessibleBoards() throws Exception {
		when(boardService.getAccessibleBoards(true, false))
			.thenReturn(List.of(
				board(1L, "자유게시판", "free"),
				board(2L, "회원게시판", "member")
			));

		mockMvc.perform(get("/api/boards")
				.with(authentication(
					createAuthentication("ROLE_MEMBER")
				)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2));

		verify(boardService)
			.getAccessibleBoards(true, false);
	}

	@Test
	@DisplayName("관리자는 모든 활성 게시판을 조회한다")
	void getAllAccessibleBoardsWithAdminRole() throws Exception {
		when(boardService.getAccessibleBoards(true, true))
			.thenReturn(List.of(
				board(1L, "자유게시판", "free"),
				board(2L, "회원게시판", "member"),
				board(3L, "관리자게시판", "admin")
			));

		mockMvc.perform(get("/api/boards")
				.with(authentication(
					createAuthentication("ROLE_ADMIN")
				)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[2].slug").value("admin"));

		verify(boardService)
			.getAccessibleBoards(true, true);
	}

	@Test
	@DisplayName("조회 가능한 게시판이 없으면 빈 배열을 반환한다")
	void returnEmptyArrayWhenNoBoardIsAccessible() throws Exception {
		when(boardService.getAccessibleBoards(false, false))
			.thenReturn(List.of());

		mockMvc.perform(get("/api/boards"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));

		verify(boardService)
			.getAccessibleBoards(false, false);
	}

	private BoardSummaryResponse board(Long id, String name, String slug) {
		return new BoardSummaryResponse(
			id,
			name,
			slug,
			name + " 설명",
			"LIST",
			id.intValue()
		);
	}

	private Authentication createAuthentication(String authority) {
		return new UsernamePasswordAuthenticationToken(
			"test-member",
			null,
			List.of(new SimpleGrantedAuthority(authority))
		);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SecurityTestConfig {

		@Bean
		SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
			http.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(
					HttpMethod.GET,
					"/api/boards/**"
				).permitAll()
				.anyRequest().authenticated()
			);

			return http.build();
		}
	}
}