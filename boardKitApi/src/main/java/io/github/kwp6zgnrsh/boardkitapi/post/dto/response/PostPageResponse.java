package io.github.kwp6zgnrsh.boardkitapi.post.dto.response;

import java.util.List;

public record PostPageResponse(
	List<PostSummaryResponse> posts,
	int page,
	int size,
	boolean hasNext,
	int availableThroughPage
) {
}