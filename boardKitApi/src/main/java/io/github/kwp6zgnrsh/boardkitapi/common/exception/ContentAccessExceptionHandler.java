package io.github.kwp6zgnrsh.boardkitapi.common.exception;

import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.kwp6zgnrsh.boardkitapi.post.exception.SecretPostAccessDeniedException;

@RestControllerAdvice
public class ContentAccessExceptionHandler {

	@ExceptionHandler(BlindedContentAccessDeniedException.class)
	public ResponseEntity<Map<String, String>> handleBlindedContent(BlindedContentAccessDeniedException exception) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.cacheControl(CacheControl.noStore())
			.body(Map.of("code", "CONTENT_BLINDED", "message", exception.getMessage()));
	}

	@ExceptionHandler(SecretPostAccessDeniedException.class)
	public ResponseEntity<Map<String, String>> handleSecretPost(SecretPostAccessDeniedException exception) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.cacheControl(CacheControl.noStore())
			.body(Map.of("code", "SECRET_POST_ACCESS_DENIED", "message", exception.getMessage()));
	}
}