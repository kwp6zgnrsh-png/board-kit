package io.github.kwp6zgnrsh.boardkitapi.member.dto.response;

public record MemberCreateResponse(
	Long id,
	String loginId,
	String displayName
) {
}