package io.github.kwp6zgnrsh.boardkitapi.auth.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.kwp6zgnrsh.boardkitapi.auth.cookie.AuthCookieFactory;
import io.github.kwp6zgnrsh.boardkitapi.auth.dto.request.LoginRequest;
import io.github.kwp6zgnrsh.boardkitapi.auth.dto.response.CurrentMemberResponse;
import io.github.kwp6zgnrsh.boardkitapi.auth.dto.response.LoginResponse;
import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginResult;
import io.github.kwp6zgnrsh.boardkitapi.auth.service.AuthService;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtPayload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthApi {

	private final AuthService authService;
	private final AuthCookieFactory authCookieFactory;

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		LoginResult result = authService.login(request);
		ResponseCookie cookie = authCookieFactory.createAccessTokenCookie(result.accessToken(), result.expiresAt());
		LoginResponse response = new LoginResponse(result.memberId(), result.displayName(), result.role());

		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(response);
	}

	@GetMapping("/csrf")
	public ResponseEntity<Void> csrf() {
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(Authentication authentication) {
		if (!(authentication.getDetails() instanceof JwtPayload payload)) {
			throw new IllegalStateException("인증된 JWT 정보를 찾을 수 없습니다.");
		}

		authService.logout(payload);

		ResponseCookie cookie = authCookieFactory.expireAccessTokenCookie();

		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
	}

	@GetMapping("/me")
	public ResponseEntity<CurrentMemberResponse> getCurrentMember(@AuthenticationPrincipal LoginMember loginMember) {
		return ResponseEntity.ok(authService.getCurrentMember(loginMember.id()));
	}
}