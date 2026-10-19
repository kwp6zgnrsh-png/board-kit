package io.github.kwp6zgnrsh.boardkitapi.report.dto.request;

import io.github.kwp6zgnrsh.boardkitapi.report.model.ReportStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportReviewRequest(
	@NotNull(message = "처리 결과를 선택해 주세요.")
	ReportStatus status,

	@NotBlank(message = "처리 메모를 입력해 주세요.")
	@Size(max = 2000, message = "처리 메모는 2,000자 이하여야 합니다.")
	String reviewNote
) {

	@AssertTrue(message = "처리 결과는 인정 또는 반려만 선택할 수 있습니다.")
	public boolean isFinalStatus() {
		return status == null
			|| status == ReportStatus.ACCEPTED
			|| status == ReportStatus.REJECTED;
	}
}