package io.github.kwp6zgnrsh.boardkitapi.post.api;

import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

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

		boolean adminAccess = hasAuthority(authentication);

		PostCreateResponse response = postService.createPost(
			boardSlug,
			request,
			loginMember.id(),
			adminAccess
		);

		URI location = URI.create("/api/boards/" + boardSlug + "/posts/" + response.id());

		return ResponseEntity.created(location).body(response);
	}

	private boolean hasAuthority(Authentication authentication) {
		if (authentication == null) {
			return false;
		}

		return authentication.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.anyMatch("ROLE_ADMIN"::equals);
	}
}