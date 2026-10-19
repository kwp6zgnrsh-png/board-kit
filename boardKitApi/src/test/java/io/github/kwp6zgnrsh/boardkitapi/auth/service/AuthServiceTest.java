package io.github.kwp6zgnrsh.boardkitapi.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;

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

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private MemberMapper memberMapper;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private RevokedTokenMapper revokedTokenMapper;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(memberMapper, passwordEncoder, jwtTokenProvider, revokedTokenMapper);
	}

	@Test
	@DisplayName("아이디와 비밀번호가 일치하면 JWT와 회원 정보를 반환한다")
	void loginWithValidCredentials() {
		LoginRequest request = new LoginRequest("admin", "1234");
		Member member = adminMember();
		Instant expiresAt = Instant.now().plusSeconds(1800);

		when(memberMapper.selectByLoginId("admin")).thenReturn(Optional.of(member));
		when(passwordEncoder.matches("1234", "encoded-password")).thenReturn(true);
		when(jwtTokenProvider.createAccessToken(1L, "ADMIN", 3L)).thenReturn("access-token");
		when(jwtTokenProvider.parseAccessToken("access-token")).thenReturn(
			new JwtPayload(1L, "ADMIN", "test-token-id", expiresAt, 3L));

		LoginResult result = authService.login(request);

		assertAll(() -> assertEquals("access-token", result.accessToken()),
			() -> assertEquals(expiresAt, result.expiresAt()), () -> assertEquals(1L, result.memberId()),
			() -> assertEquals("관리자", result.displayName()), () -> assertEquals("ADMIN", result.role()));

		verify(memberMapper).selectByLoginId("admin");
		verify(passwordEncoder).matches("1234", "encoded-password");
		verify(jwtTokenProvider).createAccessToken(1L, "ADMIN", 3L);
		verify(jwtTokenProvider).parseAccessToken("access-token");
	}

	@Test
	@DisplayName("존재하지 않는 아이디로 로그인할 수 없다")
	void rejectLoginWhenMemberDoesNotExist() {
		LoginRequest request = new LoginRequest("unknown", "1234");

		when(memberMapper.selectByLoginId("unknown")).thenReturn(Optional.empty());

		assertThrows(InvalidCredentialsException.class, () -> authService.login(request));

		verifyNoInteractions(passwordEncoder, jwtTokenProvider);
	}

	@Test
	@DisplayName("비밀번호가 일치하지 않으면 로그인할 수 없다")
	void rejectLoginWhenPasswordDoesNotMatch() {
		LoginRequest request = new LoginRequest("admin", "wrong-password");
		Member member = adminMember();

		when(memberMapper.selectByLoginId("admin")).thenReturn(Optional.of(member));
		when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

		assertThrows(InvalidCredentialsException.class, () -> authService.login(request));

		verifyNoInteractions(jwtTokenProvider);
	}

	@ParameterizedTest(name = "insertedRows={0}")
	@ValueSource(ints = {1, 0})
	@DisplayName("JWT 무효화는 신규 등록과 중복 등록 모두 성공하며 UTC 만료 시각을 저장한다")
	void revokeCurrentTokenRegardlessOfDuplicate(int insertedRows) {
		JwtPayload payload = new JwtPayload(10L, "MEMBER", "00000000-0000-0000-0000-000000000001",
			Instant.parse("2030-01-01T12:30:00Z"), 0L);
		LocalDateTime expiresAt = LocalDateTime.of(2030, 1, 1, 12, 30);
		when(revokedTokenMapper.insertRevokedToken(payload.tokenId(), expiresAt)).thenReturn(insertedRows);

		assertDoesNotThrow(() -> authService.logout(payload));

		verify(revokedTokenMapper).insertRevokedToken(payload.tokenId(), expiresAt);
		verifyNoInteractions(memberMapper, passwordEncoder, jwtTokenProvider);
	}

	@Test
	@DisplayName("무효화 기록 저장에 실패하면 로그아웃 실패를 전달한다")
	void propagateTokenRevocationFailure() {
		JwtPayload payload = new JwtPayload(10L, "MEMBER", "00000000-0000-0000-0000-000000000001",
			Instant.parse("2030-01-01T12:30:00Z"), 0L);
		LocalDateTime expiresAt = LocalDateTime.of(2030, 1, 1, 12, 30);
		DataAccessResourceFailureException failure = new DataAccessResourceFailureException("DB 연결 실패");

		when(revokedTokenMapper.insertRevokedToken(payload.tokenId(), expiresAt)).thenThrow(failure);

		assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> authService.logout(payload)));
	}

	@Test
	@DisplayName("현재 회원 정보를 DB에서 조회하여 반환한다")
	void getCurrentMemberFromDatabase() {
		when(memberMapper.selectActiveMemberById(1L)).thenReturn(Optional.of(adminMember()));

		CurrentMemberResponse response = authService.getCurrentMember(1L);

		assertEquals(new CurrentMemberResponse(1L, "관리자", "ADMIN"), response);
		verify(memberMapper).selectActiveMemberById(1L);
		verifyNoInteractions(passwordEncoder, jwtTokenProvider, revokedTokenMapper);
	}

	@Test
	@DisplayName("존재하지 않거나 삭제된 회원은 현재 회원 정보를 조회할 수 없다")
	void rejectUnavailableCurrentMember() {
		when(memberMapper.selectActiveMemberById(1L)).thenReturn(Optional.empty());

		assertThrows(MemberUnavailableException.class, () -> authService.getCurrentMember(1L));
	}

	private Member adminMember() {
		return Member.builder()
			.id(1L)
			.loginId("admin")
			.passwordHash("encoded-password")
			.authVersion(3L)
			.displayName("관리자")
			.role("ADMIN")
			.build();
	}
}