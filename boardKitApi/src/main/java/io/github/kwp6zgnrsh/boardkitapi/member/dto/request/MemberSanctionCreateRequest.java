package io.github.kwp6zgnrsh.boardkitapi.member.dto.request;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MemberSanctionCreateRequest(
	@NotNull(message = "제재할 회원을 선택해 주세요.")
	@Positive(message = "회원 정보가 올바르지 않습니다.")
	Long memberId,

	@NotBlank(message = "제재 사유를 입력해 주세요.")
	@Size(max = 2000, message = "제재 사유는 2,000자 이하여야 합니다.")
	String reason,

	@Future(message = "제재 종료 일시는 현재보다 미래여야 합니다.")
	OffsetDateTime endsAt
) {
}