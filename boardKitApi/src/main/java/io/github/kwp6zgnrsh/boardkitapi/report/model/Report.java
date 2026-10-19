package io.github.kwp6zgnrsh.boardkitapi.report.model;

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
public class Report {

	private Long id;
	private Long reporterId;
	private Long postId;
	private Long commentId;
	private ReportReason reason;
	private String detail;
	private ReportStatus status;
	private Long reviewedBy;
	private String reviewNote;
	private LocalDateTime createdAt;
	private LocalDateTime reviewedAt;
}