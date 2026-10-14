package io.github.kwp6zgnrsh.boardkitapi.auth.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import io.github.kwp6zgnrsh.boardkitapi.auth.dto.request.LoginRequest;
import io.github.kwp6zgnrsh.boardkitapi.auth.dto.response.CurrentMemberResponse;
import io.github.kwp6zgnrsh.boardkitapi.auth.exception.InvalidCredentialsException;
import io.github.kwp6zgnrsh.boardkitapi.auth.mapper.RevokedTokenMapper;
import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginResult;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtPayload;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtTokenProvider;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.member.model.Member;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final MemberMapper memberMapper;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final RevokedTokenMapper revokedTokenMapper;

	public LoginResult login(LoginRequest request) {
		Member member = memberMapper.selectByLoginId(request.loginId().trim())
			.orElseThrow(InvalidCredentialsException::new);

		if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}

		String accessToken = jwtTokenProvider.createAccessToken(member.getId(), member.getRole());
		JwtPayload payload = jwtTokenProvider.parseAccessToken(accessToken);

		return new LoginResult(accessToken, payload.expiresAt(), member.getId(), member.getDisplayName(),
			member.getRole());
	}

	public void logout(JwtPayload payload) {
		LocalDateTime expiresAt = LocalDateTime.ofInstant(payload.expiresAt(), ZoneOffset.UTC);

		revokedTokenMapper.insertRevokedToken(payload.tokenId(), expiresAt);
	}

	public CurrentMemberResponse getCurrentMember(Long memberId) {
		Member member = memberMapper.selectActiveMemberById(memberId).orElseThrow(MemberUnavailableException::new);

		return new CurrentMemberResponse(member.getId(), member.getDisplayName(), member.getRole());
	}
}