package io.github.kwp6zgnrsh.boardkitapi.post.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

public record PostReplyUpdateRequest(
	@NotNull(message = "공식 답변 본문을 입력해 주세요.")
	JsonNode contentDocument,

	@NotNull
	@PositiveOrZero
	Integer version
) {
}