package io.github.kwp6zgnrsh.boardkitapi.member.dto.request;

import jakarta.validation.constraints.NotBlank;

public record MemberDisplayNameUpdateRequest(
	@NotBlank(message = "닉네임을 입력해 주세요.")
	String displayName
) {}