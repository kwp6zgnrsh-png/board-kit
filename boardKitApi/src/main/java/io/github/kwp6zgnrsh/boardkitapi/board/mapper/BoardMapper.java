package io.github.kwp6zgnrsh.boardkitapi.board.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.model.Board;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardCategory;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardPolicy;

@Mapper
public interface BoardMapper {

	int insertBoard(Board board);

	int insertPolicy(BoardPolicy policy);

	int insertCategory(BoardCategory category);

	List<BoardSummaryResponse> selectAccessibleBoards(
		@Param("memberAccess") boolean memberAccess,
		@Param("adminAccess") boolean adminAccess
	);

	Optional<Board> selectActiveBoardBySlug(@Param("slug") String slug);

	Optional<BoardPolicy> selectPolicyByBoardId(@Param("boardId") Long boardId);

	List<BoardCategory> selectActiveCategoriesByBoardId(@Param("boardId") Long boardId);

	boolean existsActiveCategory(@Param("boardId") Long boardId, @Param("categoryId") Long categoryId);

	Optional<Board> selectActiveBoardBySlugForUpdate(@Param("slug") String slug);

	int updateBoard(Board board);

	int updatePolicy(BoardPolicy policy);

	int updateCategory(BoardCategory category);

	int deactivateActiveCategoriesExcept(@Param("boardId") Long boardId, @Param("retainedIds") List<Long> retainedIds);

	int archiveBoard(@Param("boardId") Long boardId);
}