package io.github.kwp6zgnrsh.boardkitapi.member.model;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberSanction {

	private Long id;
	private Long memberId;
	private Long adminId;
	private String reason;
	private LocalDateTime startsAt;
	private LocalDateTime endsAt;
	private LocalDateTime revokedAt;
	private Long revokedBy;
	private String revokeReason;
	private LocalDateTime createdAt;
}