package io.github.kwp6zgnrsh.boardkitapi.auth.token;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

class JwtTokenProviderTest {

	private static final String SECRET = Base64.getEncoder()
		.encodeToString("0123456789abcdef".repeat(4).getBytes(StandardCharsets.UTF_8));

	private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 3600L);

	@Test
	@DisplayName("JWT에서 회원 정보와 토큰 식별자 및 만료 시각을 읽는다")
	void parseTokenWithIdentityAndExpiration() {
		String token = provider.createAccessToken(10L, "MEMBER", 3L);

		JwtPayload payload = provider.parseAccessToken(token);

		assertEquals(10L, payload.memberId());
		assertEquals(3L, payload.authVersion());
		assertEquals("MEMBER", payload.role());
		assertNotNull(payload.tokenId());
		assertDoesNotThrow(() -> UUID.fromString(payload.tokenId()));
		assertNotNull(payload.expiresAt());
		assertTrue(payload.expiresAt().isAfter(Instant.now()));
	}

	@Test
	@DisplayName("같은 회원에게 발급한 토큰도 식별자가 서로 다르다")
	void issueDistinctTokensForSameMember() {
		String firstToken = provider.createAccessToken(10L, "MEMBER", 0L);
		String secondToken = provider.createAccessToken(10L, "MEMBER", 0L);

		JwtPayload first = provider.parseAccessToken(firstToken);
		JwtPayload second = provider.parseAccessToken(secondToken);

		assertNotEquals(first.tokenId(), second.tokenId());
		assertNotEquals(firstToken, secondToken);
	}

	@Test
	@DisplayName("서명이 유효해도 식별자가 없는 JWT는 거절한다")
	void rejectTokenWithoutIdentifier() {
		Instant now = Instant.now();
		String token = Jwts.builder()
			.issuer("boardkit")
			.subject("10")
			.claim("role", "MEMBER")
			.claim("type", "access")
			.claim("authVersion", 0L)
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plusSeconds(3600)))
			.signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
			.compact();

		assertThrows(JwtException.class, () -> provider.parseAccessToken(token));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(longs = {-1L})
	@DisplayName("인증 버전이 없거나 음수인 JWT는 거절한다")
	void rejectTokenWithInvalidAuthVersion(Long authVersion) {
		Instant now = Instant.now();
		String token = Jwts.builder()
			.issuer("boardkit")
			.id(UUID.randomUUID().toString())
			.subject("10")
			.claim("role", "MEMBER")
			.claim("type", "access")
			.claim("authVersion", authVersion)
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plusSeconds(3600)))
			.signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
			.compact();

		assertThrows(JwtException.class, () -> provider.parseAccessToken(token));
	}
}