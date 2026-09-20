package io.github.kwp6zgnrsh.boardkitapi.auth.api;

import io.github.kwp6zgnrsh.boardkitapi.auth.dto.request.LoginRequest;
import io.github.kwp6zgnrsh.boardkitapi.auth.dto.response.LoginResponse;
import io.github.kwp6zgnrsh.boardkitapi.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthApi {

	private final AuthService authService;

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(authService.login(request));
	}
}