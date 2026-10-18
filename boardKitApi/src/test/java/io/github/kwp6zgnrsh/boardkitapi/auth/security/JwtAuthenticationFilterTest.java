package io.github.kwp6zgnrsh.boardkitapi.auth.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import io.github.kwp6zgnrsh.boardkitapi.auth.cookie.AuthCookieFactory;
import io.github.kwp6zgnrsh.boardkitapi.auth.mapper.RevokedTokenMapper;
import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtPayload;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtTokenProvider;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private RevokedTokenMapper revokedTokenMapper;

	@Mock
	private MemberMapper memberMapper;

	private JwtAuthenticationFilter filter;
	private MockHttpServletResponse response;
	private MockFilterChain chain;

	@BeforeEach
	void setUp() {
		SecurityContextHolder.clearContext();
		filter = new JwtAuthenticationFilter(jwtTokenProvider, revokedTokenMapper, memberMapper);
		response = new MockHttpServletResponse();
		chain = new MockFilterChain();
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@ParameterizedTest
	@ValueSource(strings = {"MEMBER", "ADMIN"})
	@DisplayName("유효한 쿠키 JWT로 회원 ID와 권한을 인증한다")
	void authenticateValidCookieToken(String role) throws Exception {
		MockHttpServletRequest request = cookieRequest("valid-token");
		JwtPayload expectedPayload = payload(role);

		when(jwtTokenProvider.parseAccessToken("valid-token")).thenReturn(expectedPayload);
		when(revokedTokenMapper.existsRevokedToken("token-id")).thenReturn(false);
		when(memberMapper.existsActiveMemberByIdAndAuthVersion(10L, 0L)).thenReturn(true);

		filter.doFilter(request, response, chain);

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		assert authentication != null;
		assertTrue(authentication.isAuthenticated());
		assertEquals(new LoginMember(10L), authentication.getPrincipal());
		assertEquals("ROLE_" + role, authentication.getAuthorities().iterator().next().getAuthority());
		assertSame(request, chain.getRequest());
		assertSame(expectedPayload, authentication.getDetails());
		verify(revokedTokenMapper).existsRevokedToken("token-id");
		verify(memberMapper).existsActiveMemberByIdAndAuthVersion(10L, 0L);
	}

	@Test
	@DisplayName("무효화된 JWT는 인증하지 않는다")
	void rejectRevokedToken() throws Exception {
		MockHttpServletRequest request = cookieRequest("revoked-token");
		when(jwtTokenProvider.parseAccessToken("revoked-token")).thenReturn(payload("MEMBER"));
		when(revokedTokenMapper.existsRevokedToken("token-id")).thenReturn(true);

		filter.doFilter(request, response, chain);

		assertNull(SecurityContextHolder.getContext().getAuthentication());
		assertSame(request, chain.getRequest());
	}

	@Test
	@DisplayName("잘못된 JWT는 인증하지 않고 무효화 기록을 조회하지 않는다")
	void rejectInvalidTokenBeforeCheckingRevocation() throws Exception {
		MockHttpServletRequest request = cookieRequest("invalid-token");
		when(jwtTokenProvider.parseAccessToken("invalid-token")).thenThrow(new JwtException("잘못된 토큰"));

		filter.doFilter(request, response, chain);

		assertNull(SecurityContextHolder.getContext().getAuthentication());
		assertSame(request, chain.getRequest());
		verifyNoInteractions(revokedTokenMapper);
	}

	@Test
	@DisplayName("인증 쿠키가 없으면 토큰과 DB를 조회하지 않는다")
	void skipAuthenticationWithoutCookie() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/boards");

		filter.doFilter(request, response, chain);

		assertNull(SecurityContextHolder.getContext().getAuthentication());
		assertSame(request, chain.getRequest());
		verifyNoInteractions(jwtTokenProvider, revokedTokenMapper);
	}

	@Test
	@DisplayName("Bearer 헤더만으로는 인증하지 않는다")
	void ignoreBearerHeaderWithoutCookie() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/boards");
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer header-token");

		filter.doFilter(request, response, chain);

		assertNull(SecurityContextHolder.getContext().getAuthentication());
		assertSame(request, chain.getRequest());
		verifyNoInteractions(jwtTokenProvider, revokedTokenMapper);
	}

	@Test
	@DisplayName("무효화 기록 조회에 실패하면 인증을 통과시키지 않는다")
	void failAuthenticationWhenRevocationLookupFails() {
		MockHttpServletRequest request = cookieRequest("valid-token");
		when(jwtTokenProvider.parseAccessToken("valid-token")).thenReturn(payload("MEMBER"));
		when(revokedTokenMapper.existsRevokedToken("token-id")).thenThrow(
			new DataAccessResourceFailureException("DB 연결 실패"));

		assertThrows(DataAccessResourceFailureException.class, () -> filter.doFilter(request, response, chain));

		assertNull(SecurityContextHolder.getContext().getAuthentication());
		assertNull(chain.getRequest());
	}

	@ParameterizedTest
	@ValueSource(strings = {"device-a-token", "device-b-token"})
	@DisplayName("어느 기기에서 발급한 JWT든 인증 버전이 다르면 인증하지 않는다")
	void rejectTokenWithOutdatedAuthVersion(String token) throws Exception {
		MockHttpServletRequest request = cookieRequest(token);
		JwtPayload payload = new JwtPayload(10L, "MEMBER", token + "-id", Instant.now().plusSeconds(1800), 0L);

		when(jwtTokenProvider.parseAccessToken(token)).thenReturn(payload);
		when(memberMapper.existsActiveMemberByIdAndAuthVersion(10L, 0L)).thenReturn(false);

		filter.doFilter(request, response, chain);

		assertNull(SecurityContextHolder.getContext().getAuthentication());
		assertSame(request, chain.getRequest());
		verify(memberMapper).existsActiveMemberByIdAndAuthVersion(10L, 0L);
	}

	@Test
	@DisplayName("인증 버전 조회에 실패하면 요청을 진행시키지 않는다")
	void stopRequestWhenAuthVersionLookupFails() {
		MockHttpServletRequest request = cookieRequest("valid-token");
		when(jwtTokenProvider.parseAccessToken("valid-token")).thenReturn(payload("MEMBER"));
		when(memberMapper.existsActiveMemberByIdAndAuthVersion(10L, 0L)).thenThrow(
			new DataAccessResourceFailureException("DB 연결 실패"));

		assertThrows(DataAccessResourceFailureException.class, () -> filter.doFilter(request, response, chain));

		assertNull(SecurityContextHolder.getContext().getAuthentication());
		assertNull(chain.getRequest());
	}

	private MockHttpServletRequest cookieRequest(String token) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/boards");
		request.setCookies(new Cookie(AuthCookieFactory.ACCESS_TOKEN_COOKIE_NAME, token));
		return request;
	}

	private JwtPayload payload(String role) {
		return new JwtPayload(10L, role, "token-id", Instant.now().plusSeconds(1800), 0L);
	}
}