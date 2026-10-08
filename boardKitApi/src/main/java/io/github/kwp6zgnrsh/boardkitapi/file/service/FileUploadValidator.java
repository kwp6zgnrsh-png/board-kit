package io.github.kwp6zgnrsh.boardkitapi.file.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardPolicy;
import io.github.kwp6zgnrsh.boardkitapi.file.config.FileStorageProperties;
import io.github.kwp6zgnrsh.boardkitapi.file.exception.InvalidFileRequestException;
import io.github.kwp6zgnrsh.boardkitapi.file.model.FileFormat;
import io.github.kwp6zgnrsh.boardkitapi.file.model.FileUploadProfile;
import io.github.kwp6zgnrsh.boardkitapi.file.model.ValidatedUploadFile;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FileUploadValidator {

	private static final int MAX_ORIGINAL_NAME_LENGTH = 255;

	private final FileStorageProperties properties;
	private final FileSignatureDetector signatureDetector;

	public List<ValidatedUploadFile> validate(List<MultipartFile> files, BoardPolicy policy) {
		return validateWithRetainedFiles(files, policy, 0, 0L);
	}

	public List<ValidatedUploadFile> validateWithRetainedFiles(List<MultipartFile> files, BoardPolicy policy,
		int retainedFileCount, long retainedTotalSize) {

		if (retainedFileCount < 0 || retainedTotalSize < 0) {
			throw new IllegalArgumentException("유지할 파일 정보가 올바르지 않습니다.");
		}

		List<MultipartFile> requestedFiles = files == null ? List.of() : List.copyOf(files);

		if (!policy.isAttachmentsEnabled()) {
			if (!requestedFiles.isEmpty()) {
				throw new InvalidFileRequestException("첨부파일을 사용할 수 없는 게시판입니다.");
			}

			return List.of();
		}

		validatePolicyLimits(policy);

		if (!requestedFiles.isEmpty() && retainedFileCount > policy.getMaxFileCount() - requestedFiles.size()) {
			throw new InvalidFileRequestException("첨부파일은 최대 " + policy.getMaxFileCount() + "개까지 등록할 수 있습니다.");
		}

		FileUploadProfile profile = resolveUploadProfile(policy.getUploadProfile());

		List<ValidatedUploadFile> validatedFiles = new ArrayList<>(requestedFiles.size());

		long totalSize = retainedTotalSize;

		for (int index = 0; index < requestedFiles.size(); index++) {
			MultipartFile file = requestedFiles.get(index);

			if (file == null || file.isEmpty()) {
				throw new InvalidFileRequestException("빈 파일은 업로드할 수 없습니다.");
			}

			long size = file.getSize();

			if (size > policy.getMaxFileSizeBytes()) {
				throw new InvalidFileRequestException("파일 하나의 최대 용량을 초과했습니다.");
			}

			String originalName = normalizeOriginalName(file.getOriginalFilename());

			String extension = extractExtension(originalName);
			FileFormat format = signatureDetector.detect(file, extension);

			if (!format.supportsExtension(extension)) {
				throw new InvalidFileRequestException("파일의 확장자와 실제 형식이 일치하지 않습니다: " + originalName);
			}

			if (!profile.allows(format)) {
				throw new InvalidFileRequestException("이 게시판에서 허용하지 않는 파일 형식입니다: " + originalName);
			}

			if (format.isImage()) {
				validateReadableImage(file, originalName);
			}

			totalSize = addSize(totalSize, size);

			if (totalSize > properties.maxPostSize().toBytes()) {
				throw new InvalidFileRequestException(
					"게시글 하나의 전체 첨부파일 용량은 " + properties.maxPostSize().toMegabytes() + "MB를 초과할 수 없습니다.");
			}

			validatedFiles.add(new ValidatedUploadFile(file, originalName, format, size, index));
		}

		return List.copyOf(validatedFiles);
	}

	private void validatePolicyLimits(BoardPolicy policy) {
		if (policy.getMaxFileCount() == null || policy.getMaxFileCount() <= 0
			|| policy.getMaxFileCount() > properties.maxFileCount()) {
			throw new IllegalStateException("게시판의 최대 파일 개수 정책이 서버 제한을 벗어났습니다.");
		}

		if (policy.getMaxFileSizeBytes() == null || policy.getMaxFileSizeBytes() <= 0
			|| policy.getMaxFileSizeBytes() > properties.maxFileSize().toBytes()) {
			throw new IllegalStateException("게시판의 파일당 용량 정책이 서버 제한을 벗어났습니다.");
		}
	}

	private FileUploadProfile resolveUploadProfile(String value) {
		try {
			return FileUploadProfile.from(value);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("게시판의 파일 형식 정책이 올바르지 않습니다.", exception);
		}
	}

	private String normalizeOriginalName(String originalName) {
		if (originalName == null || originalName.isBlank()) {
			throw new InvalidFileRequestException("파일 이름이 없습니다.");
		}

		String normalized = Normalizer.normalize(originalName.replace('\\', '/'), Normalizer.Form.NFC);

		int lastSeparator = normalized.lastIndexOf('/');

		String fileName = normalized.substring(lastSeparator + 1).trim();

		if (fileName.isBlank() || fileName.equals(".") || fileName.equals("..")) {
			throw new InvalidFileRequestException("파일 이름이 올바르지 않습니다.");
		}

		if (fileName.codePointCount(0, fileName.length()) > MAX_ORIGINAL_NAME_LENGTH) {
			throw new InvalidFileRequestException("파일 이름은 255자 이하여야 합니다.");
		}

		if (fileName.chars().anyMatch(Character::isISOControl)) {
			throw new InvalidFileRequestException("파일 이름에 제어 문자를 사용할 수 없습니다.");
		}

		return fileName;
	}

	private String extractExtension(String fileName) {
		int dotIndex = fileName.lastIndexOf('.');

		if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
			throw new InvalidFileRequestException("파일 확장자가 필요합니다: " + fileName);
		}

		return fileName.substring(dotIndex + 1);
	}

	private void validateReadableImage(MultipartFile file, String originalName) {
		try {
			BufferedImage image = ImageIO.read(file.getInputStream());

			if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
				throw new InvalidFileRequestException("읽을 수 없는 이미지입니다: " + originalName);
			}
		} catch (IOException exception) {
			throw new InvalidFileRequestException("이미지를 읽을 수 없습니다: " + originalName, exception);
		}
	}

	private long addSize(long current, long size) {
		try {
			return Math.addExact(current, size);
		} catch (ArithmeticException exception) {
			throw new InvalidFileRequestException("전체 파일 용량이 너무 큽니다.", exception);
		}
	}
}