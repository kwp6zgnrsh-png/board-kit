package io.github.kwp6zgnrsh.boardkitapi.post.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class PostEditDeniedException extends RuntimeException {

	public PostEditDeniedException() {
		super("게시글을 수정할 권한이 없습니다.");
	}
}