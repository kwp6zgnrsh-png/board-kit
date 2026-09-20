package io.github.kwp6zgnrsh.boardkitapi.auth.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

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

		return new JwtPayload(
			Long.valueOf(claims.getSubject()),
			claims.get("role", String.class)
		);
	}
}