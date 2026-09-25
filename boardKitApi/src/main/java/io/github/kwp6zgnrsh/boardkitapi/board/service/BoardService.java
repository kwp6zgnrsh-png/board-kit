package io.github.kwp6zgnrsh.boardkitapi.board.service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import io.github.kwp6zgnrsh.boardkitapi.board.converter.BoardConverter;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardCategoryCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardCategoryUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardPolicyRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardAdminDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.exception.BoardConflictException;
import io.github.kwp6zgnrsh.boardkitapi.board.exception.BoardNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.board.exception.InvalidBoardRequestException;
import io.github.kwp6zgnrsh.boardkitapi.board.mapper.BoardMapper;
import io.github.kwp6zgnrsh.boardkitapi.board.model.Board;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardCategory;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardPolicy;
import io.github.kwp6zgnrsh.boardkitapi.board.policy.BoardReadAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@Service
@Validated
@RequiredArgsConstructor
public class BoardService {

	private final BoardMapper boardMapper;
	private final BoardConverter boardConverter;

	@Transactional
	public BoardCreateResponse createBoard(@NotNull @Valid BoardCreateRequest request,
										   @NotNull @Positive Long adminId) {

		BoardPolicyRequest policyRequest = request.policy();
		validatePolicy(policyRequest);

		if (policyRequest.categoryRequired() && request.categories().isEmpty()) {
			throw new InvalidBoardRequestException("카테고리 필수 게시판에는 하나 이상의 카테고리가 필요합니다.");
		}

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


		BoardReadAccess.validate(policy.getReadPermission(), memberAccess, adminAccess);

		List<BoardCategory> categories;

		if (policy.isCategoriesEnabled()) {
			categories = boardMapper.selectActiveCategoriesByBoardId(board.getId());
		} else {
			categories = List.of();
		}

		return boardConverter.toDetailResponse(board, policy, categories);
	}

	@Transactional(readOnly = true)
	public BoardAdminDetailResponse getBoardForAdmin(@NotBlank String slug) {
		Board board = boardMapper
			.selectActiveBoardBySlug(slug)
			.orElseThrow(BoardNotFoundException::new);

		BoardPolicy policy = requirePolicy(board.getId());

		List<BoardCategory> categories = boardMapper.selectActiveCategoriesByBoardId(board.getId());

		return boardConverter.toAdminDetailResponse(board, policy, categories);
	}

	@Transactional
	public void updateBoard(@NotBlank String slug,
						    @NotNull @Valid BoardUpdateRequest request) {

		validatePolicy(request.policy());

		Board board = boardMapper
			.selectActiveBoardBySlugForUpdate(slug)
			.orElseThrow(BoardNotFoundException::new);

		BoardPolicy currentPolicy = requirePolicy(board.getId());

		if (!Objects.equals(currentPolicy.getVersion(), request.version())) {
			throw new BoardConflictException();
		}

		List<BoardCategory> currentCategories = boardMapper.selectActiveCategoriesByBoardId(board.getId());

		validateUpdateCategories(request, currentCategories);

		Board updatedBoard = boardConverter.toUpdatedBoard(request, board.getId());

		if (boardMapper.updateBoard(updatedBoard) != 1) {
			throw new BoardNotFoundException();
		}

		BoardPolicy updatedPolicy = boardConverter.toUpdatedPolicy(request.policy(), board.getId(), request.version());

		if (boardMapper.updatePolicy(updatedPolicy) != 1) {
			throw new BoardConflictException();
		}

		if (request.policy().categoriesEnabled()) {
			updateCategories(board.getId(), request.categories());
		}
	}

	@Transactional
	public void archiveBoard(@NotBlank String slug) {
		Board board = boardMapper
			.selectActiveBoardBySlugForUpdate(slug)
			.orElseThrow(BoardNotFoundException::new);

		if (boardMapper.archiveBoard(board.getId()) != 1) {
			throw new BoardNotFoundException();
		}
	}

	private void validateUpdateCategories(BoardUpdateRequest request, List<BoardCategory> currentCategories) {
		if (!request.policy().categoriesEnabled()) {
			return;
		}

		if (request.policy().categoryRequired() && request.categories().isEmpty()) {
			throw new InvalidBoardRequestException("카테고리 필수 게시판에는 하나 이상의 카테고리가 필요합니다.");
		}

		Set<Long> activeCategoryIds = new HashSet<>();

		for (BoardCategory category : currentCategories) {
			activeCategoryIds.add(category.getId());
		}

		Set<Long> requestedIds = new HashSet<>();

		for (BoardCategoryUpdateRequest category : request.categories()) {
			if (category.id() == null) {
				continue;
			}

			if (!requestedIds.add(category.id())) {
				throw new InvalidBoardRequestException("같은 카테고리를 중복해서 요청할 수 없습니다.");
			}

			if (!activeCategoryIds.contains(category.id())) {
				throw new InvalidBoardRequestException("현재 게시판에서 사용할 수 없는 카테고리입니다.");
			}
		}
	}

	private void updateCategories(Long boardId,
								  List<BoardCategoryUpdateRequest> requests) {

		List<Long> retainedIds = requests.stream()
			.map(BoardCategoryUpdateRequest::id)
			.filter(Objects::nonNull)
			.toList();

		boardMapper.deactivateActiveCategoriesExcept(boardId, retainedIds);

		for (BoardCategoryUpdateRequest request : requests) {
			BoardCategory category = boardConverter.toUpdatedCategory(request, boardId);

			if (request.id() == null) {
				if (boardMapper.insertCategory(category) != 1 || category.getId() == null) {
					throw new IllegalStateException("카테고리 저장에 실패했습니다.");
				}
			} else {
				boardMapper.updateCategory(category);
			}
		}
	}

	private void validatePolicy(BoardPolicyRequest policy) {
		if (policy.categoryRequired() && !policy.categoriesEnabled()) {
			throw new InvalidBoardRequestException("카테고리 필수 선택은 카테고리 사용 시에만 가능합니다.");
		}

		if (policy.imageRequired() && !policy.attachmentsEnabled()) {
			throw new InvalidBoardRequestException("이미지 필수 설정은 첨부파일 사용 시에만 가능합니다.");
		}

		if (policy.attachmentsEnabled()) {
			if (policy.maxFileCount() <= 0 || policy.maxFileSizeBytes() <= 0) {
				throw new InvalidBoardRequestException("첨부파일 사용 시 최대 개수와 파일당 용량은 0보다 커야 합니다.");
			}
		} else {
			if (policy.maxFileCount() != 0 || policy.maxFileSizeBytes() != 0L) {
				throw new InvalidBoardRequestException("첨부파일 미사용 시 최대 개수와 파일당 용량은 0이어야 합니다.");
			}
		}
	}

	private BoardPolicy requirePolicy(Long boardId) {
		return boardMapper
			.selectPolicyByBoardId(boardId)
			.orElseThrow(() -> new IllegalStateException(
				"게시판 정책을 찾을 수 없습니다. boardId=" + boardId
			));
	}
}