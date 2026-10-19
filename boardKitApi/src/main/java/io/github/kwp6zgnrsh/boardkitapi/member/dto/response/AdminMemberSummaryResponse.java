package io.github.kwp6zgnrsh.boardkitapi.member.dto.response;

public record AdminMemberSummaryResponse(
	Long memberId,
	String loginId,
	String displayName,
	boolean sanctioned
) {}