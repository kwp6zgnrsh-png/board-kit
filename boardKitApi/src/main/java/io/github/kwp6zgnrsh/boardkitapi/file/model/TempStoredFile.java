package io.github.kwp6zgnrsh.boardkitapi.file.model;

public record TempStoredFile(
	String tempKey,
	String originalName,
	FileFormat format,
	long sizeBytes,
	int sortOrder
) {
}