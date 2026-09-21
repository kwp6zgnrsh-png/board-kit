package io.github.kwp6zgnrsh.boardkitapi.post.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class SecretPostAccessDeniedException
	extends RuntimeException {

	public SecretPostAccessDeniedException() {
		super("비밀글 비밀번호가 필요하거나 올바르지 않습니다.");
	}
}