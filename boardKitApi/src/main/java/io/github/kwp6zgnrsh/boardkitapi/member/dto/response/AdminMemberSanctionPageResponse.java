package io.github.kwp6zgnrsh.boardkitapi.member.dto.response;

import java.util.List;

public record AdminMemberSanctionPageResponse(
	List<AdminMemberSanctionSummaryResponse> sanctions,
	int page,
	int size,
	long totalElements,
	int totalPages
) {}