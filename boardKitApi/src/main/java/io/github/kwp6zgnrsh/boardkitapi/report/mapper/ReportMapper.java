package io.github.kwp6zgnrsh.boardkitapi.report.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import io.github.kwp6zgnrsh.boardkitapi.post.model.PostCommentTarget;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportStatusFilter;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminReportDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminReportSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.model.Report;
import io.github.kwp6zgnrsh.boardkitapi.report.model.ReportStatus;

@Mapper
public interface ReportMapper {

	int insertReport(Report report);

	Optional<PostCommentTarget> selectActivePostForShare(
		@Param("boardId") Long boardId,
		@Param("postId") Long postId
	);

	Optional<Long> selectActiveCommentAuthorForShare(
		@Param("postId") Long postId,
		@Param("commentId") Long commentId
	);

	long countAdminReports(@Param("filter") ReportStatusFilter filter);

	List<AdminReportSummaryResponse> selectAdminReports(
		@Param("filter") ReportStatusFilter filter,
		@Param("limit") int limit,
		@Param("offset") long offset
	);

	Optional<AdminReportDetailResponse> selectAdminReport(
		@Param("reportId") Long reportId
	);

	boolean existsReport(@Param("reportId") Long reportId);

	int reviewReport(
		@Param("reportId") Long reportId,
		@Param("status") ReportStatus status,
		@Param("reviewNote") String reviewNote,
		@Param("adminId") Long adminId
	);

	long countAcceptedPostReports(@Param("postId") Long postId);

	long countAcceptedCommentReports(@Param("commentId") Long commentId);
}