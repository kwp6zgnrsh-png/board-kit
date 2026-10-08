package io.github.kwp6zgnrsh.boardkitapi.file.model;

public record StoredPostFile(
	String storageKey,
	String originalName,
	FileFormat format,
	long sizeBytes,
	int sortOrder,
	String thumbnailKey
) {
	public String contentType() {
		return format.contentType();
	}

	public boolean isImage() {
		return format.isImage();
	}
}