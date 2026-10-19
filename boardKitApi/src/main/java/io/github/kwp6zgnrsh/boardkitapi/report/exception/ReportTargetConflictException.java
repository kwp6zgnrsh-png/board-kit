package io.github.kwp6zgnrsh.boardkitapi.report.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ReportTargetConflictException extends RuntimeException {

	public ReportTargetConflictException() {
		super("대상 상태가 변경되었습니다. 새로고침 후 다시 확인해 주세요.");
	}
}