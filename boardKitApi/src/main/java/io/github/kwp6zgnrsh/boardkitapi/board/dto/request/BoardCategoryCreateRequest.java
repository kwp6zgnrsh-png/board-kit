package io.github.kwp6zgnrsh.boardkitapi.board.dto.request;

import jakarta.validation.constraints.*;

public record BoardCategoryCreateRequest(

	@NotBlank @Size(max = 50) String name,

	@NotNull @PositiveOrZero Integer sortOrder

) {
}