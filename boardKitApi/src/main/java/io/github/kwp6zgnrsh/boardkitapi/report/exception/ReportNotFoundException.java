package io.github.kwp6zgnrsh.boardkitapi.report.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ReportNotFoundException extends RuntimeException {

	public ReportNotFoundException() {
		super("신고를 찾을 수 없습니다.");
	}
}