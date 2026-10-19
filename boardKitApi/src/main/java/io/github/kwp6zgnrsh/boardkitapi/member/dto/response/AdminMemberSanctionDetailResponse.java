package io.github.kwp6zgnrsh.boardkitapi.member.dto.response;

import java.time.LocalDateTime;

public record AdminMemberSanctionDetailResponse(
	Long sanctionId,
	Long memberId,
	String loginId,
	String displayName,
	boolean memberDeleted,
	Long adminId,
	String adminDisplayName,
	String reason,
	String status,
	LocalDateTime startsAt,
	LocalDateTime endsAt,
	LocalDateTime revokedAt,
	Long revokedBy,
	String revokerDisplayName,
	String revokeReason,
	LocalDateTime createdAt
) {}