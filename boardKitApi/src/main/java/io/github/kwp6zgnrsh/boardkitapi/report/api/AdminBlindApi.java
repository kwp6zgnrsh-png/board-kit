package io.github.kwp6zgnrsh.boardkitapi.report.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportTargetActionRequest;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminBlindedCommentPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminBlindedContentDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminBlindedPostPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.service.AdminBlindService;
import io.github.kwp6zgnrsh.boardkitapi.report.service.AdminContentModerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/blinded")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBlindApi {

	private final AdminBlindService adminBlindService;
	private final AdminContentModerationService adminContentModerationService;

	@GetMapping("/posts")
	public ResponseEntity<AdminBlindedPostPageResponse> getBlindedPosts(
		@RequestParam(defaultValue = "1") @Min(1) int page,
		@RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
		@AuthenticationPrincipal LoginMember loginMember) {

		AdminBlindedPostPageResponse response = adminBlindService.getBlindedPosts(page, size,
			requireMemberId(loginMember), true);

		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
	}

	@GetMapping("/comments")
	public ResponseEntity<AdminBlindedCommentPageResponse> getBlindedComments(
		@RequestParam(defaultValue = "1") @Min(1) int page,
		@RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
		@AuthenticationPrincipal LoginMember loginMember) {

		AdminBlindedCommentPageResponse response = adminBlindService.getBlindedComments(page, size,
			requireMemberId(loginMember), true);

		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
	}

	@GetMapping("/posts/{postId}")
	public ResponseEntity<AdminBlindedContentDetailResponse> getBlindedPost(@PathVariable @Positive Long postId,
		@AuthenticationPrincipal LoginMember loginMember) {

		AdminBlindedContentDetailResponse response = adminBlindService.getBlindedPost(postId,
			requireMemberId(loginMember), true);

		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
	}

	@GetMapping("/comments/{commentId}")
	public ResponseEntity<AdminBlindedContentDetailResponse> getBlindedComment(@PathVariable @Positive Long commentId,
		@AuthenticationPrincipal LoginMember loginMember) {

		AdminBlindedContentDetailResponse response = adminBlindService.getBlindedComment(commentId,
			requireMemberId(loginMember), true);

		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
	}

	@PatchMapping(value = "/posts/{postId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> moderatePost(@PathVariable @Positive Long postId,
		@Valid @RequestBody ReportTargetActionRequest request, @AuthenticationPrincipal LoginMember loginMember) {

		adminContentModerationService.moderateTarget(postId, null, request, requireMemberId(loginMember), true);

		return ResponseEntity.noContent().build();
	}

	@PatchMapping(value = "/posts/{postId}/comments/{commentId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> moderateComment(@PathVariable @Positive Long postId,
		@PathVariable @Positive Long commentId, @Valid @RequestBody ReportTargetActionRequest request,
		@AuthenticationPrincipal LoginMember loginMember) {

		adminContentModerationService.moderateTarget(postId, commentId, request, requireMemberId(loginMember), true);

		return ResponseEntity.noContent().build();
	}

	private Long requireMemberId(LoginMember loginMember) {
		if (loginMember == null || loginMember.id() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}

		return loginMember.id();
	}
}