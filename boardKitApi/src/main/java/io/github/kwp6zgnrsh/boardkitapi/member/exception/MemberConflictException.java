package io.github.kwp6zgnrsh.boardkitapi.member.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class MemberConflictException extends RuntimeException {

	public MemberConflictException() {
		super("회원 정보가 변경되었습니다. 다시 로그인한 후 시도해 주세요.");
	}
}