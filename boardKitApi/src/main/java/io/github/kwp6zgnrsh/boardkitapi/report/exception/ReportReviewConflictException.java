package io.github.kwp6zgnrsh.boardkitapi.report.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ReportReviewConflictException extends RuntimeException {

	public ReportReviewConflictException() {
		super("이미 처리된 신고입니다. 새로고침 후 확인해 주세요.");
	}
}