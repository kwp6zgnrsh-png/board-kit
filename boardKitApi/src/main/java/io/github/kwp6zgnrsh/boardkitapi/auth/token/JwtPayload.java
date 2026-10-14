package io.github.kwp6zgnrsh.boardkitapi.auth.token;

import java.time.Instant;

public record JwtPayload(
	Long memberId,
	String role,
	String tokenId,
	Instant expiresAt
) {
}