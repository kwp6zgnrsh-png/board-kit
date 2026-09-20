package io.github.kwp6zgnrsh.boardkitapi.post.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PostCreateRequest(

	@NotBlank
	@Size(max = 200)
	String title,

	@NotBlank
	String content,

	@Positive
	Long categoryId,

	@NotNull
	Boolean isSecret,

	@Size(max = 100)
	String secretPassword,

	@NotNull
	Boolean commentsLocked

) {
}