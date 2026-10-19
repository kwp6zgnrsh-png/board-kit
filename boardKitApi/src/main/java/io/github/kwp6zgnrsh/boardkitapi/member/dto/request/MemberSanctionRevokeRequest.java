package io.github.kwp6zgnrsh.boardkitapi.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberSanctionRevokeRequest(

	@NotBlank(message = "제재 해제 사유를 입력해 주세요.")
	@Size(max = 2000, message = "제재 해제 사유는 2,000자 이하여야 합니다.")
	String revokeReason
) {
}