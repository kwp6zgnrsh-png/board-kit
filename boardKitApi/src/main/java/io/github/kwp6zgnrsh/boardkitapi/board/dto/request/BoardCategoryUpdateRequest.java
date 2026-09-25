package io.github.kwp6zgnrsh.boardkitapi.board.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record BoardCategoryUpdateRequest(

	@Positive
	Long id,

	@NotBlank
	@Size(max = 50)
	String name,

	@NotNull
	@PositiveOrZero
	Integer sortOrder

) {
}