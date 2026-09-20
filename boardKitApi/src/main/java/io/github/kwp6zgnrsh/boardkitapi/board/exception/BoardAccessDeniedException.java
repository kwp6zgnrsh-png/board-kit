package io.github.kwp6zgnrsh.boardkitapi.board.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class BoardAccessDeniedException extends RuntimeException {

	public BoardAccessDeniedException() {
		super("게시판을 조회할 권한이 없습니다.");
	}
}