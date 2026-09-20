package io.github.kwp6zgnrsh.boardkitapi.post.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class PostWriteDeniedException extends RuntimeException {

	public PostWriteDeniedException() {
		super("게시글 작성 권한이 없습니다.");
	}
}