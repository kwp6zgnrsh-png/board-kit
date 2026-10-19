package io.github.kwp6zgnrsh.boardkitapi.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class BlindedContentAccessDeniedException extends RuntimeException {

	public BlindedContentAccessDeniedException(String message) {
		super(message);
	}
}