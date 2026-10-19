package io.github.kwp6zgnrsh.boardkitapi.post.service;

import java.util.List;
import java.util.Optional;

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
import io.github.kwp6zgnrsh.boardkitapi.common.security.ContentBlindAccess;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.converter.PostConverter;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostReplyCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostReplyFilter;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostReplyUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.AdminPostReplyPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.AdminPostReplySummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostReplyResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostReplyConflictException;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostReplyMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostCommentTarget;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostReply;
import io.github.kwp6zgnrsh.boardkitapi.post.policy.PostSecretAccess;
import io.github.kwp6zgnrsh.boardkitapi.post.service.PostDocumentValidator.ValidatedDocument;
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
public class PostReplyService {

	private final BoardMapper boardMapper;
	private final PostMapper postMapper;
	private final PostReplyMapper postReplyMapper;
	private final MemberMapper memberMapper;
	private final PostConverter postConverter;
	private final PostDocumentValidator postDocumentValidator;
	private final PostSecretAccess postSecretAccess;

	@Transactional(readOnly = true)
	public Optional<PostReplyResponse> getPostReply(@NotBlank String boardSlug, @NotNull @Positive Long postId,
		Long memberId, boolean adminAccess, String secretPassword) {

		Board board = boardMapper.selectReadableBoardBySlug(boardSlug, adminAccess)
			.orElseThrow(BoardNotFoundException::new);
		BoardPolicy policy = requirePolicy(board.getId());
		BoardReadAccess.validate(policy.getReadPermission(), memberId != null, adminAccess);

		PostCommentTarget post = postMapper.selectActiveCommentTarget(board.getId(), postId)
			.orElseThrow(PostNotFoundException::new);

		ContentBlindAccess.validateRead(post.blindedAt(), post.authorId(), memberId, adminAccess);

		postSecretAccess.validate(post.secret(), post.secretPasswordHash(), post.authorId(), memberId, adminAccess,
			secretPassword);

		return postReplyMapper.selectPostReply(board.getId(), postId);
	}

	@Transactional(readOnly = true)
	public AdminPostReplyPageResponse getAdminPostReplyTargets(@NotNull PostReplyFilter filter, @Min(1) int page,
		@Min(1) @Max(100) int size, @NotNull @Positive Long adminId, boolean adminAccess) {
		if (!adminAccess) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 공식 답변 관리 목록을 조회할 수 있습니다.");
		}
		if (!memberMapper.existsActiveMemberById(adminId)) {
			throw new MemberUnavailableException();
		}

		long totalElements = postReplyMapper.countAdminPostReplyTargets(filter);
		long offset = ((long)page - 1) * size;

		List<AdminPostReplySummaryResponse> posts;
		if (offset >= totalElements) {
			posts = List.of();
		} else {
			posts = postReplyMapper.selectAdminPostReplyTargets(filter, size, offset);
		}

		long totalPages = totalElements / size + (totalElements % size == 0 ? 0 : 1);

		return new AdminPostReplyPageResponse(posts, page, size, totalElements, Math.toIntExact(totalPages));
	}

	@Transactional
	public PostReplyResponse createPostReply(@NotBlank String boardSlug, @NotNull @Positive Long postId,
		@NotNull @Valid PostReplyCreateRequest request, @NotNull @Positive Long adminId, boolean adminAccess) {

		Board board = requireManageablePost(boardSlug, postId, adminId, adminAccess);
		ValidatedDocument document = postDocumentValidator.validateReply(request.contentDocument());
		PostReply reply = postConverter.toPostReply(postId, adminId, document);

		int insertedCount;

		try {
			insertedCount = postReplyMapper.insertPostReply(reply);
		} catch (DuplicateKeyException exception) {
			throw new PostReplyConflictException();
		}

		if (insertedCount != 1 || reply.getId() == null) {
			throw new IllegalStateException("공식 답변 저장에 실패했습니다.");
		}

		return postReplyMapper.selectPostReply(board.getId(), postId)
			.orElseThrow(() -> new IllegalStateException("저장한 공식 답변을 찾을 수 없습니다."));
	}

	@Transactional
	public void updatePostReply(@NotBlank String boardSlug, @NotNull @Positive Long postId,
		@NotNull @Positive Long replyId, @NotNull @Valid PostReplyUpdateRequest request,
		@NotNull @Positive Long adminId, boolean adminAccess) {
		Board board = requireManageablePost(boardSlug, postId, adminId, adminAccess);
		ValidatedDocument document = postDocumentValidator.validateReply(request.contentDocument());

		int updatedCount = postReplyMapper.updatePostReply(board.getId(), postId, replyId, document.text(),
			document.document(), request.version());

		if (updatedCount != 1) {
			throw new PostReplyConflictException();
		}
	}

	@Transactional
	public void deletePostReply(@NotBlank String boardSlug, @NotNull @Positive Long postId,
		@NotNull @Positive Long replyId, @NotNull @PositiveOrZero Integer version, @NotNull @Positive Long adminId,
		boolean adminAccess) {
		Board board = requireManageablePost(boardSlug, postId, adminId, adminAccess);

		if (postReplyMapper.deletePostReply(board.getId(), postId, replyId, version) != 1) {
			throw new PostReplyConflictException();
		}
	}

	private Board requireManageablePost(String boardSlug, Long postId, Long adminId, boolean adminAccess) {
		if (!adminAccess) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 공식 답변을 관리할 수 있습니다.");
		}

		Board board = boardMapper.selectActiveBoardBySlugForShare(boardSlug).orElseThrow(BoardNotFoundException::new);

		if (!memberMapper.existsActiveMemberById(adminId)) {
			throw new MemberUnavailableException();
		}

		BoardPolicy policy = requirePolicy(board.getId());
		BoardReadAccess.validate(policy.getReadPermission(), true, adminAccess);

		if (!policy.isOfficialReplyEnabled()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "공식 답변을 사용할 수 없는 게시판입니다.");
		}

		PostCommentTarget post = postMapper
			.selectActiveCommentTargetForUpdate(board.getId(), postId)
			.orElseThrow(PostNotFoundException::new);

		ContentBlindAccess.validateWrite(post.blindedAt());

		return board;
	}

	private BoardPolicy requirePolicy(Long boardId) {
		return boardMapper.selectPolicyByBoardId(boardId)
			.orElseThrow(() -> new IllegalStateException("게시판 정책을 찾을 수 없습니다. boardId=" + boardId));
	}
}