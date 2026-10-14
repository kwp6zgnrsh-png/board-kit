package io.github.kwp6zgnrsh.boardkitapi.auth.model;

import java.time.Instant;

public record LoginResult(
	String accessToken,
	Instant expiresAt,
	Long memberId,
	String displayName,
	String role
) {
}