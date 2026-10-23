package io.github.kwp6zgnrsh.boardkitapi.board.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardStatisticsResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.service.BoardStatisticsService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/board-statistics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBoardStatisticsApi {

	private final BoardStatisticsService statisticsService;

	@GetMapping
	public ResponseEntity<BoardStatisticsResponse> getStatistics(@RequestParam(required = false) @Positive Long boardId,
		@RequestParam(defaultValue = "30") int days, @AuthenticationPrincipal LoginMember loginMember) {
		if (loginMember == null || loginMember.id() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}

		BoardStatisticsResponse response = statisticsService.getStatistics(boardId, days, loginMember.id(), true);

		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
	}
}