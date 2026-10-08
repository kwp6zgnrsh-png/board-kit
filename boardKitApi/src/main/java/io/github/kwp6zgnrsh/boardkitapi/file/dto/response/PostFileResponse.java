package io.github.kwp6zgnrsh.boardkitapi.file.dto.response;

public record PostFileResponse(
	Long id,
	String originalName,
	String contentType,
	long sizeBytes,
	int sortOrder,
	boolean thumbnailAvailable,
	String fileUsage
) {
}