package io.github.kwp6zgnrsh.boardkitapi.report.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateReportException extends RuntimeException {

	public DuplicateReportException() {
		super("이미 신고한 대상입니다.");
	}
}