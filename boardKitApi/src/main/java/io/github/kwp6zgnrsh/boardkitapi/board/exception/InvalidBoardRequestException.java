package io.github.kwp6zgnrsh.boardkitapi.board.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidBoardRequestException extends RuntimeException {

	public InvalidBoardRequestException(String message) {
		super(message);
	}
}