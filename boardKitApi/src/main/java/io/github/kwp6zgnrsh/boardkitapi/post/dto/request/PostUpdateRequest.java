package io.github.kwp6zgnrsh.boardkitapi.post.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record PostUpdateRequest(
	@NotBlank @Size(max = 200)
	String title,

	@NotNull
	JsonNode contentDocument,

	@Positive
	Long categoryId,

	@NotNull
	Boolean isSecret,

	@Size(max = 100)
	String secretPassword,

	@NotNull
	Boolean clearSecretPassword,

	@NotNull
	Boolean commentsLocked,

	@NotNull @PositiveOrZero
	Integer version,

	@NotNull
	List<@NotNull @Positive Long> retainedFileIds
) {
}