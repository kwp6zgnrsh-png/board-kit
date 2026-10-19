package io.github.kwp6zgnrsh.boardkitapi.board.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardCategoryCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardCategoryUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardPolicyRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardAdminDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardCategoryResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardPolicyResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.model.Board;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardCategory;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardPolicy;

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
	@Mapping(target = "status", source = "board.status")
	BoardDetailResponse toDetailResponse(Board board, BoardPolicy policy, List<BoardCategory> categories);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "status", constant = "ACTIVE")
	@Mapping(target = "createdBy", source = "adminId")
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	Board toBoard(BoardCreateRequest request, Long adminId);

	@Mapping(target = "boardId", source = "boardId")
	@Mapping(target = "version", constant = "0")
	@Mapping(target = "updatedAt", ignore = true)
	BoardPolicy toPolicy(BoardPolicyRequest request, Long boardId);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "boardId", source = "boardId")
	@Mapping(target = "active", constant = "true")
	BoardCategory toCategory(BoardCategoryCreateRequest request, Long boardId);

	BoardCreateResponse toCreateResponse(Board board);

	@Mapping(target = "id", source = "board.id")
	@Mapping(target = "name", source = "board.name")
	@Mapping(target = "slug", source = "board.slug")
	@Mapping(target = "description", source = "board.description")
	@Mapping(target = "displayType", source = "board.displayType")
	@Mapping(target = "sortOrder", source = "board.sortOrder")
	@Mapping(target = "version", source = "policy.version")
	@Mapping(target = "policy", source = "policy")
	@Mapping(target = "categories", source = "categories")
	@Mapping(target = "status", source = "board.status")
	BoardAdminDetailResponse toAdminDetailResponse(Board board, BoardPolicy policy, List<BoardCategory> categories);

	@Mapping(target = "id", source = "boardId")
	@Mapping(target = "slug", ignore = true)
	@Mapping(target = "status", ignore = true)
	@Mapping(target = "createdBy", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	Board toUpdatedBoard(BoardUpdateRequest request, Long boardId);

	@Mapping(target = "boardId", source = "boardId")
	@Mapping(target = "version", source = "version")
	@Mapping(target = "updatedAt", ignore = true)
	BoardPolicy toUpdatedPolicy(BoardPolicyRequest request, Long boardId, Integer version);

	@Mapping(target = "boardId", source = "boardId")
	@Mapping(target = "active", constant = "true")
	BoardCategory toUpdatedCategory(BoardCategoryUpdateRequest request, Long boardId);
}