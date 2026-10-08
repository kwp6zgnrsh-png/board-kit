package io.github.kwp6zgnrsh.boardkitapi.file.model;

import org.springframework.web.multipart.MultipartFile;

public record ValidatedUploadFile(
	MultipartFile source,
	String originalName,
	FileFormat format,
	long sizeBytes,
	int sortOrder
) {

	public boolean isImage() {
		return format.isImage();
	}

	public String contentType() {
		return format.contentType();
	}

	public String storageExtension() {
		return format.storageExtension();
	}
}