package io.github.kwp6zgnrsh.boardkitapi.report.dto.response;

import java.time.LocalDateTime;

import io.github.kwp6zgnrsh.boardkitapi.report.model.ReportReason;
import io.github.kwp6zgnrsh.boardkitapi.report.model.ReportStatus;
import tools.jackson.databind.JsonNode;

public record AdminReportDetailResponse(
	Long id,
	Long reporterId,
	String reporterDisplayName,
	Long postId,
	Long commentId,
	String boardSlug,
	String boardName,
	String boardStatus,
	String postTitle,
	Long authorId,
	String authorDisplayName,
	ReportReason reason,
	String detail,
	ReportStatus status,
	String content,
	JsonNode contentDocument,
	LocalDateTime contentCreatedAt,
	LocalDateTime contentUpdatedAt,
	boolean postDeleted,
	boolean commentDeleted,
	Long reviewedBy,
	String reviewerDisplayName,
	String reviewNote,
	LocalDateTime createdAt,
	LocalDateTime reviewedAt,
	boolean postBlinded,
	boolean commentBlinded,
	Integer targetVersion
) {
}