package io.github.kwp6zgnrsh.boardkitapi.member.api;

import java.net.URI;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.kwp6zgnrsh.boardkitapi.auth.cookie.AuthCookieFactory;
import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberDisplayNameUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberPasswordUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MemberCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MemberProfileResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MyCommentPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MyPostPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.service.MemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberApi {

	private final MemberService memberService;
	private final AuthCookieFactory authCookieFactory;

	@PostMapping
	public ResponseEntity<MemberCreateResponse> createMember(@Valid @RequestBody MemberCreateRequest request) {
		MemberCreateResponse response = memberService.createMember(request);

		URI location = URI.create("/api/members/" + response.id());

		return ResponseEntity.created(location).body(response);
	}

	@GetMapping("/me")
	public ResponseEntity<MemberProfileResponse> getProfile(@AuthenticationPrincipal LoginMember loginMember) {
		MemberProfileResponse response = memberService.getProfile(loginMember.id());

		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
	}

	@GetMapping("/me/posts")
	public ResponseEntity<MyPostPageResponse> getMyPosts(@AuthenticationPrincipal LoginMember loginMember,
		@RequestParam(defaultValue = "1") @Min(1) int page,
		@RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
		MyPostPageResponse response = memberService.getMyPosts(loginMember.id(), page, size);

		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
	}

	@GetMapping("/me/comments")
	public ResponseEntity<MyCommentPageResponse> getMyComments(@AuthenticationPrincipal LoginMember loginMember,
		@RequestParam(defaultValue = "1") @Min(1) int page,
		@RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
		MyCommentPageResponse response = memberService.getMyComments(loginMember.id(), page, size);

		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
	}

	@PatchMapping("/me/display-name")
	public ResponseEntity<Void> updateDisplayName(@AuthenticationPrincipal LoginMember loginMember,
		@Valid @RequestBody MemberDisplayNameUpdateRequest request) {
		memberService.updateDisplayName(loginMember.id(), request);

		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/me/password")
	public ResponseEntity<Void> updatePassword(@AuthenticationPrincipal LoginMember loginMember,
		@Valid @RequestBody MemberPasswordUpdateRequest request) {
		memberService.updatePassword(loginMember.id(), request);

		ResponseCookie cookie = authCookieFactory.expireAccessTokenCookie();

		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
	}
}