package io.github.kwp6zgnrsh.boardkitapi.comment.service;

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
import io.github.kwp6zgnrsh.boardkitapi.comment.mapper.CommentLikeMapper;
import io.github.kwp6zgnrsh.boardkitapi.comment.mapper.CommentMapper;
import io.github.kwp6zgnrsh.boardkitapi.common.security.ContentBlindAccess;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberSanctionedException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostCommentTarget;
import io.github.kwp6zgnrsh.boardkitapi.post.policy.PostSecretAccess;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@Service
@Validated
@RequiredArgsConstructor
public class CommentLikeService {

	private final BoardMapper boardMapper;
	private final PostMapper postMapper;
	private final CommentMapper commentMapper;
	private final CommentLikeMapper commentLikeMapper;
	private final MemberMapper memberMapper;
	private final PostSecretAccess postSecretAccess;

	@Transactional
	public void likeComment(@NotBlank String boardSlug, @NotNull @Positive Long postId,
		@NotNull @Positive Long commentId, @NotNull @Positive Long memberId, boolean adminAccess,
		String secretPassword) {
		validateActiveMember(memberId);

		if (memberMapper.existsActiveSanctionByMemberId(memberId)) {
			throw new MemberSanctionedException();
		}

		validateLikeAccess(boardSlug, postId, commentId, memberId, adminAccess, secretPassword);

		commentLikeMapper.insertCommentLike(commentId, memberId);
	}

	@Transactional
	public void unlikeComment(@NotBlank String boardSlug, @NotNull @Positive Long postId,
		@NotNull @Positive Long commentId, @NotNull @Positive Long memberId, boolean adminAccess,
		String secretPassword) {
		validateActiveMember(memberId);
		validateLikeAccess(boardSlug, postId, commentId, memberId, adminAccess, secretPassword);

		commentLikeMapper.deleteCommentLike(commentId, memberId);
	}

	private void validateActiveMember(Long memberId) {
		if (!memberMapper.existsActiveMemberById(memberId)) {
			throw new MemberUnavailableException();
		}
	}

	private void validateLikeAccess(String boardSlug, Long postId, Long commentId, Long memberId, boolean adminAccess,
		String secretPassword) {
		Board board = boardMapper.selectActiveBoardBySlug(boardSlug).orElseThrow(BoardNotFoundException::new);

		BoardPolicy policy = boardMapper.selectPolicyByBoardId(board.getId())
			.orElseThrow(() -> new IllegalStateException("게시판 정책을 찾을 수 없습니다. boardId=" + board.getId()));

		BoardReadAccess.validate(policy.getReadPermission(), true, adminAccess);

		if (!policy.isCommentLikesEnabled()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "댓글 좋아요를 사용할 수 없는 게시판입니다.");
		}

		PostCommentTarget post = postMapper
			.selectActiveCommentTargetForShare(board.getId(), postId)
			.orElseThrow(PostNotFoundException::new);

		ContentBlindAccess.validateWrite(post.blindedAt());

		postSecretAccess.validate(
			post.secret(), post.secretPasswordHash(), post.authorId(),
			memberId, adminAccess, secretPassword
		);

		if (commentMapper.selectActiveLikeTargetForShare(board.getId(), postId, commentId).isEmpty()) {
			throw new CommentNotFoundException();
		}
	}
}