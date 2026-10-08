package io.github.kwp6zgnrsh.boardkitapi.comment.api;

import java.net.URI;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.comment.dto.request.CommentCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.comment.dto.request.CommentUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.comment.dto.response.CommentCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.comment.dto.response.CommentPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.comment.service.CommentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/boards/{boardSlug}/posts/{postId}/comments")
public class CommentApi {

	private final CommentService commentService;

	@PostMapping
	public ResponseEntity<CommentCreateResponse> createComment(@PathVariable String boardSlug,
		@PathVariable @Positive Long postId, @Valid @RequestBody CommentCreateRequest request,
		@RequestHeader(value = "X-Post-Password", required = false) String secretPassword,
		@AuthenticationPrincipal LoginMember loginMember, Authentication authentication) {

		if (loginMember == null || loginMember.id() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}

		Long memberId = requireMemberId(loginMember);
		boolean adminAccess = hasAuthority(authentication, "ROLE_ADMIN");

		CommentCreateResponse response = commentService.createComment(boardSlug, postId, request, memberId, adminAccess,
			secretPassword);

		URI location = URI.create("/api/boards/" + boardSlug + "/posts/" + postId + "/comments/" + response.id());

		return ResponseEntity.created(location).body(response);
	}

	@GetMapping
	public ResponseEntity<CommentPageResponse> getComments(@PathVariable String boardSlug,
		@PathVariable @Positive Long postId, @RequestParam(defaultValue = "1") @Min(1) int page,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		@RequestHeader(value = "X-Post-Password", required = false) String secretPassword,
		@AuthenticationPrincipal LoginMember loginMember, Authentication authentication) {

		boolean adminAccess = hasAuthority(authentication, "ROLE_ADMIN");
		boolean memberAccess = adminAccess || hasAuthority(authentication, "ROLE_MEMBER");

		CommentPageResponse response = commentService.getComments(boardSlug, postId, page, size,
			loginMember == null ? null : loginMember.id(), memberAccess, adminAccess, secretPassword);

		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
	}

	@PatchMapping("/{commentId}")
	public ResponseEntity<Void> updateComment(@PathVariable String boardSlug, @PathVariable @Positive Long postId,
		@PathVariable @Positive Long commentId, @Valid @RequestBody CommentUpdateRequest request,
		@RequestHeader(value = "X-Post-Password", required = false) String secretPassword,
		@AuthenticationPrincipal LoginMember loginMember, Authentication authentication) {

		Long memberId = requireMemberId(loginMember);
		boolean adminAccess = hasAuthority(authentication, "ROLE_ADMIN");

		commentService.updateComment(boardSlug, postId, commentId, request, memberId, adminAccess, secretPassword);

		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{commentId}")
	public ResponseEntity<Void> deleteComment(@PathVariable String boardSlug, @PathVariable @Positive Long postId,
		@PathVariable @Positive Long commentId, @RequestParam @PositiveOrZero Integer version,
		@AuthenticationPrincipal LoginMember loginMember, Authentication authentication) {

		Long memberId = requireMemberId(loginMember);
		boolean adminAccess = hasAuthority(authentication, "ROLE_ADMIN");

		commentService.deleteComment(boardSlug, postId, commentId, version, memberId, adminAccess);

		return ResponseEntity.noContent().build();
	}

	private Long requireMemberId(LoginMember loginMember) {
		if (loginMember == null || loginMember.id() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}

		return loginMember.id();
	}

	private boolean hasAuthority(Authentication authentication, String authority) {
		if (authentication == null) {
			return false;
		}

		return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(authority::equals);
	}
}