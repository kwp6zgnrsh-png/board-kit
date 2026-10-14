package io.github.kwp6zgnrsh.boardkitapi.auth.dto.response;

public record CurrentMemberResponse(
	Long memberId,
	String displayName,
	String role
) {
}