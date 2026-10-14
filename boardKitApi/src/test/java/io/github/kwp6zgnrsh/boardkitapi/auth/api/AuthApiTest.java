package io.github.kwp6zgnrsh.boardkitapi.auth.api;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.github.kwp6zgnrsh.boardkitapi.auth.cookie.AuthCookieFactory;
import io.github.kwp6zgnrsh.boardkitapi.auth.dto.request.LoginRequest;
import io.github.kwp6zgnrsh.boardkitapi.auth.dto.response.CurrentMemberResponse;
import io.github.kwp6zgnrsh.boardkitapi.auth.exception.InvalidCredentialsException;
import io.github.kwp6zgnrsh.boardkitapi.auth.mapper.RevokedTokenMapper;
import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginResult;
import io.github.kwp6zgnrsh.boardkitapi.auth.service.AuthService;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtPayload;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtTokenProvider;
import io.github.kwp6zgnrsh.boardkitapi.config.SecurityConfig;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;

@WebMvcTest(controllers = AuthApi.class, properties = "boardkit.auth.cookie-secure=true")
@Import({SecurityConfig.class, AuthCookieFactory.class})
class AuthApiTest {

	private static final String VALID_REQUEST = """
		{
		  "loginId": "admin",
		  "password": "1234"
		}
		""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private RevokedTokenMapper revokedTokenMapper;

	@MockitoSpyBean
	private AuthCookieFactory authCookieFactory;

	@Test
	@DisplayName("로그인 성공 시 JWT는 쿠키로 발급하고 본문에는 회원 정보만 반환한다")
	void issueCookieWithoutExposingTokenInResponseBody() throws Exception {
		when(authService.login(any(LoginRequest.class))).thenReturn(
			new LoginResult("access-token", Instant.now().plusSeconds(1800), 1L, "관리자", "ADMIN"));

		// 성공 테스트와 로그인 실패 테스트
		mockMvc.perform(loginRequest(VALID_REQUEST))
			.andExpect(status().isOk())
			.andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(
				allOf(containsString("boardkit_access_token=access-token"), containsString("Path=/api"),
					containsString("Max-Age="), containsString("HttpOnly"), containsString("Secure"),
					containsString("SameSite=Lax")))))
			.andExpect(jsonPath("$.memberId").value(1))
			.andExpect(jsonPath("$.displayName").value("관리자"))
			.andExpect(jsonPath("$.role").value("ADMIN"))
			.andExpect(jsonPath("$.accessToken").doesNotExist())
			.andExpect(jsonPath("$.tokenType").doesNotExist());
	}

	@Test
	@DisplayName("로그인 실패 시 인증 쿠키를 발급하지 않는다")
	void rejectInvalidCredentialsWithoutIssuingCookie() throws Exception {
		when(authService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

		mockMvc.perform(loginRequest(VALID_REQUEST))
			.andExpect(status().isUnauthorized())
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	@Test
	@DisplayName("잘못된 로그인 요청은 서비스 호출 전에 거절한다")
	void rejectInvalidRequestBeforeCallingService() throws Exception {
		String invalidRequest = VALID_REQUEST.replace("\"admin\"", "\"\"");

		mockMvc.perform(loginRequest(invalidRequest))
			.andExpect(status().isBadRequest())
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

		verifyNoInteractions(authService);
	}

	@Test
	@DisplayName("비로그인 사용자는 CSRF 쿠키를 발급받을 수 있다")
	void issueCsrfCookieWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/auth/csrf"))
			.andExpect(status().isNoContent())
			.andExpect(cookie().exists("XSRF-TOKEN"))
			.andExpect(cookie().path("XSRF-TOKEN", "/"))
			.andExpect(cookie().secure("XSRF-TOKEN", true))
			.andExpect(cookie().httpOnly("XSRF-TOKEN", false))
			.andExpect(cookie().attribute("XSRF-TOKEN", "SameSite", "Lax"));

		verifyNoInteractions(authService, jwtTokenProvider, revokedTokenMapper);
	}

	@Test
	@DisplayName("CSRF 헤더가 없으면 로그인 요청을 서비스 호출 전에 거절한다")
	void rejectLoginWithoutCsrfHeader() throws Exception {
		mockMvc.perform(post("/api/auth/login").cookie(new Cookie("XSRF-TOKEN", "test-csrf-token"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_REQUEST))
			.andExpect(status().isForbidden())
			.andExpect(
				header().stringValues(HttpHeaders.SET_COOKIE, not(hasItem(startsWith("boardkit_access_token=")))));

		verifyNoInteractions(authService);
	}

	@Test
	@DisplayName("로그아웃하면 현재 JWT를 무효화하고 인증 쿠키를 만료시킨다")
	void logoutAndExpireAuthenticationCookie() throws Exception {
		JwtPayload payload = prepareLogoutAuthentication();

		mockMvc.perform(logoutRequest())
			.andExpect(status().isNoContent())
			.andExpect(cookie().value("boardkit_access_token", ""))
			.andExpect(cookie().maxAge("boardkit_access_token", 0))
			.andExpect(cookie().path("boardkit_access_token", "/api"))
			.andExpect(cookie().httpOnly("boardkit_access_token", true))
			.andExpect(cookie().secure("boardkit_access_token", true))
			.andExpect(cookie().attribute("boardkit_access_token", "SameSite", "Lax"));

		verify(authService).logout(payload);
	}

	@Test
	@DisplayName("인증 쿠키가 없으면 로그아웃 요청을 거절한다")
	void rejectLogoutWithoutAuthentication() throws Exception {
		mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("XSRF-TOKEN", "test-csrf-token"))
			.header("X-XSRF-TOKEN", "test-csrf-token")).andExpect(status().isUnauthorized());

		verifyNoInteractions(authService, jwtTokenProvider, revokedTokenMapper);
		verify(authCookieFactory, never()).expireAccessTokenCookie();
	}

	@Test
	@DisplayName("CSRF 헤더가 없으면 로그아웃 요청을 거절한다")
	void rejectLogoutWithoutCsrfHeader() throws Exception {
		mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("boardkit_access_token", "logout-token"),
			new Cookie("XSRF-TOKEN", "test-csrf-token"))).andExpect(status().isForbidden());

		verifyNoInteractions(authService);
		verify(authCookieFactory, never()).expireAccessTokenCookie();
	}

	@Test
	@DisplayName("이미 무효화된 JWT로는 로그아웃 API에 접근할 수 없다")
	void rejectLogoutWithRevokedToken() throws Exception {
		JwtPayload payload = prepareLogoutAuthentication();
		when(revokedTokenMapper.existsRevokedToken(payload.tokenId())).thenReturn(true);

		mockMvc.perform(logoutRequest()).andExpect(status().isUnauthorized());

		verifyNoInteractions(authService);
		verify(authCookieFactory, never()).expireAccessTokenCookie();
	}

	@Test
	@DisplayName("무효화 기록 저장에 실패하면 인증 쿠키를 만료시키지 않는다")
	void preserveCookieWhenRevocationStorageFails() {
		JwtPayload payload = prepareLogoutAuthentication();
		doThrow(new DataAccessResourceFailureException("DB 연결 실패")).when(authService).logout(payload);

		ServletException exception = assertThrows(ServletException.class, () -> mockMvc.perform(logoutRequest()));

		assertInstanceOf(DataAccessResourceFailureException.class, exception.getCause());
		verify(authCookieFactory, never()).expireAccessTokenCookie();
	}

	@Test
	@DisplayName("인증 쿠키로 현재 회원 정보를 조회한다")
	void getCurrentMemberWithAuthenticationCookie() throws Exception {
		prepareCurrentMemberAuthentication();
		when(authService.getCurrentMember(10L)).thenReturn(new CurrentMemberResponse(10L, "회원닉네임", "MEMBER"));

		mockMvc.perform(currentMemberRequest())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.memberId").value(10))
			.andExpect(jsonPath("$.displayName").value("회원닉네임"))
			.andExpect(jsonPath("$.role").value("MEMBER"))
			.andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andExpect(jsonPath("$.accessToken").doesNotExist())
			.andExpect(jsonPath("$.tokenId").doesNotExist());

		verify(authService).getCurrentMember(10L);
	}

	@Test
	@DisplayName("인증 쿠키가 없으면 현재 회원 조회를 거절한다")
	void rejectCurrentMemberRequestWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

		verifyNoInteractions(authService, jwtTokenProvider, revokedTokenMapper);
	}

	@Test
	@DisplayName("무효화된 JWT로는 현재 회원 정보를 조회할 수 없다")
	void rejectCurrentMemberRequestWithRevokedToken() throws Exception {
		JwtPayload payload = prepareCurrentMemberAuthentication();
		when(revokedTokenMapper.existsRevokedToken(payload.tokenId())).thenReturn(true);

		mockMvc.perform(currentMemberRequest()).andExpect(status().isUnauthorized());

		verifyNoInteractions(authService);
	}

	@Test
	@DisplayName("JWT가 유효해도 삭제된 회원이면 현재 회원 조회를 거절한다")
	void rejectCurrentMemberRequestForUnavailableMember() throws Exception {
		prepareCurrentMemberAuthentication();
		when(authService.getCurrentMember(10L)).thenThrow(new MemberUnavailableException());

		mockMvc.perform(currentMemberRequest()).andExpect(status().isUnauthorized());

		verify(authService).getCurrentMember(10L);
	}

	private JwtPayload prepareLogoutAuthentication() {
		JwtPayload payload = new JwtPayload(10L, "MEMBER", "00000000-0000-0000-0000-000000000001",
			Instant.now().plusSeconds(1800));

		when(jwtTokenProvider.parseAccessToken("logout-token")).thenReturn(payload);

		return payload;
	}

	private MockHttpServletRequestBuilder logoutRequest() {
		return post("/api/auth/logout").cookie(new Cookie(AuthCookieFactory.ACCESS_TOKEN_COOKIE_NAME, "logout-token"),
			new Cookie("XSRF-TOKEN", "test-csrf-token")).header("X-XSRF-TOKEN", "test-csrf-token");
	}

	private MockHttpServletRequestBuilder loginRequest(String body) {
		return post("/api/auth/login").cookie(new Cookie("XSRF-TOKEN", "test-csrf-token"))
			.header("X-XSRF-TOKEN", "test-csrf-token")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

	private MockHttpServletRequestBuilder currentMemberRequest() {
		return get("/api/auth/me").cookie(new Cookie(AuthCookieFactory.ACCESS_TOKEN_COOKIE_NAME, "member-token"));
	}

	private JwtPayload prepareCurrentMemberAuthentication() {
		JwtPayload payload = new JwtPayload(10L, "MEMBER", "00000000-0000-0000-0000-000000000001",
			Instant.now().plusSeconds(1800));

		when(jwtTokenProvider.parseAccessToken("member-token")).thenReturn(payload);

		return payload;
	}
}