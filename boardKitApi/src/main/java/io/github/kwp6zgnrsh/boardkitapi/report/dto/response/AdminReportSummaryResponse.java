package io.github.kwp6zgnrsh.boardkitapi.report.dto.response;

import java.time.LocalDateTime;

import io.github.kwp6zgnrsh.boardkitapi.report.model.ReportReason;
import io.github.kwp6zgnrsh.boardkitapi.report.model.ReportStatus;

public record AdminReportSummaryResponse(
	Long id,
	Long postId,
	Long commentId,
	String boardSlug,
	String boardName,
	String boardStatus,
	String postTitle,
	String reporterDisplayName,
	ReportReason reason,
	ReportStatus status,
	boolean postDeleted,
	boolean commentDeleted,
	LocalDateTime createdAt
) {
}