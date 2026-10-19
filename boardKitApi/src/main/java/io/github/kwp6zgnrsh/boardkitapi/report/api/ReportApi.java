package io.github.kwp6zgnrsh.boardkitapi.report.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.ReportCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.service.ReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/boards/{boardSlug}/posts/{postId}")
public class ReportApi {

	private final ReportService reportService;

	@PostMapping(value = "/reports", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ReportCreateResponse> createPostReport(@PathVariable String boardSlug,
		@PathVariable @Positive Long postId, @Valid @RequestBody ReportCreateRequest request,
		@RequestHeader(value = "X-Post-Password", required = false) String secretPassword,
		@AuthenticationPrincipal LoginMember loginMember, Authentication authentication) {
		Long reportId = reportService.createPostReport(boardSlug, postId, request, requireMemberId(loginMember),
			hasAdminAuthority(authentication), secretPassword);

		return ResponseEntity.status(HttpStatus.CREATED)
			.cacheControl(CacheControl.noStore())
			.body(new ReportCreateResponse(reportId));
	}

	@PostMapping(value = "/comments/{commentId}/reports", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ReportCreateResponse> createCommentReport(@PathVariable String boardSlug,
		@PathVariable @Positive Long postId, @PathVariable @Positive Long commentId,
		@Valid @RequestBody ReportCreateRequest request,
		@RequestHeader(value = "X-Post-Password", required = false) String secretPassword,
		@AuthenticationPrincipal LoginMember loginMember, Authentication authentication) {
		Long reportId = reportService.createCommentReport(boardSlug, postId, commentId, request,
			requireMemberId(loginMember), hasAdminAuthority(authentication), secretPassword);

		return ResponseEntity.status(HttpStatus.CREATED)
			.cacheControl(CacheControl.noStore())
			.body(new ReportCreateResponse(reportId));
	}

	private Long requireMemberId(LoginMember loginMember) {
		if (loginMember == null || loginMember.id() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}

		return loginMember.id();
	}

	private boolean hasAdminAuthority(Authentication authentication) {
		if (authentication == null)
			return false;

		return authentication.getAuthorities()
			.stream()
			.anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
	}
}