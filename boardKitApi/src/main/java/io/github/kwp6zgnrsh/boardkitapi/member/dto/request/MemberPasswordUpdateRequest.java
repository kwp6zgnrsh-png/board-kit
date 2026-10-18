package io.github.kwp6zgnrsh.boardkitapi.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberPasswordUpdateRequest(
	@NotBlank(message = "현재 비밀번호를 입력해 주세요.")
	String currentPassword,

	@NotBlank(message = "새 비밀번호를 입력해 주세요.")
	@Size(min = 12, max = 64, message = "새 비밀번호는 12자 이상 64자 이하이어야 합니다.")
	@Pattern(
		regexp = "^[!-~]+$",
		message = "새 비밀번호는 공백 없이 영문, 숫자, 특수문자만 사용할 수 있습니다."
	)
	String newPassword,

	@NotBlank(message = "새 비밀번호 확인을 입력해 주세요.")
	@Size(min = 12, max = 64, message = "새 비밀번호 확인은 12자 이상 64자 이하이어야 합니다.")
	@Pattern(
		regexp = "^[!-~]+$",
		message = "새 비밀번호 확인은 공백 없이 영문, 숫자, 특수문자만 사용할 수 있습니다."
	)
	String newPasswordConfirm
) {
}