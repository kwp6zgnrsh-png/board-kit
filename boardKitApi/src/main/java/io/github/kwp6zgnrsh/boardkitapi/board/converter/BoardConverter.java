package io.github.kwp6zgnrsh.boardkitapi.board.converter;

import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardCategoryCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardPolicyCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardCategoryResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardPolicyResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.model.Board;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardCategory;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardPolicy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BoardConverter {

	BoardPolicyResponse toPolicyResponse(BoardPolicy policy);

	BoardCategoryResponse toCategoryResponse(BoardCategory category);

	@Mapping(target = "id", source = "board.id")
	@Mapping(target = "name", source = "board.name")
	@Mapping(target = "slug", source = "board.slug")
	@Mapping(target = "description", source = "board.description")
	@Mapping(target = "displayType", source = "board.displayType")
	@Mapping(target = "sortOrder", source = "board.sortOrder")
	@Mapping(target = "policy", source = "policy")
	@Mapping(target = "categories", source = "categories")
	BoardDetailResponse toDetailResponse(
		Board board,
		BoardPolicy policy,
		List<BoardCategory> categories
	);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "status", constant = "ACTIVE")
	@Mapping(target = "createdBy", source = "adminId")
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	Board toBoard(
		BoardCreateRequest request,
		Long adminId
	);

	@Mapping(target = "boardId", source = "boardId")
	@Mapping(target = "version", constant = "0")
	@Mapping(target = "updatedAt", ignore = true)
	BoardPolicy toPolicy(
		BoardPolicyCreateRequest request,
		Long boardId
	);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "boardId", source = "boardId")
	@Mapping(target = "active", constant = "true")
	BoardCategory toCategory(
		BoardCategoryCreateRequest request,
		Long boardId
	);

	BoardCreateResponse toCreateResponse(Board board);
}