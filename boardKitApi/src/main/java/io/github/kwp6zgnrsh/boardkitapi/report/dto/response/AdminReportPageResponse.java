package io.github.kwp6zgnrsh.boardkitapi.report.dto.response;

import java.util.List;

public record AdminReportPageResponse(
	List<AdminReportSummaryResponse> reports,
	int page,
	int size,
	long totalElements,
	int totalPages
) {
}