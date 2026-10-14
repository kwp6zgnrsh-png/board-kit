package io.github.kwp6zgnrsh.boardkitapi.auth.token;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

	private static final String ISSUER = "boardkit";

	private final SecretKey signingKey;
	private final long accessTokenExpirationSeconds;

	public JwtTokenProvider(@Value("${jwt.secret}") String encodedSecret,
		@Value("${jwt.access-token-expiration-seconds}") long accessTokenExpirationSeconds) {
		this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(encodedSecret));
		this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
	}

	public String createAccessToken(Long memberId, String role) {
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plusSeconds(accessTokenExpirationSeconds);

		return Jwts.builder()
			.issuer(ISSUER)
			.id(UUID.randomUUID().toString())
			.subject(memberId.toString())
			.claim("role", role)
			.claim("type", "access")
			.issuedAt(Date.from(issuedAt))
			.expiration(Date.from(expiresAt))
			.signWith(signingKey)
			.compact();
	}

	public JwtPayload parseAccessToken(String token) {
		Claims claims = Jwts.parser()
			.verifyWith(signingKey)
			.requireIssuer(ISSUER)
			.require("type", "access")
			.build()
			.parseSignedClaims(token)
			.getPayload();

		String tokenId = claims.getId();
		Date expiration = claims.getExpiration();

		if (tokenId == null || tokenId.isBlank() || expiration == null) {
			throw new JwtException("필수 인증 정보가 없는 토큰입니다.");
		}

		return new JwtPayload(Long.valueOf(claims.getSubject()), claims.get("role", String.class), tokenId,
			expiration.toInstant());
	}
}