package io.github.kwp6zgnrsh.boardkitapi.board.api;

import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.request.BoardCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.service.BoardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/boards")
public class AdminBoardApi {

	private final BoardService boardService;

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<BoardCreateResponse> createBoard(@Valid @RequestBody BoardCreateRequest request,
														   @AuthenticationPrincipal LoginMember loginMember) {
		if (loginMember == null || loginMember.id() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}

		BoardCreateResponse response = boardService.createBoard(request, loginMember.id());

		URI location = URI.create("/api/boards/" + response.slug());

		return ResponseEntity.created(location).body(response);
	}
}