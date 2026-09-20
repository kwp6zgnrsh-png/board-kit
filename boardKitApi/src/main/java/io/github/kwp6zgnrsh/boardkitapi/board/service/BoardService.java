package io.github.kwp6zgnrsh.boardkitapi.board.service;

import java.util.List;

import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardCategoryCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardPolicyCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.mapper.BoardMapper;
import io.github.kwp6zgnrsh.boardkitapi.board.model.Board;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardCategory;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class BoardService {

	private final BoardMapper boardMapper;

	@Transactional
	public BoardCreateResponse createBoard(@NotNull @Valid BoardCreateRequest request,
										   @NotNull @Positive Long adminId) {

		BoardPolicyCreateRequest policyRequest = request.policy();
		validatePolicy(policyRequest);

		Board board = Board.builder()
			.name(request.name())
			.slug(request.slug())
			.description(request.description())
			.displayType(request.displayType())
			.status("ACTIVE")
			.sortOrder(request.sortOrder())
			.createdBy(adminId)
			.build();

		int boardCount = boardMapper.insertBoard(board);

		if (boardCount != 1 || board.getId() == null) {
			throw new IllegalStateException("게시판 저장에 실패했습니다.");
		}

		BoardPolicy policy = BoardPolicy.builder()
			.boardId(board.getId())
			.readPermission(policyRequest.readPermission())
			.writePermission(policyRequest.writePermission())
			.commentPermission(policyRequest.commentPermission())
			.secretMode(policyRequest.secretMode())
			.categoriesEnabled(policyRequest.categoriesEnabled())
			.categoryRequired(policyRequest.categoryRequired())
			.attachmentsEnabled(policyRequest.attachmentsEnabled())
			.uploadProfile(policyRequest.uploadProfile())
			.maxFileCount(policyRequest.maxFileCount())
			.maxFileSizeBytes(policyRequest.maxFileSizeBytes())
			.imageRequired(policyRequest.imageRequired())
			.thumbnailEnabled(policyRequest.thumbnailEnabled())
			.representativeImageMode(policyRequest.representativeImageMode())
			.commentsEnabled(policyRequest.commentsEnabled())
			.commentRepliesEnabled(policyRequest.commentRepliesEnabled())
			.postLikesEnabled(policyRequest.postLikesEnabled())
			.commentLikesEnabled(policyRequest.commentLikesEnabled())
			.officialReplyEnabled(policyRequest.officialReplyEnabled())
			.version(0)
			.build();

		if (boardMapper.insertPolicy(policy) != 1) {
			throw new IllegalStateException("게시판 정책 저장에 실패했습니다.");
		}

		for (BoardCategoryCreateRequest categoryRequest : request.categories()) {
			BoardCategory category = BoardCategory.builder()
				.boardId(board.getId())
				.name(categoryRequest.name())
				.sortOrder(categoryRequest.sortOrder())
				.active(true)
				.build();

			if (boardMapper.insertCategory(category) != 1) {
				throw new IllegalStateException("카테고리 저장에 실패했습니다.");
			}
		}

		return new BoardCreateResponse(
			board.getId(),
			board.getName(),
			board.getSlug()
		);
	}

	@Transactional(readOnly = true)
	public List<BoardSummaryResponse> getAccessibleBoards(boolean memberAccess, boolean adminAccess) {
		return boardMapper.selectAccessibleBoards(
			memberAccess,
			adminAccess
		);
	}

	private void validatePolicy(BoardPolicyCreateRequest policy) {
		if (policy.categoryRequired() && !policy.categoriesEnabled()) {
			throw new IllegalArgumentException("카테고리 필수 선택은 카테고리 사용 시에만 가능합니다.");
		}

		if (policy.imageRequired() && !policy.attachmentsEnabled()) {
			throw new IllegalArgumentException("이미지 필수 설정은 첨부파일 사용 시에만 가능합니다.");
		}

		if (policy.attachmentsEnabled()) {
			if (policy.maxFileCount() <= 0 || policy.maxFileSizeBytes() <= 0) {
				throw new IllegalArgumentException("첨부파일 사용 시 최대 개수와 파일당 용량은 0보다 커야 합니다.");
			}
		} else {
			if (policy.maxFileCount() != 0 || policy.maxFileSizeBytes() != 0L) {
				throw new IllegalArgumentException("첨부파일 미사용 시 최대 개수와 파일당 용량은 0이어야 합니다.");
			}
		}
	}
}