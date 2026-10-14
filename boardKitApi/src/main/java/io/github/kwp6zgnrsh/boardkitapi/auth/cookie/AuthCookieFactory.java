package io.github.kwp6zgnrsh.boardkitapi.auth.cookie;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieFactory {

	public static final String ACCESS_TOKEN_COOKIE_NAME = "boardkit_access_token";

	private final boolean cookieSecure;

	public AuthCookieFactory(@Value("${boardkit.auth.cookie-secure:true}") boolean cookieSecure) {
		this.cookieSecure = cookieSecure;
	}

	public ResponseCookie createAccessTokenCookie(String token, Instant expiresAt) {
		Duration remaining = Duration.between(Instant.now(), expiresAt);

		if (remaining.getSeconds() <= 0) {
			throw new IllegalArgumentException("만료된 인증 토큰은 쿠키로 발급할 수 없습니다.");
		}

		return baseCookie(token).maxAge(remaining).build();
	}

	public ResponseCookie expireAccessTokenCookie() {
		return baseCookie("").maxAge(0).build();
	}

	private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
		return ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, value)
			.httpOnly(true)
			.secure(cookieSecure)
			.sameSite("Lax")
			.path("/api");
	}
}