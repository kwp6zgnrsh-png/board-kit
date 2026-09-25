package io.github.kwp6zgnrsh.boardkitapi.board.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.kwp6zgnrsh.boardkitapi.board.converter.BoardConverter;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardCategoryUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardPolicyRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.exception.BoardConflictException;
import io.github.kwp6zgnrsh.boardkitapi.board.exception.InvalidBoardRequestException;
import io.github.kwp6zgnrsh.boardkitapi.board.mapper.BoardMapper;
import io.github.kwp6zgnrsh.boardkitapi.board.model.Board;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardCategory;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardPolicy;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

	@Mock
	private BoardMapper boardMapper;

	@Mock
	private BoardConverter boardConverter;

	private BoardService boardService;

	@BeforeEach
	void setUp() {
		boardService = new BoardService(
			boardMapper,
			boardConverter
		);
	}

	@Test
	@DisplayName("게시판 설정과 기존 카테고리를 함께 수정한다")
	void updateBoardAndExistingCategories() {
		BoardUpdateRequest request = updateRequest(
			List.of(
				new BoardCategoryUpdateRequest(
					10L,
					"수정된 카테고리",
					0
				)
			),
			true,
			3
		);

		Board board = board();
		BoardPolicy currentPolicy = policyModel(3);
		Board updatedBoard = board();
		BoardPolicy updatedPolicy = policyModel(3);
		BoardCategory currentCategory = category(10L, "기존 카테고리");
		BoardCategory removedCategory = category(11L, "삭제할 카테고리");
		BoardCategory updatedCategory = category(10L, "수정된 카테고리");

		when(boardMapper.selectActiveBoardBySlugForUpdate("free"))
			.thenReturn(Optional.of(board));
		when(boardMapper.selectPolicyByBoardId(1L))
			.thenReturn(Optional.of(currentPolicy));
		when(boardMapper.selectActiveCategoriesByBoardId(1L))
			.thenReturn(List.of(currentCategory, removedCategory));
		when(boardConverter.toUpdatedBoard(request, 1L))
			.thenReturn(updatedBoard);
		when(boardMapper.updateBoard(updatedBoard))
			.thenReturn(1);
		when(boardConverter.toUpdatedPolicy(request.policy(), 1L, 3))
			.thenReturn(updatedPolicy);
		when(boardMapper.updatePolicy(updatedPolicy))
			.thenReturn(1);
		when(boardConverter.toUpdatedCategory(request.categories().getFirst(), 1L))
			.thenReturn(updatedCategory);

		boardService.updateBoard("free", request);

		verify(boardMapper)
			.deactivateActiveCategoriesExcept(1L, List.of(10L));
		verify(boardMapper)
			.updateCategory(updatedCategory);
	}

	@Test
	@DisplayName("이전 버전으로 게시판 설정을 수정하면 충돌이 발생한다")
	void rejectBoardUpdateWithStaleVersion() {
		BoardUpdateRequest request = updateRequest(List.of(), false, 2);

		when(boardMapper.selectActiveBoardBySlugForUpdate("free"))
			.thenReturn(Optional.of(board()));
		when(boardMapper.selectPolicyByBoardId(1L))
			.thenReturn(Optional.of(policyModel(3)));

		assertThrows(
			BoardConflictException.class,
			() -> boardService.updateBoard(
				"free",
				request
			)
		);

		verify(boardMapper, never())
			.updateBoard(org.mockito.ArgumentMatchers.any());
		verify(boardMapper, never())
			.updatePolicy(org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("다른 게시판의 카테고리 ID로 수정할 수 없다")
	void rejectCategoryFromAnotherBoard() {
		BoardUpdateRequest request = updateRequest(
			List.of(
				new BoardCategoryUpdateRequest(
					99L,
					"다른 게시판 카테고리",
					0
				)
			),
			true,
			3
		);

		when(boardMapper.selectActiveBoardBySlugForUpdate("free"))
			.thenReturn(Optional.of(board()));
		when(boardMapper.selectPolicyByBoardId(1L))
			.thenReturn(Optional.of(policyModel(3)));
		when(boardMapper.selectActiveCategoriesByBoardId(1L))
			.thenReturn(List.of(
				category(10L, "기존 카테고리")
			));

		assertThrows(
			InvalidBoardRequestException.class,
			() -> boardService.updateBoard(
				"free",
				request
			)
		);

		verify(boardMapper, never())
			.updateBoard(org.mockito.ArgumentMatchers.any());
		verify(boardMapper, never())
			.updatePolicy(org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("게시판을 삭제하면 보관 상태로 변경한다")
	void archiveActiveBoard() {
		when(boardMapper.selectActiveBoardBySlugForUpdate("free"))
			.thenReturn(Optional.of(board()));
		when(boardMapper.archiveBoard(1L))
			.thenReturn(1);

		boardService.archiveBoard("free");

		verify(boardMapper).archiveBoard(1L);
	}

	private BoardUpdateRequest updateRequest(List<BoardCategoryUpdateRequest> categories,
											 boolean categoriesEnabled,
											 int version) {

		return new BoardUpdateRequest(
			"자유게시판",
			"수정된 설명",
			"LIST",
			0,
			policyRequest(categoriesEnabled),
			categories,
			version
		);
	}

	private BoardPolicyRequest policyRequest(boolean categoriesEnabled) {
		return new BoardPolicyRequest(
			"PUBLIC",
			"MEMBER",
			"MEMBER",
			"OFF",
			categoriesEnabled,
			false,
			false,
			"IMAGE",
			0,
			0L,
			false,
			false,
			"FIRST_IMAGE",
			true,
			true,
			true,
			true,
			false
		);
	}

	private Board board() {
		return Board.builder()
			.id(1L)
			.name("자유게시판")
			.slug("free")
			.status("ACTIVE")
			.build();
	}

	private BoardPolicy policyModel(int version) {
		return BoardPolicy.builder()
			.boardId(1L)
			.version(version)
			.build();
	}

	private BoardCategory category(Long id, String name) {
		return BoardCategory.builder()
			.id(id)
			.boardId(1L)
			.name(name)
			.sortOrder(0)
			.active(true)
			.build();
	}
}