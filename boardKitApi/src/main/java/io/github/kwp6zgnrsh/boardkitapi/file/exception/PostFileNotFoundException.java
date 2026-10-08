package io.github.kwp6zgnrsh.boardkitapi.file.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class PostFileNotFoundException extends RuntimeException {
	public PostFileNotFoundException() {
		super("첨부파일을 찾을 수 없습니다.");
	}
}