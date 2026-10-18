package io.github.kwp6zgnrsh.boardkitapi.post.policy;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import io.github.kwp6zgnrsh.boardkitapi.common.security.PasswordLimits;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.SecretPostAccessDeniedException;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PostSecretAccess {

	private final PasswordEncoder passwordEncoder;

	public void validate(boolean secret, String passwordHash, Long authorId, Long memberId, boolean adminAccess,
		String password) {
		if (!secret || passwordHash == null || adminAccess || (memberId != null && Objects.equals(authorId,
			memberId))) {
			return;
		}

		if (password == null || password.isBlank()
			|| password.getBytes(StandardCharsets.UTF_8).length > PasswordLimits.BCRYPT_MAX_PASSWORD_BYTES
			|| !passwordEncoder.matches(password, passwordHash)) {

			throw new SecretPostAccessDeniedException();
		}
	}
}