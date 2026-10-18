package io.github.kwp6zgnrsh.boardkitapi.comment.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.comment.service.CommentLikeService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/boards/{boardSlug}/posts/{postId}/comments/{commentId}/like")
public class CommentLikeApi {

	private final CommentLikeService commentLikeService;

	@PutMapping
	public ResponseEntity<Void> likeComment(@PathVariable String boardSlug, @PathVariable @Positive Long postId,
		@PathVariable @Positive Long commentId,
		@RequestHeader(value = "X-Post-Password", required = false) String secretPassword,
		@AuthenticationPrincipal LoginMember loginMember, Authentication authentication) {
		Long memberId = requireMemberId(loginMember);
		boolean adminAccess = hasAdminAuthority(authentication);

		commentLikeService.likeComment(boardSlug, postId, commentId, memberId, adminAccess, secretPassword);

		return ResponseEntity.noContent().build();
	}

	@DeleteMapping
	public ResponseEntity<Void> unlikeComment(@PathVariable String boardSlug, @PathVariable @Positive Long postId,
		@PathVariable @Positive Long commentId,
		@RequestHeader(value = "X-Post-Password", required = false) String secretPassword,
		@AuthenticationPrincipal LoginMember loginMember, Authentication authentication) {
		Long memberId = requireMemberId(loginMember);
		boolean adminAccess = hasAdminAuthority(authentication);

		commentLikeService.unlikeComment(boardSlug, postId, commentId, memberId, adminAccess, secretPassword);

		return ResponseEntity.noContent().build();
	}

	private Long requireMemberId(LoginMember loginMember) {
		if (loginMember == null || loginMember.id() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}

		return loginMember.id();
	}

	private boolean hasAdminAuthority(Authentication authentication) {
		if (authentication == null) {
			return false;
		}

		return authentication.getAuthorities()
			.stream()
			.anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
	}
}