package io.github.kwp6zgnrsh.boardkitapi.post.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class PostDeleteDeniedException extends RuntimeException {

	public PostDeleteDeniedException() {
		super("게시글을 삭제할 권한이 없습니다.");
	}
}