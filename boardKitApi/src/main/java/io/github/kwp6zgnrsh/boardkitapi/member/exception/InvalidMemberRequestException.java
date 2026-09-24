package io.github.kwp6zgnrsh.boardkitapi.member.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidMemberRequestException extends RuntimeException {

	public InvalidMemberRequestException(String message) {
		super(message);
	}
}