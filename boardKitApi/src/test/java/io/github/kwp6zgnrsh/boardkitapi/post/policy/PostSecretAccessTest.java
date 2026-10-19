package io.github.kwp6zgnrsh.boardkitapi.post.policy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.kwp6zgnrsh.boardkitapi.post.exception.SecretPostAccessDeniedException;

@ExtendWith(MockitoExtension.class)
class PostSecretAccessTest {

	@Mock
	private PasswordEncoder passwordEncoder;

	private PostSecretAccess postSecretAccess;

	@BeforeEach
	void setUp() {
		postSecretAccess = new PostSecretAccess(passwordEncoder);
	}

	@ParameterizedTest(name = "secret={0}")
	@ValueSource(booleans = {false, true})
	@DisplayName("일반 글과 비밀번호 없는 비밀글은 암호 비교 없이 접근할 수 있다")
	void allowPostWithoutPasswordHash(boolean secret) {
		assertDoesNotThrow(() -> postSecretAccess.validate(secret, null, 10L, null, false, null));

		verifyNoInteractions(passwordEncoder);
	}

	@ParameterizedTest
	@CsvSource({"10, false", "20, true"})
	@DisplayName("게시글 작성자와 관리자는 비밀글 비밀번호 없이 접근할 수 있다")
	void allowAuthorAndAdminWithoutPassword(Long authorId, boolean adminAccess) {
		assertDoesNotThrow(() -> postSecretAccess.validate(true, "hash", authorId, 10L, adminAccess, null));

		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("일치하는 비밀번호는 앞뒤 공백을 포함한 원문으로 비교한다")
	void comparePasswordWithoutTrimming() {
		String password = "  correct  ";
		when(passwordEncoder.matches(password, "hash")).thenReturn(true);

		assertDoesNotThrow(() -> postSecretAccess.validate(true, "hash", 10L, 20L, false, password));

		verify(passwordEncoder).matches(password, "hash");
	}

	@Test
	@DisplayName("틀린 비밀번호는 비밀글 접근을 거절한다")
	void rejectIncorrectPassword() {
		when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

		assertThrows(SecretPostAccessDeniedException.class,
			() -> postSecretAccess.validate(true, "hash", 10L, 20L, false, "wrong"));
	}

	@ParameterizedTest
	@NullSource
	@EmptySource
	@ValueSource(strings = {" ", "\t"})
	@DisplayName("비밀번호가 없거나 공백뿐이면 암호 비교 전에 거절한다")
	void rejectMissingOrBlankPassword(String password) {
		assertThrows(SecretPostAccessDeniedException.class,
			() -> postSecretAccess.validate(true, "hash", 10L, 20L, false, password));

		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("비로그인 사용자는 비밀번호 없이 보호된 비밀글에 접근할 수 없다")
	void rejectAnonymousAccessWithoutPassword() {
		assertThrows(SecretPostAccessDeniedException.class,
			() -> postSecretAccess.validate(true, "hash", 10L, null, false, null));

		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("UTF-8 기준 72바이트 비밀번호는 암호 비교를 수행한다")
	void acceptPasswordAtByteLimit() {
		String password = "가".repeat(24);
		when(passwordEncoder.matches(password, "hash")).thenReturn(true);

		assertDoesNotThrow(() -> postSecretAccess.validate(true, "hash", 10L, 20L, false, password));

		verify(passwordEncoder).matches(password, "hash");
	}

	@Test
	@DisplayName("UTF-8 기준 72바이트 초과 비밀번호는 암호 비교 전에 거절한다")
	void rejectPasswordExceedingByteLimit() {
		String password = "가".repeat(25);

		assertThrows(SecretPostAccessDeniedException.class,
			() -> postSecretAccess.validate(true, "hash", 10L, 20L, false, password));

		verifyNoInteractions(passwordEncoder);
	}
}