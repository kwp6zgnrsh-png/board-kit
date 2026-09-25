package io.github.kwp6zgnrsh.boardkitapi.comment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CommentConflictException extends RuntimeException {

	public CommentConflictException() {
		super("댓글이 변경되었습니다. 새로고침 후 다시 시도해주세요.");
	}
}