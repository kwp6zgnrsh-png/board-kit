package io.github.kwp6zgnrsh.boardkitapi.member.dto.response;

import java.time.LocalDateTime;

public record AdminMemberSanctionSummaryResponse(
	Long sanctionId,
	Long memberId,
	String loginId,
	String displayName,
	boolean memberDeleted,
	String reason,
	String status,
	LocalDateTime startsAt,
	LocalDateTime endsAt,
	LocalDateTime revokedAt,
	LocalDateTime createdAt
) {}