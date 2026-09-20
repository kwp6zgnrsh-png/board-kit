package io.github.kwp6zgnrsh.boardkitapi.auth.token;

public record JwtPayload(
	Long memberId,
	String role
) {
}