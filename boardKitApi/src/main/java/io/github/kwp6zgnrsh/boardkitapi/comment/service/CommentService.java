package io.github.kwp6zgnrsh.boardkitapi.comment.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.board.exception.BoardNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.board.mapper.BoardMapper;
import io.github.kwp6zgnrsh.boardkitapi.board.model.Board;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardPolicy;
import io.github.kwp6zgnrsh.boardkitapi.board.policy.BoardReadAccess;
import io.github.kwp6zgnrsh.boardkitapi.comment.converter.CommentConverter;
import io.github.kwp6zgnrsh.boardkitapi.comment.dto.request.CommentCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.comment.dto.request.CommentUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.comment.dto.response.CommentCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.comment.dto.response.CommentItemResponse;
import io.github.kwp6zgnrsh.boardkitapi.comment.dto.response.CommentPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.comment.exception.CommentConflictException;
import io.github.kwp6zgnrsh.boardkitapi.comment.exception.CommentDeleteDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.comment.exception.CommentEditDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.comment.exception.CommentNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.comment.mapper.CommentMapper;
import io.github.kwp6zgnrsh.boardkitapi.comment.model.Comment;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberSanctionedException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.SecretPostAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostCommentTarget;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

@Service
@Validated
@RequiredArgsConstructor
public class CommentService {

	private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

	private final BoardMapper boardMapper;
	private final PostMapper postMapper;
	private final CommentMapper commentMapper;
	private final MemberMapper memberMapper;
	private final CommentConverter commentConverter;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public CommentCreateResponse createComment(@NotBlank String boardSlug, @NotNull @Positive Long postId,
		@NotNull @Valid CommentCreateRequest request, @NotNull @Positive Long memberId, boolean adminAccess,
		String secretPassword) {

		validateMember(memberId);

		Board board = requireActiveBoard(boardSlug);
		BoardPolicy policy = requireBoardPolicy(board.getId());

		BoardReadAccess.validate(policy.getReadPermission(), true, adminAccess);

		PostCommentTarget post = postMapper.selectActiveCommentTargetForUpdate(board.getId(), postId)
			.orElseThrow(PostNotFoundException::new);

		validateSecretAccess(post, memberId, adminAccess, secretPassword);

		validateCommentWritePolicy(policy, adminAccess);

		validateCommentCreation(post, policy, postId, request.parentId());

		Comment comment = commentConverter.toComment(request, postId, memberId);

		int insertedCount = commentMapper.insertComment(comment);

		if (insertedCount != 1 || comment.getId() == null) {
			throw new IllegalStateException("댓글 저장에 실패했습니다.");
		}

		return commentConverter.toCreateResponse(comment);
	}

	@Transactional(readOnly = true)
	public CommentPageResponse getComments(@NotBlank String boardSlug, @NotNull @Positive Long postId, @Min(1) int page,
		@Min(1) @Max(100) int size, Long memberId, boolean memberAccess, boolean adminAccess, String secretPassword) {

		Board board = requireActiveBoard(boardSlug);
		BoardPolicy policy = requireBoardPolicy(board.getId());

		BoardReadAccess.validate(policy.getReadPermission(), memberAccess, adminAccess);

		validatePostAccess(board.getId(), postId, memberId, adminAccess, secretPassword);

		long totalElements = commentMapper.countActiveComments(postId);

		long offset = (long)(page - 1) * size;

		List<CommentItemResponse> comments = totalElements == 0 || offset >= totalElements ? List.of() :
			commentMapper.selectActiveComments(postId, size, offset);

		long calculatedTotalPages = totalElements / size + (totalElements % size == 0 ? 0 : 1);

		return new CommentPageResponse(comments, page, size, totalElements, Math.toIntExact(calculatedTotalPages));
	}

	@Transactional
	public void updateComment(@NotBlank String boardSlug, @NotNull @Positive Long postId,
		@NotNull @Positive Long commentId, @NotNull @Valid CommentUpdateRequest request,
		@NotNull @Positive Long memberId, boolean adminAccess, String secretPassword) {

		validateMember(memberId);

		Board board = requireActiveBoard(boardSlug);
		BoardPolicy policy = requireBoardPolicy(board.getId());

		BoardReadAccess.validate(policy.getReadPermission(), true, adminAccess);

		validateCommentWritePolicy(policy, adminAccess);

		PostCommentTarget post = validatePostAccess(board.getId(), postId, memberId, adminAccess, secretPassword);

		if (post.commentsLocked()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "댓글 작성이 잠긴 게시글입니다.");
		}

		Comment currentComment = requireActiveComment(board.getId(), postId, commentId);

		if (!Objects.equals(currentComment.getAuthorId(), memberId)) {
			throw new CommentEditDeniedException();
		}

		if (!Objects.equals(currentComment.getVersion(), request.version())) {
			throw new CommentConflictException();
		}

		int updatedCount = commentMapper.updateComment(board.getId(), postId, commentId, memberId, request.content(),
			request.version());

		if (updatedCount != 1) {
			throw new CommentConflictException();
		}
	}

	@Transactional
	public void deleteComment(@NotBlank String boardSlug, @NotNull @Positive Long postId,
		@NotNull @Positive Long commentId, @NotNull @PositiveOrZero Integer version, @NotNull @Positive Long memberId,
		boolean adminAccess) {

		validateActiveMember(memberId);

		Board board = requireActiveBoard(boardSlug);

		Comment currentComment = requireActiveComment(board.getId(), postId, commentId);

		boolean author = Objects.equals(currentComment.getAuthorId(), memberId);

		if (!author && !adminAccess) {
			throw new CommentDeleteDeniedException();
		}

		if (!Objects.equals(currentComment.getVersion(), version)) {
			throw new CommentConflictException();
		}

		int deletedCount = commentMapper.softDeleteComment(board.getId(), postId, commentId, version);

		if (deletedCount != 1) {
			throw new CommentConflictException();
		}
	}

	private void validateSecretAccess(PostCommentTarget post, Long memberId, boolean adminAccess, String password) {
		if (!post.secret() || post.secretPasswordHash() == null || adminAccess || Objects.equals(post.authorId(),
			memberId)) {
			return;
		}

		if (password == null || password.isBlank()
			|| password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES || !passwordEncoder.matches(
			password, post.secretPasswordHash())) {
			throw new SecretPostAccessDeniedException();
		}
	}

	private void validateMember(Long memberId) {
		validateActiveMember(memberId);

		if (memberMapper.existsActiveSanctionByMemberId(memberId)) {
			throw new MemberSanctionedException();
		}
	}

	private void validateActiveMember(Long memberId) {
		if (!memberMapper.existsActiveMemberById(memberId)) {
			throw new MemberUnavailableException();
		}
	}

	private PostCommentTarget validatePostAccess(Long boardId, Long postId, Long memberId, boolean adminAccess,
		String secretPassword) {

		PostCommentTarget post = postMapper.selectActiveCommentTarget(boardId, postId)
			.orElseThrow(PostNotFoundException::new);

		validateSecretAccess(post, memberId, adminAccess, secretPassword);

		return post;
	}

	private void validateCommentWritePolicy(BoardPolicy policy, boolean adminAccess) {
		if (!policy.isCommentsEnabled()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "댓글을 사용할 수 없는 게시판입니다.");
		}

		switch (policy.getCommentPermission()) {
			case "MEMBER" -> {
			}
			case "ADMIN" -> {
				if (!adminAccess) {
					throw new ResponseStatusException(HttpStatus.FORBIDDEN, "댓글 작성 권한이 없습니다.");
				}
			}
			default -> throw new IllegalStateException("지원하지 않는 댓글 작성 권한입니다: " + policy.getCommentPermission());
		}
	}

	private void validateCommentCreation(PostCommentTarget post, BoardPolicy policy, Long postId, Long parentId) {

		if (post.commentsLocked()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "댓글 작성이 잠긴 게시글입니다.");
		}

		if (parentId == null) {
			return;
		}

		if (!policy.isCommentRepliesEnabled()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "대댓글을 사용할 수 없는 게시판입니다.");
		}

		if (commentMapper.selectActiveRootIdForUpdate(postId, parentId).isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용할 수 없는 부모 댓글입니다.");
		}
	}

	private Comment requireActiveComment(Long boardId, Long postId, Long commentId) {
		return commentMapper.selectActiveComment(boardId, postId, commentId).orElseThrow(CommentNotFoundException::new);
	}

	private Board requireActiveBoard(String boardSlug) {
		return boardMapper.selectActiveBoardBySlug(boardSlug).orElseThrow(BoardNotFoundException::new);
	}

	private BoardPolicy requireBoardPolicy(Long boardId) {
		return boardMapper.selectPolicyByBoardId(boardId)
			.orElseThrow(() -> new IllegalStateException("게시판 정책을 찾을 수 없습니다. boardId=" + boardId));
	}
}