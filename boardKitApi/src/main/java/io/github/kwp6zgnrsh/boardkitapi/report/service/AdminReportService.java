package io.github.kwp6zgnrsh.boardkitapi.report.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.comment.exception.CommentNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.comment.mapper.CommentMapper;
import io.github.kwp6zgnrsh.boardkitapi.comment.model.Comment;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.model.Post;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportReviewRequest;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportStatusFilter;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportTargetActionRequest;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminReportDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminReportPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminReportSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.exception.ReportNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.report.exception.ReportReviewConflictException;
import io.github.kwp6zgnrsh.boardkitapi.report.mapper.ReportMapper;
import io.github.kwp6zgnrsh.boardkitapi.report.model.ReportStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@Service
@Validated
@RequiredArgsConstructor
public class AdminReportService {

	private static final int AUTO_BLIND_THRESHOLD = 3;

	private final MemberMapper memberMapper;
	private final ReportMapper reportMapper;
	private final PostMapper postMapper;
	private final CommentMapper commentMapper;
	private final AdminContentModerationService adminContentModerationService;

	@Transactional(readOnly = true)
	public AdminReportPageResponse getReports(@NotNull ReportStatusFilter filter, @Min(1) int page,
		@Min(1) @Max(100) int size, @NotNull @Positive Long adminId, boolean adminAccess) {
		requireAdmin(adminId, adminAccess);

		long totalElements = reportMapper.countAdminReports(filter);
		long offset = ((long)page - 1) * size;

		List<AdminReportSummaryResponse> reports;
		if (offset >= totalElements) {
			reports = List.of();
		} else {
			reports = reportMapper.selectAdminReports(filter, size, offset);
		}

		long totalPages = totalElements / size + (totalElements % size == 0 ? 0 : 1);

		return new AdminReportPageResponse(reports, page, size, totalElements, Math.toIntExact(totalPages));
	}

	@Transactional(readOnly = true)
	public AdminReportDetailResponse getReport(@NotNull @Positive Long reportId, @NotNull @Positive Long adminId,
		boolean adminAccess) {
		requireAdmin(adminId, adminAccess);

		return reportMapper.selectAdminReport(reportId).orElseThrow(ReportNotFoundException::new);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void reviewReport(@NotNull @Positive Long reportId, @NotNull @Valid ReportReviewRequest request,
		@NotNull @Positive Long adminId, boolean adminAccess) {
		requireAdmin(adminId, adminAccess);

		AdminReportDetailResponse report = reportMapper.selectAdminReport(reportId)
			.orElseThrow(ReportNotFoundException::new);

		Post post = postMapper.selectPostForModerationForUpdate(report.postId())
			.orElseThrow(PostNotFoundException::new);

		Comment comment = null;
		if (report.commentId() != null) {
			comment = commentMapper.selectCommentForModerationForUpdate(post.getId(), report.commentId())
				.orElseThrow(CommentNotFoundException::new);
		}

		int updatedCount = reportMapper.reviewReport(reportId, request.status(), request.reviewNote(), adminId);

		if (updatedCount != 1) {
			throw new ReportReviewConflictException();
		}

		if (request.status() != ReportStatus.ACCEPTED || post.getDeletedAt() != null) {
			return;
		}

		if (comment == null) {
			blindPostIfThresholdReached(post);
		} else {
			blindCommentIfThresholdReached(comment);
		}
	}

	public void moderateReportTarget(@NotNull @Positive Long reportId,
		@NotNull @Valid ReportTargetActionRequest request, @NotNull @Positive Long adminId, boolean adminAccess) {
		adminContentModerationService.moderateReportTarget(reportId, request, adminId, adminAccess);
	}

	private void blindPostIfThresholdReached(Post post) {
		if (post.getBlindedAt() != null) {
			return;
		}

		long acceptedCount = reportMapper.countAcceptedPostReports(post.getId());

		if (acceptedCount - post.getBlindReportBaseCount() < AUTO_BLIND_THRESHOLD) {
			return;
		}

		if (postMapper.blindPost(post.getId(), post.getVersion()) != 1) {
			throw new IllegalStateException("게시글 블라인드 처리에 실패했습니다.");
		}
	}

	private void blindCommentIfThresholdReached(Comment comment) {
		if (comment.getDeletedAt() != null || comment.getBlindedAt() != null) {
			return;
		}

		long acceptedCount = reportMapper.countAcceptedCommentReports(comment.getId());

		if (acceptedCount - comment.getBlindReportBaseCount() < AUTO_BLIND_THRESHOLD) {
			return;
		}

		if (commentMapper.blindComment(comment.getPostId(), comment.getId(), comment.getVersion()) != 1) {
			throw new IllegalStateException("댓글 블라인드 처리에 실패했습니다.");
		}
	}

	private void requireAdmin(Long adminId, boolean adminAccess) {
		if (!adminAccess) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
		}
		if (!memberMapper.existsActiveMemberById(adminId)) {
			throw new MemberUnavailableException();
		}
	}
}