package io.github.kwp6zgnrsh.boardkitapi.comment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class CommentDeleteDeniedException extends RuntimeException {

	public CommentDeleteDeniedException() {
		super("댓글을 삭제할 권한이 없습니다.");
	}
}