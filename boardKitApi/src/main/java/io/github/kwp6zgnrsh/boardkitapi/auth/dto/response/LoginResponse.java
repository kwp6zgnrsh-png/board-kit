package io.github.kwp6zgnrsh.boardkitapi.auth.dto.response;

public record LoginResponse(
	String accessToken,
	String tokenType,
	Long memberId,
	String displayName,
	String role
) {
}