package io.github.kwp6zgnrsh.boardkitapi.post.api;

import java.net.URI;
import java.util.Optional;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostReplyCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostReplyUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostReplyResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.service.PostReplyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/boards/{boardSlug}/posts/{postId}/reply")
public class PostReplyApi {

	private final PostReplyService postReplyService;

	@GetMapping
	public ResponseEntity<PostReplyResponse> getPostReply(@PathVariable String boardSlug,
		@PathVariable @Positive Long postId,
		@RequestHeader(value = "X-Post-Password", required = false) String secretPassword,
		@AuthenticationPrincipal LoginMember loginMember, Authentication authentication) {
		Long memberId = loginMember == null ? null : loginMember.id();
		boolean adminAccess = hasAdminAuthority(authentication);

		Optional<PostReplyResponse> response = postReplyService.getPostReply(boardSlug, postId, memberId, adminAccess,
			secretPassword);

		return response.map(
			postReplyResponse -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(postReplyResponse))
			.orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());

	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<PostReplyResponse> createPostReply(@PathVariable String boardSlug,
		@PathVariable @Positive Long postId, @Valid @RequestBody PostReplyCreateRequest request,
		@AuthenticationPrincipal LoginMember loginMember, Authentication authentication) {
		Long memberId = requireMemberId(loginMember);
		boolean adminAccess = hasAdminAuthority(authentication);

		PostReplyResponse response = postReplyService.createPostReply(boardSlug, postId, request, memberId,
			adminAccess);

		URI location = URI.create("/api/boards/" + boardSlug + "/posts/" + postId + "/reply");

		return ResponseEntity.created(location).cacheControl(CacheControl.noStore()).body(response);
	}

	@PatchMapping(value = "/{replyId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Void> updatePostReply(@PathVariable String boardSlug, @PathVariable @Positive Long postId,
		@PathVariable @Positive Long replyId, @Valid @RequestBody PostReplyUpdateRequest request,
		@AuthenticationPrincipal LoginMember loginMember, Authentication authentication) {
		Long memberId = requireMemberId(loginMember);
		boolean adminAccess = hasAdminAuthority(authentication);

		postReplyService.updatePostReply(boardSlug, postId, replyId, request, memberId, adminAccess);

		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{replyId}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Void> deletePostReply(@PathVariable String boardSlug, @PathVariable @Positive Long postId,
		@PathVariable @Positive Long replyId, @RequestParam @PositiveOrZero Integer version,
		@AuthenticationPrincipal LoginMember loginMember, Authentication authentication) {
		Long memberId = requireMemberId(loginMember);
		boolean adminAccess = hasAdminAuthority(authentication);

		postReplyService.deletePostReply(boardSlug, postId, replyId, version, memberId, adminAccess);

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