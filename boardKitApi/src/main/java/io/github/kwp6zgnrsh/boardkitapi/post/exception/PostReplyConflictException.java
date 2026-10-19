package io.github.kwp6zgnrsh.boardkitapi.post.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class PostReplyConflictException extends RuntimeException {

	public PostReplyConflictException() {
		super("공식 답변이 변경되었습니다. 새로고침 후 다시 시도해 주세요.");
	}
}