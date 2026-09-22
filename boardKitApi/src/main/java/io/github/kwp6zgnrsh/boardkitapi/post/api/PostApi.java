package io.github.kwp6zgnrsh.boardkitapi.post.api;

import java.net.URI;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.service.PostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/boards/{boardSlug}/posts")
public class PostApi {

	private final PostService postService;

	@PostMapping
	public ResponseEntity<PostCreateResponse> createPost(@PathVariable String boardSlug,
														 @Valid @RequestBody PostCreateRequest request,
														 @AuthenticationPrincipal LoginMember loginMember,
														 Authentication authentication) {

		if (loginMember == null || loginMember.id() == null) {
			throw new ResponseStatusException(
				HttpStatus.UNAUTHORIZED,
				"로그인이 필요합니다."
			);
		}

		boolean adminAccess = hasAuthority(authentication, "ROLE_ADMIN");

		PostCreateResponse response = postService.createPost(
			boardSlug,
			request,
			loginMember.id(),
			adminAccess
		);

		URI location = URI.create("/api/boards/" + boardSlug + "/posts/" + response.id());

		return ResponseEntity.created(location).body(response);
	}

	@GetMapping
	public PostPageResponse getPosts(@PathVariable String boardSlug, @RequestParam(defaultValue = "1")
									 @Min(1) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
									 Authentication authentication) {
		boolean adminAccess = hasAuthority(authentication, "ROLE_ADMIN");

		boolean memberAccess = adminAccess || hasAuthority(authentication, "ROLE_MEMBER");

		return postService.getPosts(
			boardSlug,
			page,
			size,
			memberAccess,
			adminAccess
		);
	}

	@GetMapping("/{postId}")
	public ResponseEntity<PostDetailResponse> getPostDetail(@PathVariable String boardSlug,
														    @PathVariable @Positive Long postId,
															@RequestHeader(value = "X-Post-Password", required = false)
															String secretPassword,
														    @AuthenticationPrincipal LoginMember loginMember,
															Authentication authentication) {
		boolean adminAccess = hasAuthority(authentication, "ROLE_ADMIN");

		boolean memberAccess = adminAccess || hasAuthority(authentication, "ROLE_MEMBER");

		Long memberId = loginMember == null ? null : loginMember.id();

		PostDetailResponse response =
			postService.getPostDetail(
				boardSlug,
				postId,
				memberId,
				memberAccess,
				adminAccess,
				secretPassword
			);

		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(response);
	}

	private boolean hasAuthority(Authentication authentication, String authority) {
		if (authentication == null) {
			return false;
		}

		return authentication.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.anyMatch(authority::equals);
	}
}