package io.github.kwp6zgnrsh.boardkitapi.comment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CommentUpdateRequest(
	@NotBlank
	@Size(max = 2000)
	String content,

	@NotNull
	@PositiveOrZero
	Integer version
) {}