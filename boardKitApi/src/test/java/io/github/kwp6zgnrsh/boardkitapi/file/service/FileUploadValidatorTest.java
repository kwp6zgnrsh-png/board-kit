package io.github.kwp6zgnrsh.boardkitapi.file.service;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardPolicy;
import io.github.kwp6zgnrsh.boardkitapi.file.config.FileStorageProperties;
import io.github.kwp6zgnrsh.boardkitapi.file.exception.InvalidFileRequestException;
import io.github.kwp6zgnrsh.boardkitapi.file.model.FileFormat;
import io.github.kwp6zgnrsh.boardkitapi.file.model.ValidatedUploadFile;

class FileUploadValidatorTest {

	@Test
	@DisplayName("실제 형식과 확장자가 일치하는 이미지를 허용한다")
	void acceptValidImage() throws Exception {
		FileUploadValidator validator = validator(DataSize.ofMegabytes(20), DataSize.ofMegabytes(50));
		MockMultipartFile file = new MockMultipartFile("files", "image.png", "image/png", createPng());

		List<ValidatedUploadFile> result = validator.validate(List.of(file), policy("IMAGE", 3, 1024 * 1024, false));

		assertEquals(1, result.size());
		assertEquals(FileFormat.PNG, result.getFirst().format());
		assertEquals("image.png", result.getFirst().originalName());
		assertEquals(0, result.getFirst().sortOrder());
	}

	@Test
	@DisplayName("확장자와 실제 파일 형식이 다르면 거절한다")
	void rejectMismatchedExtension() throws Exception {
		FileUploadValidator validator = validator(DataSize.ofMegabytes(20), DataSize.ofMegabytes(50));
		MockMultipartFile file = new MockMultipartFile("files", "image.jpg", "image/jpeg", createPng());

		assertThrows(
			InvalidFileRequestException.class,
			() -> validator.validate(List.of(file), policy("IMAGE", 3, 1024 * 1024, false))
		);
	}

	@Test
	@DisplayName("첨부파일을 사용하지 않는 게시판의 파일 요청을 거절한다")
	void rejectFileWhenAttachmentsAreDisabled() {
		FileUploadValidator validator = validator(DataSize.ofMegabytes(20), DataSize.ofMegabytes(50));
		MockMultipartFile file = zipFile("archive.zip");

		assertThrows(
			InvalidFileRequestException.class,
			() -> validator.validate(List.of(file), BoardPolicy.builder().attachmentsEnabled(false).build())
		);
	}

	@Test
	@DisplayName("게시글 전체 첨부파일 용량 제한을 초과하면 거절한다")
	void rejectFilesExceedingPostSizeLimit() {
		FileUploadValidator validator = validator(DataSize.ofBytes(10), DataSize.ofBytes(12));
		BoardPolicy policy = policy("IMAGE_AND_ZIP", 3, 10, false);

		assertThrows(
			InvalidFileRequestException.class,
			() -> validator.validate(List.of(zipFile("first.zip"), zipFile("second.zip")), policy)
		);
	}

	@Test
	@DisplayName("파일 검증은 본문 이미지 필수 조건을 판단하지 않는다")
	void validateFilesWithoutCheckingRequiredBodyImage() {
		FileUploadValidator validator = validator(DataSize.ofMegabytes(20), DataSize.ofMegabytes(50));
		BoardPolicy policy = policy("IMAGE_AND_ZIP", 3, 1024 * 1024, true);

		List<ValidatedUploadFile> result = validator.validate(List.of(zipFile("archive.zip")), policy);

		assertEquals(1, result.size());
		assertEquals(FileFormat.ZIP, result.getFirst().format());
	}

	@Test
	@DisplayName("유지한 파일과 새 파일의 합계가 최대 개수를 넘으면 거절한다")
	void rejectUpdateExceedingCombinedFileCount() {
		FileUploadValidator validator = validator(DataSize.ofBytes(10), DataSize.ofBytes(20));
		BoardPolicy policy = policy("IMAGE_AND_ZIP", 2, 10, false);

		assertThrows(
			InvalidFileRequestException.class,
			() -> validator.validateWithRetainedFiles(
				List.of(zipFile("new.zip")), policy, 2, 8L
			)
		);
	}

	@Test
	@DisplayName("유지한 파일과 새 파일의 합계가 전체 용량을 넘으면 거절한다")
	void rejectUpdateExceedingCombinedSize() {
		FileUploadValidator validator = validator(DataSize.ofBytes(10), DataSize.ofBytes(12));
		BoardPolicy policy = policy("IMAGE_AND_ZIP", 3, 10, false);

		assertThrows(
			InvalidFileRequestException.class,
			() -> validator.validateWithRetainedFiles(
				List.of(zipFile("new.zip")), policy, 1, 8L
			)
		);
	}

	@Test
	@DisplayName("기존 파일을 유지하면서 새 ZIP 파일을 검증한다")
	void acceptZipWithRetainedFiles() {
		FileUploadValidator validator = validator(DataSize.ofMegabytes(20), DataSize.ofMegabytes(50));
		BoardPolicy policy = policy("IMAGE_AND_ZIP", 3, 1024 * 1024, true);

		List<ValidatedUploadFile> result =
			validator.validateWithRetainedFiles(
				List.of(zipFile("new.zip")), policy, 1, 100L
			);

		assertEquals(1, result.size());
		assertEquals(FileFormat.ZIP, result.getFirst().format());
	}

	@Test
	@DisplayName("새 묶음에서는 UTF-8 한글과 이모지가 포함된 TXT를 허용한다")
	void acceptUtf8TextFile() {
		FileUploadValidator validator =
			validator(DataSize.ofMegabytes(20), DataSize.ofMegabytes(50));
		MockMultipartFile file = new MockMultipartFile(
			"files", "notes.TXT", "application/octet-stream",
			"\uFEFF안녕하세요 😀\n다음 줄\t내용".getBytes(StandardCharsets.UTF_8)
		);

		List<ValidatedUploadFile> result = validator.validate(
			List.of(file), policy("IMAGE_AND_FILE", 3, 1024 * 1024, false)
		);

		assertEquals(FileFormat.TXT, result.getFirst().format());
		assertEquals("text/plain;charset=UTF-8", result.getFirst().contentType());
		assertFalse(result.getFirst().isImage());
	}

	@Test
	@DisplayName("새 묶음에서는 PDF 시그니처가 있는 파일을 허용한다")
	void acceptPdfFile() {
		FileUploadValidator validator =
			validator(DataSize.ofMegabytes(20), DataSize.ofMegabytes(50));
		MockMultipartFile file = new MockMultipartFile(
			"files", "guide.pdf", "application/octet-stream",
			"%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII)
		);

		List<ValidatedUploadFile> result = validator.validate(
			List.of(file), policy("IMAGE_AND_FILE", 3, 1024 * 1024, false)
		);

		assertEquals(FileFormat.PDF, result.getFirst().format());
		assertEquals("application/pdf", result.getFirst().contentType());
		assertFalse(result.getFirst().isImage());
	}

	@Test
	@DisplayName("TXT 뒷부분에 잘못된 UTF-8 바이트가 있으면 거절한다")
	void rejectMalformedUtf8AtEndOfTextFile() {
		FileUploadValidator validator =
			validator(DataSize.ofMegabytes(20), DataSize.ofMegabytes(50));
		byte[] bytes = new byte[8194];
		Arrays.fill(bytes, (byte) 'a');
		bytes[8192] = (byte) 0xC3;
		bytes[8193] = 0x28;

		MockMultipartFile file = new MockMultipartFile(
			"files", "notes.txt", "text/plain", bytes
		);

		assertThrows(
			InvalidFileRequestException.class,
			() -> validator.validate(
				List.of(file), policy("IMAGE_AND_FILE", 3, 1024 * 1024, false)
			)
		);
	}

	@Test
	@DisplayName("TXT에 NUL 문자가 포함되어 있으면 거절한다")
	void rejectTextFileContainingNullCharacter() {
		FileUploadValidator validator =
			validator(DataSize.ofMegabytes(20), DataSize.ofMegabytes(50));
		MockMultipartFile file = new MockMultipartFile(
			"files", "notes.txt", "text/plain",
			"hello\u0000world".getBytes(StandardCharsets.UTF_8)
		);

		assertThrows(
			InvalidFileRequestException.class,
			() -> validator.validate(
				List.of(file), policy("IMAGE_AND_FILE", 3, 1024 * 1024, false)
			)
		);
	}

	@Test
	@DisplayName("ZIP 파일을 TXT 확장자로 바꾸면 거절한다")
	void rejectZipRenamedAsTextFile() {
		FileUploadValidator validator =
			validator(DataSize.ofMegabytes(20), DataSize.ofMegabytes(50));

		assertThrows(
			InvalidFileRequestException.class,
			() -> validator.validate(
				List.of(zipFile("fake.txt")),
				policy("IMAGE_AND_FILE", 3, 1024 * 1024, false)
			)
		);
	}

	@Test
	@DisplayName("텍스트 파일을 PDF 확장자로 바꾸면 거절한다")
	void rejectTextRenamedAsPdfFile() {
		FileUploadValidator validator =
			validator(DataSize.ofMegabytes(20), DataSize.ofMegabytes(50));
		MockMultipartFile file = new MockMultipartFile(
			"files", "fake.pdf", "application/pdf",
			"plain text".getBytes(StandardCharsets.UTF_8)
		);

		assertThrows(
			InvalidFileRequestException.class,
			() -> validator.validate(
				List.of(file), policy("IMAGE_AND_FILE", 3, 1024 * 1024, false)
			)
		);
	}

	@ParameterizedTest
	@CsvSource({"IMAGE, txt", "IMAGE, pdf", "IMAGE_AND_ZIP, txt", "IMAGE_AND_ZIP, pdf"})
	@DisplayName("기존 파일 묶음에서는 TXT와 PDF를 허용하지 않는다")
	void rejectNewFormatsInExistingProfiles(String profile, String extension) {
		FileUploadValidator validator = validator(DataSize.ofMegabytes(20), DataSize.ofMegabytes(50));
		String content = "pdf".equals(extension) ? "%PDF-1.7\n" : "메모";
		MockMultipartFile file = new MockMultipartFile(
			"files", "file." + extension, "application/octet-stream",
			content.getBytes(StandardCharsets.UTF_8)
		);

		assertThrows(
			InvalidFileRequestException.class,
			() -> validator.validate(
				List.of(file), policy(profile, 3, 1024 * 1024, false)
			)
		);
	}



	private FileUploadValidator validator(DataSize maxFileSize, DataSize maxPostSize) {
		FileStorageProperties properties = new FileStorageProperties(
			Path.of("build/test-store"),
			20,
			maxFileSize,
			maxPostSize,
			Duration.ofHours(24)
		);

		return new FileUploadValidator(properties, new FileSignatureDetector());
	}

	private BoardPolicy policy(String uploadProfile, int maxFileCount, long maxFileSize, boolean imageRequired) {
		return BoardPolicy.builder()
			.attachmentsEnabled(true)
			.uploadProfile(uploadProfile)
			.maxFileCount(maxFileCount)
			.maxFileSizeBytes(maxFileSize)
			.imageRequired(imageRequired)
			.build();
	}

	private MockMultipartFile zipFile(String name) {
		byte[] content = { 0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0 };
		return new MockMultipartFile("files", name, "application/zip", content);
	}

	private byte[] createPng() throws Exception {
		BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);
		return output.toByteArray();
	}
}