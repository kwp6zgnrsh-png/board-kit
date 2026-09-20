package io.github.kwp6zgnrsh.boardkitapi.board.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record BoardCreateRequest(

	@NotBlank
	@Size(max = 100)
	String name,

	@NotBlank
	@Size(max = 100)
	@Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
	String slug,

	@Size(max = 2000)
	String description,

	@NotBlank
	@Pattern(regexp = "LIST|CARD")
	String displayType,

	@NotNull
	@PositiveOrZero
	Integer sortOrder,

	@NotNull
	@Valid
	BoardPolicyCreateRequest policy,

	@NotNull
	List<@NotNull @Valid BoardCategoryCreateRequest> categories

) {
}