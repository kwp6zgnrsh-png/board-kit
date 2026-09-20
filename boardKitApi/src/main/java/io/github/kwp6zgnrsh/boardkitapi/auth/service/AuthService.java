package io.github.kwp6zgnrsh.boardkitapi.auth.service;

import io.github.kwp6zgnrsh.boardkitapi.auth.dto.request.LoginRequest;
import io.github.kwp6zgnrsh.boardkitapi.auth.dto.response.LoginResponse;
import io.github.kwp6zgnrsh.boardkitapi.auth.exception.InvalidCredentialsException;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtTokenProvider;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.member.model.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final MemberMapper memberMapper;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;

	public LoginResponse login(LoginRequest request) {
		Member member = memberMapper
			.selectByLoginId(request.loginId().trim())
			.orElseThrow(InvalidCredentialsException::new);

		if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}

		String accessToken = jwtTokenProvider.createAccessToken(
			member.getId(),
			member.getRole()
		);

		return new LoginResponse(
			accessToken,
			"Bearer",
			member.getId(),
			member.getDisplayName(),
			member.getRole()
		);
	}
}