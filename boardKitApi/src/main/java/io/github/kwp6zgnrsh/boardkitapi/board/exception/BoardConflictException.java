package io.github.kwp6zgnrsh.boardkitapi.board.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class BoardConflictException extends RuntimeException {

	public BoardConflictException() {
		super("게시판 설정이 변경되었습니다. 새로고침 후 다시 시도해주세요.");
	}
}