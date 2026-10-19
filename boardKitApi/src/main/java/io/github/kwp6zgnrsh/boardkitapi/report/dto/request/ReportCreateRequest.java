package io.github.kwp6zgnrsh.boardkitapi.report.dto.request;

import io.github.kwp6zgnrsh.boardkitapi.report.model.ReportReason;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportCreateRequest(
	@NotNull(message = "신고 사유를 선택해 주세요.")
	ReportReason reason,

	@Size(max = 2000, message = "상세 사유는 2,000자 이하여야 합니다.")
	String detail
) {

	@AssertTrue(message = "기타 사유를 선택하면 상세 사유를 입력해 주세요.")
	public boolean isOtherDetailProvided() {
		return reason != ReportReason.OTHER
			|| (detail != null && !detail.isBlank());
	}
}