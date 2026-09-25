package io.github.kwp6zgnrsh.boardkitapi.member.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class MemberSanctionedException extends RuntimeException {

	public MemberSanctionedException() {
		super("제재 중인 회원은 게시글이나 댓글을 작성할 수 없습니다.");
	}
}