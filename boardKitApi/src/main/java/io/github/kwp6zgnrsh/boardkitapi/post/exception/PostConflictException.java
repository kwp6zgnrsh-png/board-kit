package io.github.kwp6zgnrsh.boardkitapi.post.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class PostConflictException extends RuntimeException {

	public PostConflictException() {
		super("게시글이 변경되었습니다. 새로고침 후 다시 시도해주세요.");
	}
}