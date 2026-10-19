package io.github.kwp6zgnrsh.boardkitapi.auth.cookie;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseCookie;

class AuthCookieFactoryTest {

	@ParameterizedTest(name = "secure={0}")
	@ValueSource(booleans = {true, false})
	@DisplayName("환경별 Secure 설정과 JWT 수명 및 공통 보안 속성을 적용해 인증 쿠키를 발급한다")
	void createCookieWithJwtLifetimeAndSecuritySettings(boolean secure) {
		AuthCookieFactory factory = new AuthCookieFactory(secure);
		Instant expiresAt = Instant.now().plusSeconds(1800);

		Instant before = Instant.now();
		ResponseCookie cookie = factory.createAccessTokenCookie("test-token", expiresAt);
		Instant after = Instant.now();

		assertEquals(AuthCookieFactory.ACCESS_TOKEN_COOKIE_NAME, cookie.getName());
		assertEquals("test-token", cookie.getValue());
		assertTrue(cookie.isHttpOnly());
		assertEquals(secure, cookie.isSecure());
		assertEquals("Lax", cookie.getSameSite());
		assertEquals("/api", cookie.getPath());
		assertNull(cookie.getDomain());

		long actualAge = cookie.getMaxAge().getSeconds();
		long minimumAge = Duration.between(after, expiresAt).getSeconds();
		long maximumAge = Duration.between(before, expiresAt).getSeconds();

		assertTrue(actualAge > 0);
		assertTrue(actualAge >= minimumAge && actualAge <= maximumAge);
	}

	@Test
	@DisplayName("로그아웃 쿠키는 기존 쿠키와 같은 범위에서 즉시 만료된다")
	void expireCookieWithSameScope() {
		AuthCookieFactory factory = new AuthCookieFactory(true);
		ResponseCookie issued = factory.createAccessTokenCookie("test-token", Instant.now().plusSeconds(1800));

		ResponseCookie expired = factory.expireAccessTokenCookie();

		assertEquals(issued.getName(), expired.getName());
		assertEquals(issued.getPath(), expired.getPath());
		assertEquals(issued.getDomain(), expired.getDomain());
		assertEquals(issued.getSameSite(), expired.getSameSite());
		assertEquals(issued.isHttpOnly(), expired.isHttpOnly());
		assertEquals(issued.isSecure(), expired.isSecure());
		assertEquals("", expired.getValue());
		assertEquals(Duration.ZERO, expired.getMaxAge());
	}

	@Test
	@DisplayName("만료된 JWT로 인증 쿠키를 발급할 수 없다")
	void rejectExpiredTokenCookie() {
		AuthCookieFactory factory = new AuthCookieFactory(true);

		assertThrows(IllegalArgumentException.class,
			() -> factory.createAccessTokenCookie("test-token", Instant.now().minusSeconds(1)));
	}
}