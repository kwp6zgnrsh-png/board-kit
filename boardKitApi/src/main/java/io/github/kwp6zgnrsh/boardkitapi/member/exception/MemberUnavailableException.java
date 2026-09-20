package io.github.kwp6zgnrsh.boardkitapi.member.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class MemberUnavailableException extends RuntimeException {

	public MemberUnavailableException() {
		super("유효한 회원이 아닙니다.");
	}
}