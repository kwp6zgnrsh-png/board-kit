package io.github.kwp6zgnrsh.boardkitapi.file.model;

import java.util.Set;

public enum FileUploadProfile {

	IMAGE(Set.of(FileFormat.JPEG, FileFormat.PNG, FileFormat.GIF)), IMAGE_AND_ZIP(
		Set.of(FileFormat.JPEG, FileFormat.PNG, FileFormat.GIF, FileFormat.ZIP)), IMAGE_AND_FILE(
		Set.of(FileFormat.JPEG, FileFormat.PNG, FileFormat.GIF, FileFormat.ZIP, FileFormat.TXT, FileFormat.PDF));

	private final Set<FileFormat> allowedFormats;

	FileUploadProfile(Set<FileFormat> allowedFormats) {
		this.allowedFormats = allowedFormats;
	}

	public boolean allows(FileFormat format) {
		return allowedFormats.contains(format);
	}

	public static FileUploadProfile from(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("파일 업로드 형식 묶음이 없습니다.");
		}

		try {
			return valueOf(value);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("지원하지 않는 파일 업로드 형식 묶음입니다: " + value, exception);
		}
	}
}