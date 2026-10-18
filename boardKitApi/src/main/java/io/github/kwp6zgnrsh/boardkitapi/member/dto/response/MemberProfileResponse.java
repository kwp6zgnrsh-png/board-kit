package io.github.kwp6zgnrsh.boardkitapi.member.dto.response;

import java.time.LocalDateTime;

public record MemberProfileResponse(
	Long memberId,
	String loginId,
	String displayName,
	String role,
	LocalDateTime createdAt
) {}