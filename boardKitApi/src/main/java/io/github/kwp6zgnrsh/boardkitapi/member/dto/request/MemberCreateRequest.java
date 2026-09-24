package io.github.kwp6zgnrsh.boardkitapi.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberCreateRequest(

	@NotBlank
	@Pattern(regexp = "^[a-z0-9_]{4,20}$", message = "아이디는 영문 소문자, 숫자, 밑줄로 4자 이상 20자 이하이어야 합니다.")
	String loginId,

	@NotBlank
	@Size(min = 12, max = 64, message = "비밀번호는 12자 이상 64자 이하이어야 합니다.")
	@Pattern(regexp = "^[!-~]+$", message = "비밀번호는 공백 없이 영문, 숫자, 특수문자만 사용할 수 있습니다.")
	String password,

	@NotBlank
	@Size(min = 12, max = 64, message = "비밀번호 확인은 12자 이상 64자 이하이어야 합니다.")
	@Pattern(regexp = "^[!-~]+$", message = "비밀번호 확인은 공백 없이 영문, 숫자, 특수문자만 사용할 수 있습니다.")
	String passwordConfirm,

	@NotBlank
	String displayName

) {
}