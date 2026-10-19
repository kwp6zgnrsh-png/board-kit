package io.github.kwp6zgnrsh.boardkitapi.member.dto.response;

import java.util.List;

public record AdminMemberPageResponse(
	List<AdminMemberSummaryResponse> members,
	int page,
	int size,
	long totalElements,
	int totalPages
) {}