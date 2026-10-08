package io.github.kwp6zgnrsh.boardkitapi.file.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import io.github.kwp6zgnrsh.boardkitapi.file.config.FileStorageProperties;
import io.github.kwp6zgnrsh.boardkitapi.file.exception.FileStorageException;
import io.github.kwp6zgnrsh.boardkitapi.file.model.FileFormat;
import io.github.kwp6zgnrsh.boardkitapi.file.model.StoredFileSnapshot;
import io.github.kwp6zgnrsh.boardkitapi.file.model.StoredPostFile;
import io.github.kwp6zgnrsh.boardkitapi.file.model.TempStoredFile;
import io.github.kwp6zgnrsh.boardkitapi.file.model.ValidatedUploadFile;

class LocalFileStorageTest {

	@TempDir
	Path temporaryDirectory;

	private Path storageRoot;
	private LocalFileStorage fileStorage;

	@BeforeEach
	void setUp() {
		storageRoot = temporaryDirectory.resolve("store");

		FileStorageProperties properties = new FileStorageProperties(
			storageRoot,
			20,
			DataSize.ofMegabytes(20),
			DataSize.ofMegabytes(50),
			Duration.ofHours(24)
		);

		fileStorage = new LocalFileStorage(properties);
		fileStorage.initializeDirectories();
	}

	@Test
	@DisplayName("검증된 파일을 임시 디렉터리에 저장한다")
	void storeValidatedFileInTemporaryDirectory() throws Exception {
		byte[] content = { 0x50, 0x4B, 0x03, 0x04 };
		MockMultipartFile source = new MockMultipartFile("files", "archive.zip", "application/zip", content);
		ValidatedUploadFile upload = new ValidatedUploadFile(source, "archive.zip", FileFormat.ZIP, content.length, 0);

		TempStoredFile storedFile = fileStorage.storeTemporary(List.of(upload)).getFirst();
		Path storedPath = storageRoot.resolve(storedFile.tempKey());

		assertTrue(storedFile.tempKey().startsWith("temp/upload-"));
		assertTrue(Files.exists(storedPath));
		assertArrayEquals(content, Files.readAllBytes(storedPath));
	}

	@Test
	@DisplayName("저장된 임시 파일을 삭제한다")
	void deleteStoredTemporaryFile() {
		byte[] content = { 0x50, 0x4B, 0x03, 0x04 };
		MockMultipartFile source = new MockMultipartFile("files", "archive.zip", "application/zip", content);
		ValidatedUploadFile upload = new ValidatedUploadFile(source, "archive.zip", FileFormat.ZIP, content.length, 0);

		TempStoredFile storedFile = fileStorage.storeTemporary(List.of(upload)).getFirst();
		Path storedPath = storageRoot.resolve(storedFile.tempKey());

		fileStorage.delete(storedFile.tempKey());

		assertFalse(Files.exists(storedPath));
	}

	@Test
	@DisplayName("여러 파일 중 하나의 저장이 실패하면 앞서 저장한 임시 파일도 삭제한다")
	void deletePreviouslyStoredFilesWhenBatchStorageFails() throws Exception {
		byte[] content = { 0x50, 0x4B, 0x03, 0x04 };
		MockMultipartFile validSource = new MockMultipartFile("files", "valid.zip", "application/zip", content);
		ValidatedUploadFile validUpload =
			new ValidatedUploadFile(validSource, "valid.zip", FileFormat.ZIP, content.length, 0);

		MultipartFile brokenSource = mock(MultipartFile.class);
		when(brokenSource.getInputStream()).thenThrow(new IOException("읽기 실패"));
		ValidatedUploadFile brokenUpload =
			new ValidatedUploadFile(brokenSource, "broken.zip", FileFormat.ZIP, content.length, 1);

		assertThrows(
			FileStorageException.class,
			() -> fileStorage.storeTemporary(List.of(validUpload, brokenUpload))
		);

		try (Stream<Path> paths = Files.list(storageRoot.resolve("temp"))) {
			assertTrue(paths.findAny().isEmpty());
		}
	}

	@Test
	@DisplayName("저장소 밖을 가리키는 파일 키를 거절한다")
	void rejectStorageKeyOutsideRoot() {
		assertThrows(IllegalArgumentException.class, () -> fileStorage.delete("../outside.txt"));
	}

	@Test
	@DisplayName("이미지 중앙을 잘라 360×360 PNG 썸네일을 만든다")
	void createCenteredThumbnail() throws Exception {
		BufferedImage image = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.RED);
		graphics.fillRect(0, 0, 200, 400);
		graphics.setColor(Color.GREEN);
		graphics.fillRect(200, 0, 400, 400);
		graphics.setColor(Color.BLUE);
		graphics.fillRect(600, 0, 200, 400);
		graphics.dispose();

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(image, "png", bytes);

		byte[] content = bytes.toByteArray();
		MockMultipartFile source =
			new MockMultipartFile("files", "image.png", "image/png", content);
		ValidatedUploadFile upload =
			new ValidatedUploadFile(source, "image.png", FileFormat.PNG, content.length, 0);

		TempStoredFile temporary = fileStorage.storeTemporary(List.of(upload)).getFirst();
		StoredPostFile stored = fileStorage.moveToPost(1L, List.of(temporary), true).getFirst();
		BufferedImage thumbnail =
			ImageIO.read(storageRoot.resolve(stored.thumbnailKey()).toFile());

		assertEquals(360, thumbnail.getWidth());
		assertEquals(360, thumbnail.getHeight());
		assertEquals(Color.GREEN.getRGB(), thumbnail.getRGB(0, 0));
	}

	@Test
	@DisplayName("기준 시각보다 오래된 파일을 정리 후보로 조회한다")
	void findFilesOlderThanCutoff() throws Exception {
		Path oldFile = storageRoot.resolve("temp/old.zip");
		Path recentFile = storageRoot.resolve("temp/recent.zip");

		Files.write(oldFile, new byte[] {1, 2, 3});
		Files.write(recentFile, new byte[] {4, 5, 6});

		Instant now = Instant.now();
		Files.setLastModifiedTime(oldFile, FileTime.from(now.minus(Duration.ofHours(25))));
		Files.setLastModifiedTime(recentFile, FileTime.from(now.minus(Duration.ofHours(1))));

		List<StoredFileSnapshot> result = fileStorage.findFilesOlderThan(now.minus(Duration.ofHours(24)));

		assertEquals(1, result.size());
		assertEquals("temp/old.zip", result.getFirst().storageKey());
		assertEquals(3L, result.getFirst().sizeBytes());
	}

	@Test
	@DisplayName("탐색 이후 변경되지 않은 오래된 파일을 삭제한다")
	void deleteOldFileWhenSnapshotIsUnchanged() throws Exception {
		Path file = storageRoot.resolve("temp/old.zip");
		Files.write(file, new byte[] {1, 2, 3});

		Instant now = Instant.now();
		Instant oldTime = now.minus(Duration.ofHours(25));
		Files.setLastModifiedTime(file, FileTime.from(oldTime));

		StoredFileSnapshot snapshot = fileStorage.findFilesOlderThan(now.minus(Duration.ofHours(24))).getFirst();

		fileStorage.deleteIfUnchanged(snapshot, now.minus(Duration.ofHours(24)));

		assertFalse(Files.exists(file));
	}

	@Test
	@DisplayName("탐색 이후 변경된 파일은 삭제하지 않는다")
	void keepFileWhenChangedAfterSnapshot() throws Exception {
		Path file = storageRoot.resolve("temp/old.zip");
		Files.write(file, new byte[] {1, 2, 3});

		Instant now = Instant.now();
		Files.setLastModifiedTime(file, FileTime.from(now.minus(Duration.ofHours(25))));

		StoredFileSnapshot snapshot = fileStorage.findFilesOlderThan(now.minus(Duration.ofHours(24))).getFirst();

		Files.write(file, new byte[] {1, 2, 3, 4});

		fileStorage.deleteIfUnchanged(snapshot, now.minus(Duration.ofHours(24)));

		assertTrue(Files.exists(file));
	}
}