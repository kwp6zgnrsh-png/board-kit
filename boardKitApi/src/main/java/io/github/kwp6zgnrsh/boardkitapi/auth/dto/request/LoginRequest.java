package io.github.kwp6zgnrsh.boardkitapi.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

	@NotBlank
	@Size(max = 50)
	String loginId,

	@NotBlank
	String password

) {
}