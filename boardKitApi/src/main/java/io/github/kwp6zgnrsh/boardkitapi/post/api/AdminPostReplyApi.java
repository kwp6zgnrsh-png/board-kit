package io.github.kwp6zgnrsh.boardkitapi.post.api;

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
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostReplyFilter;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.AdminPostReplyPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.service.PostReplyService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/post-replies")
public class AdminPostReplyApi {

	private final PostReplyService postReplyService;

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<AdminPostReplyPageResponse> getPostReplyTargets(
		@RequestParam(defaultValue = "UNANSWERED") PostReplyFilter filter,
		@RequestParam(defaultValue = "1") @Min(1) int page,
		@RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
		@AuthenticationPrincipal LoginMember loginMember) {
		if (loginMember == null || loginMember.id() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}

		AdminPostReplyPageResponse response = postReplyService.getAdminPostReplyTargets(filter, page, size,
			loginMember.id(), true);

		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
	}
}