package io.github.kwp6zgnrsh.boardkitapi.board.api;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.service.BoardService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/boards")
public class BoardApi {

	private final BoardService boardService;

	@GetMapping
	public List<BoardSummaryResponse> getBoards(Authentication authentication) {
		boolean adminAccess = hasAuthority(authentication, "ROLE_ADMIN");

		boolean memberAccess = adminAccess || hasAuthority(authentication, "ROLE_MEMBER");

		return boardService.getAccessibleBoards(memberAccess, adminAccess);
	}

	@GetMapping("/{boardSlug}")
	public BoardDetailResponse getBoardDetail(@PathVariable String boardSlug, Authentication authentication) {
		boolean adminAccess = hasAuthority(authentication, "ROLE_ADMIN");

		boolean memberAccess = adminAccess || hasAuthority(authentication, "ROLE_MEMBER");

		return boardService.getBoardDetail(boardSlug, memberAccess, adminAccess);
	}

	private boolean hasAuthority(Authentication authentication, String authority) {
		if (authentication == null) {
			return false;
		}

		return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(authority::equals);
	}
}