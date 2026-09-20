package io.github.kwp6zgnrsh.boardkitapi.board.service;

import java.util.List;

import io.github.kwp6zgnrsh.boardkitapi.board.converter.BoardConverter;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardCategoryCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardPolicyCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardCategoryResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardPolicyResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.exception.BoardAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.board.exception.BoardNotFoundException;
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
	private final BoardConverter boardConverter;

	@Transactional
	public BoardCreateResponse createBoard(@NotNull @Valid BoardCreateRequest request,
										   @NotNull @Positive Long adminId) {

		BoardPolicyCreateRequest policyRequest = request.policy();
		validatePolicy(policyRequest);

		Board board = boardConverter.toBoard(request, adminId);

		int boardCount = boardMapper.insertBoard(board);

		if (boardCount != 1 || board.getId() == null) {
			throw new IllegalStateException("게시판 저장에 실패했습니다.");
		}

		BoardPolicy policy = boardConverter.toPolicy(
			policyRequest,
			board.getId()
		);

		if (boardMapper.insertPolicy(policy) != 1) {
			throw new IllegalStateException("게시판 정책 저장에 실패했습니다.");
		}

		for (BoardCategoryCreateRequest categoryRequest : request.categories()) {
			BoardCategory category = boardConverter.toCategory(
				categoryRequest,
				board.getId()
			);

			if (boardMapper.insertCategory(category) != 1) {
				throw new IllegalStateException("카테고리 저장에 실패했습니다.");
			}
		}

		return boardConverter.toCreateResponse(board);
	}

	@Transactional(readOnly = true)
	public List<BoardSummaryResponse> getAccessibleBoards(boolean memberAccess, boolean adminAccess) {
		return boardMapper.selectAccessibleBoards(
			memberAccess,
			adminAccess
		);
	}

	@Transactional(readOnly = true)
	public BoardDetailResponse getBoardDetail(String slug, boolean memberAccess, boolean adminAccess) {
		Board board = boardMapper
			.selectActiveBoardBySlug(slug)
			.orElseThrow(BoardNotFoundException::new);

		BoardPolicy policy = boardMapper
			.selectPolicyByBoardId(board.getId())
			.orElseThrow(() -> new IllegalStateException(
				"게시판 정책을 찾을 수 없습니다. boardId=" + board.getId()
			));

		if (!canReadBoard(policy.getReadPermission(), memberAccess, adminAccess)) {
			throw new BoardAccessDeniedException();
		}

		List<BoardCategory> categories;

		if (policy.isCategoriesEnabled()) {
			categories = boardMapper.selectActiveCategoriesByBoardId(board.getId());
		} else {
			categories = List.of();
		}

		return boardConverter.toDetailResponse(
			board,
			policy,
			categories
		);
	}

	private boolean canReadBoard(String readPermission, boolean memberAccess, boolean adminAccess) {
		return switch (readPermission) {
			case "PUBLIC" -> true;
			case "MEMBER" -> memberAccess || adminAccess;
			case "ADMIN" -> adminAccess;
			default -> throw new IllegalStateException("지원하지 않는 읽기 권한입니다: " + readPermission);
		};
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