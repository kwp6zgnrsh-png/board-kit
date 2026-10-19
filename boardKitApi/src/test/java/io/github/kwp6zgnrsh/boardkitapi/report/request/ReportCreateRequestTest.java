package io.github.kwp6zgnrsh.boardkitapi.report.request;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.report.model.ReportReason;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class ReportCreateRequestTest {

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
	@EnumSource(value = ReportReason.class, names = {"SPAM", "ABUSE"})
	@DisplayName("스팸과 욕설 신고는 상세 사유 없이 등록할 수 있다")
	void allowOptionalDetailForPredefinedReasons(ReportReason reason) {
		var request = new ReportCreateRequest(reason, null);

		assertTrue(validator.validate(request).isEmpty());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\n\t"})
	@DisplayName("기타 신고는 상세 사유가 없거나 공백뿐이면 거절한다")
	void rejectOtherReasonWithoutDetail(String detail) {
		var request = new ReportCreateRequest(ReportReason.OTHER, detail);

		assertFalse(validator.validate(request).isEmpty());
	}

	@ParameterizedTest(name = "length={0}, valid={1}")
	@CsvSource({"2000, true", "2001, false"})
	@DisplayName("신고 상세 사유의 2,000자 경계를 검증한다")
	void validateDetailLengthBoundary(int length, boolean valid) {
		var request = new ReportCreateRequest(ReportReason.OTHER, "가".repeat(length));

		assertEquals(valid, validator.validate(request).isEmpty());
	}

	@Test
	@DisplayName("신고 사유를 선택하지 않으면 거절한다")
	void rejectMissingReason() {
		var request = new ReportCreateRequest(null, "신고 내용");

		assertFalse(validator.validate(request).isEmpty());
	}
}