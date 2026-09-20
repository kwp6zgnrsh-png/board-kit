package io.github.kwp6zgnrsh.boardkitapi.auth.service;

import io.github.kwp6zgnrsh.boardkitapi.auth.dto.request.LoginRequest;
import io.github.kwp6zgnrsh.boardkitapi.auth.dto.response.LoginResponse;
import io.github.kwp6zgnrsh.boardkitapi.auth.exception.InvalidCredentialsException;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtTokenProvider;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.member.model.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private MemberMapper memberMapper;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(
			memberMapper,
			passwordEncoder,
			jwtTokenProvider
		);
	}

	@Test
	@DisplayName("아이디와 비밀번호가 일치하면 JWT를 발급한다")
	void loginWithValidCredentials() {
		LoginRequest request = new LoginRequest("admin", "1234");
		Member member = adminMember();

		when(memberMapper.selectByLoginId("admin"))
			.thenReturn(Optional.of(member));
		when(passwordEncoder.matches("1234", "encoded-password"))
			.thenReturn(true);
		when(jwtTokenProvider.createAccessToken(1L, "ADMIN"))
			.thenReturn("access-token");

		LoginResponse response = authService.login(request);

		assertAll(
			() -> assertEquals("access-token", response.accessToken()),
			() -> assertEquals("Bearer", response.tokenType()),
			() -> assertEquals(1L, response.memberId()),
			() -> assertEquals("관리자", response.displayName()),
			() -> assertEquals("ADMIN", response.role())
		);

		verify(memberMapper).selectByLoginId("admin");
		verify(passwordEncoder).matches("1234", "encoded-password");
		verify(jwtTokenProvider).createAccessToken(1L, "ADMIN");
	}

	@Test
	@DisplayName("존재하지 않는 아이디로 로그인할 수 없다")
	void rejectLoginWhenMemberDoesNotExist() {
		LoginRequest request = new LoginRequest("unknown", "1234");

		when(memberMapper.selectByLoginId("unknown"))
			.thenReturn(Optional.empty());

		assertThrows(
			InvalidCredentialsException.class,
			() -> authService.login(request)
		);

		verifyNoInteractions(passwordEncoder, jwtTokenProvider);
	}

	@Test
	@DisplayName("비밀번호가 일치하지 않으면 로그인할 수 없다")
	void rejectLoginWhenPasswordDoesNotMatch() {
		LoginRequest request = new LoginRequest("admin", "wrong-password");
		Member member = adminMember();

		when(memberMapper.selectByLoginId("admin"))
			.thenReturn(Optional.of(member));
		when(passwordEncoder.matches("wrong-password", "encoded-password"))
			.thenReturn(false);

		assertThrows(
			InvalidCredentialsException.class,
			() -> authService.login(request)
		);

		verifyNoInteractions(jwtTokenProvider);
	}

	private Member adminMember() {
		return Member.builder()
			.id(1L)
			.loginId("admin")
			.passwordHash("encoded-password")
			.displayName("관리자")
			.role("ADMIN")
			.build();
	}
}