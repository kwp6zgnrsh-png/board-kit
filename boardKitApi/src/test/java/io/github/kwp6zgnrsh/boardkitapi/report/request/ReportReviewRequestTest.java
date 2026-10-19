package io.github.kwp6zgnrsh.boardkitapi.report.request;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
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

	@Test
	@DisplayName("처리 결과로 대기 상태를 요청할 수 없다")
	void rejectPendingReviewStatus() {
		var request = new ReportReviewRequest(ReportStatus.PENDING, "처리 메모");

		assertFalse(validator.validate(request).isEmpty());
	}

	@Test
	@DisplayName("처리 결과를 선택하지 않으면 거절한다")
	void rejectMissingReviewStatus() {
		var request = new ReportReviewRequest(null, "처리 메모");

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

	@ParameterizedTest
	@EnumSource(value = ReportStatus.class, names = {"ACCEPTED", "REJECTED"})
	@DisplayName("인정과 반려의 처리 메모는 2,000자까지 허용한다")
	void allowReviewNoteAtMaximumLength(ReportStatus status) {
		var request = new ReportReviewRequest(status, "가".repeat(2000));

		assertTrue(validator.validate(request).isEmpty());
	}

	@ParameterizedTest
	@EnumSource(value = ReportStatus.class, names = {"ACCEPTED", "REJECTED"})
	@DisplayName("인정과 반려의 처리 메모가 2,000자를 초과하면 거절한다")
	void rejectReviewNoteExceedingMaximumLength(ReportStatus status) {
		var request = new ReportReviewRequest(status, "가".repeat(2001));

		assertFalse(validator.validate(request).isEmpty());
	}
}