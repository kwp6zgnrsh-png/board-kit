package io.github.kwp6zgnrsh.boardkitapi.board.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record BoardUpdateRequest(

	@NotBlank
	@Size(max = 100)
	String name,

	@Size(max = 2000)
	String description,

	@NotBlank
	@jakarta.validation.constraints.Pattern(regexp = "LIST|CARD")
	String displayType,

	@NotNull
	@PositiveOrZero
	Integer sortOrder,

	@NotNull
	@Valid
	BoardPolicyRequest policy,

	@NotNull
	List<@NotNull @Valid BoardCategoryUpdateRequest> categories,

	@NotNull
	@PositiveOrZero
	Integer version

) {
}