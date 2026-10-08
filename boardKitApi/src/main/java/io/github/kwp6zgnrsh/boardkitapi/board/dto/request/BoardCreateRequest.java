package io.github.kwp6zgnrsh.boardkitapi.board.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record BoardCreateRequest(

	@NotBlank @Size(max = 100) String name,

	@NotBlank @Size(max = 100) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,

	@Size(max = 2000) String description,

	@NotBlank @Pattern(regexp = "LIST|CARD") String displayType,

	@NotNull @PositiveOrZero Integer sortOrder,

	@NotNull @Valid BoardPolicyRequest policy,

	@NotNull List<@NotNull @Valid BoardCategoryCreateRequest> categories

) {
}