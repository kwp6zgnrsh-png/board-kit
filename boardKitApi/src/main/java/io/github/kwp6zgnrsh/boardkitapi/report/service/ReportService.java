package io.github.kwp6zgnrsh.boardkitapi.report.service;

import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.board.exception.BoardNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.board.mapper.BoardMapper;
import io.github.kwp6zgnrsh.boardkitapi.board.model.Board;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardPolicy;
import io.github.kwp6zgnrsh.boardkitapi.board.policy.BoardReadAccess;
import io.github.kwp6zgnrsh.boardkitapi.comment.exception.CommentNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.common.security.ContentBlindAccess;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostCommentTarget;
import io.github.kwp6zgnrsh.boardkitapi.post.policy.PostSecretAccess;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.report.exception.DuplicateReportException;
import io.github.kwp6zgnrsh.boardkitapi.report.mapper.ReportMapper;
import io.github.kwp6zgnrsh.boardkitapi.report.model.Report;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@Service
@Validated
@RequiredArgsConstructor
public class ReportService {

	private final BoardMapper boardMapper;
	private final MemberMapper memberMapper;
	private final ReportMapper reportMapper;
	private final PostSecretAccess postSecretAccess;

	@Transactional
	public Long createPostReport(@NotBlank String boardSlug, @NotNull @Positive Long postId,
		@NotNull @Valid ReportCreateRequest request, @NotNull @Positive Long memberId, boolean adminAccess,
		String secretPassword) {
		PostCommentTarget post = requireReportablePost(boardSlug, postId, memberId, adminAccess, secretPassword);

		validateNotOwnContent(post.authorId(), memberId);

		Report report = Report.builder()
			.reporterId(memberId)
			.postId(postId)
			.reason(request.reason())
			.detail(request.detail())
			.build();

		return saveReport(report);
	}

	@Transactional
	public Long createCommentReport(@NotBlank String boardSlug, @NotNull @Positive Long postId,
		@NotNull @Positive Long commentId, @NotNull @Valid ReportCreateRequest request,
		@NotNull @Positive Long memberId, boolean adminAccess, String secretPassword) {
		requireReportablePost(boardSlug, postId, memberId, adminAccess, secretPassword);

		Long commentAuthorId = reportMapper.selectActiveCommentAuthorForShare(postId, commentId)
			.orElseThrow(CommentNotFoundException::new);

		validateNotOwnContent(commentAuthorId, memberId);

		Report report = Report.builder()
			.reporterId(memberId)
			.commentId(commentId)
			.reason(request.reason())
			.detail(request.detail())
			.build();

		return saveReport(report);
	}

	private PostCommentTarget requireReportablePost(String boardSlug, Long postId, Long memberId, boolean adminAccess,
		String secretPassword) {
		Board board = boardMapper.selectActiveBoardBySlugForShare(boardSlug).orElseThrow(BoardNotFoundException::new);

		if (!memberMapper.existsActiveMemberById(memberId)) {
			throw new MemberUnavailableException();
		}
		if (memberMapper.existsActiveSanctionByMemberId(memberId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "제재 중에는 신고할 수 없습니다.");
		}

		BoardPolicy policy = boardMapper.selectPolicyByBoardId(board.getId())
			.orElseThrow(() -> new IllegalStateException("게시판 정책을 찾을 수 없습니다. boardId=" + board.getId()));

		BoardReadAccess.validate(policy.getReadPermission(), true, adminAccess);

		PostCommentTarget post = reportMapper.selectActivePostForShare(board.getId(), postId)
			.orElseThrow(PostNotFoundException::new);

		ContentBlindAccess.validateWrite(post.blindedAt());

		postSecretAccess.validate(post.secret(), post.secretPasswordHash(), post.authorId(), memberId, adminAccess,
			secretPassword);

		return post;
	}

	private void validateNotOwnContent(Long authorId, Long memberId) {
		if (Objects.equals(authorId, memberId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "자신이 작성한 내용은 신고할 수 없습니다.");
		}
	}

	private Long saveReport(Report report) {
		int insertedCount;

		try {
			insertedCount = reportMapper.insertReport(report);
		} catch (DuplicateKeyException exception) {
			throw new DuplicateReportException();
		}

		if (insertedCount != 1 || report.getId() == null) {
			throw new IllegalStateException("신고 저장에 실패했습니다.");
		}

		return report.getId();
	}
}