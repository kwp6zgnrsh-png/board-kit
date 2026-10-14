package io.github.kwp6zgnrsh.boardkitapi.auth.dto.response;

public record LoginResponse(
	Long memberId,
	String displayName,
	String role
) {
}