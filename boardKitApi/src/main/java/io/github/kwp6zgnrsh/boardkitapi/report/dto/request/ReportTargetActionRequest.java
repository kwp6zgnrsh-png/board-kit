package io.github.kwp6zgnrsh.boardkitapi.report.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ReportTargetActionRequest(
	@NotNull(message = "관리 조치를 선택해 주세요.")
	ReportTargetAction action,

	@NotNull(message = "대상 버전이 필요합니다.")
	@PositiveOrZero(message = "대상 버전이 올바르지 않습니다.")
	Integer version
) {
}