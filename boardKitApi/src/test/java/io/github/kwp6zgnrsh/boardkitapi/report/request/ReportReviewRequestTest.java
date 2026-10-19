package io.github.kwp6zgnrsh.boardkitapi.report.request;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportReviewRequest;
import io.github.kwp6zgnrsh.boardkitapi.report.model.ReportStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class ReportReviewRequestTest {

	private static ValidatorFactory validatorFactory;
	private static Validator validator;

	@BeforeAll
	static void setUpValidator() {
		validatorFactory = Validation.buildDefaultValidatorFactory();
		validator = validatorFactory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		validatorFactory.close();
	}

	@ParameterizedTest
	@EnumSource(value = ReportStatus.class, names = {"ACCEPTED", "REJECTED"})
	@DisplayName("처리 메모가 있는 인정과 반려 요청을 허용한다")
	void allowFinalStatusWithReviewNote(ReportStatus status) {
		var request = new ReportReviewRequest(status, "신고 내용을 확인했습니다.");

		assertTrue(validator.validate(request).isEmpty());
	}

	@ParameterizedTest(name = "status={0}")
	@NullSource
	@EnumSource(value = ReportStatus.class, names = "PENDING")
	@DisplayName("처리 결과가 없거나 대기 상태이면 거절한다")
	void rejectNonFinalReviewStatus(ReportStatus status) {
		var request = new ReportReviewRequest(status, "처리 메모");

		assertFalse(validator.validate(request).isEmpty());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\n\t"})
	@DisplayName("처리 메모가 없거나 공백뿐이면 거절한다")
	void rejectBlankReviewNote(String reviewNote) {
		var request = new ReportReviewRequest(ReportStatus.ACCEPTED, reviewNote);

		assertFalse(validator.validate(request).isEmpty());
	}

	@ParameterizedTest(name = "status={0}, length={1}, valid={2}")
	@CsvSource({"ACCEPTED, 2000, true", "REJECTED, 2000, true", "ACCEPTED, 2001, false", "REJECTED, 2001, false"})
	@DisplayName("인정과 반려 처리 메모의 2,000자 경계를 검증한다")
	void validateReviewNoteLengthBoundary(ReportStatus status, int length, boolean valid) {
		var request = new ReportReviewRequest(status, "가".repeat(length));

		assertEquals(valid, validator.validate(request).isEmpty());
	}
}
