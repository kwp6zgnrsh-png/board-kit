package io.github.kwp6zgnrsh.boardkitapi.report.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.comment.exception.CommentNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.comment.mapper.CommentMapper;
import io.github.kwp6zgnrsh.boardkitapi.comment.model.Comment;
import io.github.kwp6zgnrsh.boardkitapi.file.mapper.FileCleanupTaskMapper;
import io.github.kwp6zgnrsh.boardkitapi.file.mapper.PostFileMapper;
import io.github.kwp6zgnrsh.boardkitapi.file.model.PostFile;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.model.Post;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportTargetAction;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportTargetActionRequest;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminReportDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.exception.ReportNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.report.exception.ReportTargetConflictException;
import io.github.kwp6zgnrsh.boardkitapi.report.mapper.ReportMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@Service
@Validated
@RequiredArgsConstructor
public class AdminContentModerationService {

	private final MemberMapper memberMapper;
	private final ReportMapper reportMapper;
	private final PostMapper postMapper;
	private final CommentMapper commentMapper;
	private final PostFileMapper postFileMapper;
	private final FileCleanupTaskMapper fileCleanupTaskMapper;

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void moderateReportTarget(@NotNull @Positive Long reportId,
		@NotNull @Valid ReportTargetActionRequest request, @NotNull @Positive Long adminId, boolean adminAccess) {
		requireAdmin(adminId, adminAccess);

		AdminReportDetailResponse report = reportMapper.selectAdminReport(reportId)
			.orElseThrow(ReportNotFoundException::new);

		applyAction(report.postId(), report.commentId(), request);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void moderateTarget(@NotNull @Positive Long postId, @Positive Long commentId,
		@NotNull @Valid ReportTargetActionRequest request, @NotNull @Positive Long adminId, boolean adminAccess) {
		requireAdmin(adminId, adminAccess);
		applyAction(postId, commentId, request);
	}

	private void applyAction(Long postId, Long commentId, ReportTargetActionRequest request) {
		Post post = postMapper.selectPostForModerationForUpdate(postId).orElseThrow(PostNotFoundException::new);

		if (post.getDeletedAt() != null) {
			throw new ReportTargetConflictException();
		}

		Comment comment = null;
		if (commentId != null) {
			comment = commentMapper.selectCommentForModerationForUpdate(postId, commentId)
				.orElseThrow(CommentNotFoundException::new);

			if (comment.getDeletedAt() != null) {
				throw new ReportTargetConflictException();
			}
		}

		Integer currentVersion = comment == null ? post.getVersion() : comment.getVersion();
		LocalDateTime blindedAt = comment == null ? post.getBlindedAt() : comment.getBlindedAt();

		if (!Objects.equals(currentVersion, request.version())) {
			throw new ReportTargetConflictException();
		}

		if ((request.action() == ReportTargetAction.BLIND && blindedAt != null) || (
			request.action() == ReportTargetAction.UNBLIND && blindedAt == null)) {
			throw new ReportTargetConflictException();
		}

		int updatedCount = switch (request.action()) {
			case BLIND -> comment == null ? postMapper.blindPost(postId, request.version()) :
				commentMapper.blindComment(postId, commentId, request.version());

			case UNBLIND -> {
				long acceptedCount = comment == null ? reportMapper.countAcceptedPostReports(postId) :
					reportMapper.countAcceptedCommentReports(commentId);

				yield comment == null ? postMapper.unblindPost(postId, request.version(), acceptedCount) :
					commentMapper.unblindComment(postId, commentId, request.version(), acceptedCount);
			}

			case DELETE -> comment == null ? postMapper.softDeletePost(post.getBoardId(), postId, request.version()) :
				commentMapper.softDeleteComment(post.getBoardId(), postId, commentId, request.version());
		};

		if (updatedCount != 1) {
			throw new ReportTargetConflictException();
		}

		if (request.action() == ReportTargetAction.DELETE && comment == null) {
			List<PostFile> files = postFileMapper.selectActivePostFilesForUpdate(postId);
			postFileMapper.softDeleteAllPostFiles(postId);

			List<String> targetKeys = new ArrayList<>();
			for (PostFile file : files) {
				targetKeys.add(file.getStorageKey());
				if (file.getThumbnailKey() != null) {
					targetKeys.add(file.getThumbnailKey());
				}
			}

			if (!targetKeys.isEmpty()) {
				fileCleanupTaskMapper.enqueueDeletes(targetKeys);
			}
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