package io.github.kwp6zgnrsh.boardkitapi.file.model;

import java.util.Locale;
import java.util.Set;

public enum FileFormat {

	JPEG("image/jpeg", "jpg", true, Set.of("jpg", "jpeg")), PNG("image/png", "png", true, Set.of("png")), GIF(
		"image/gif", "gif", true, Set.of("gif")), ZIP("application/zip", "zip", false, Set.of("zip")), TXT(
		"text/plain;charset=UTF-8", "txt", false, Set.of("txt")), PDF("application/pdf", "pdf", false, Set.of("pdf"));

	private final String contentType;
	private final String storageExtension;
	private final boolean image;
	private final Set<String> allowedExtensions;

	FileFormat(String contentType, String storageExtension, boolean image, Set<String> allowedExtensions) {
		this.contentType = contentType;
		this.storageExtension = storageExtension;
		this.image = image;
		this.allowedExtensions = allowedExtensions;
	}

	public String contentType() {
		return contentType;
	}

	public String storageExtension() {
		return storageExtension;
	}

	public boolean isImage() {
		return image;
	}

	public boolean supportsExtension(String extension) {
		if (extension == null) {
			return false;
		}

		return allowedExtensions.contains(extension.toLowerCase(Locale.ROOT));
	}
}