package io.github.kwp6zgnrsh.boardkitapi.post.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PostListRequest(
	@Size(max = 100)
	String keyword,

	@Positive
	Long categoryId,

	PostSort sort
) {

	public PostListRequest {
		keyword = normalizeKeyword(keyword);
		sort = sort == null ? PostSort.LATEST : sort;
	}

	private static String normalizeKeyword(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}

		return keyword.trim();
	}
}