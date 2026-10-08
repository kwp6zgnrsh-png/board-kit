package io.github.kwp6zgnrsh.boardkitapi.file.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;

import io.github.kwp6zgnrsh.boardkitapi.file.config.FileStorageProperties;
import io.github.kwp6zgnrsh.boardkitapi.file.exception.FileStorageException;
import io.github.kwp6zgnrsh.boardkitapi.file.model.StoredFileSnapshot;
import io.github.kwp6zgnrsh.boardkitapi.file.model.StoredPostFile;
import io.github.kwp6zgnrsh.boardkitapi.file.model.TempStoredFile;
import io.github.kwp6zgnrsh.boardkitapi.file.model.ValidatedUploadFile;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalFileStorage {

	private static final String TEMP_DIRECTORY = "temp";
	private static final String POST_DIRECTORY = "posts";
	private static final String THUMBNAIL_DIRECTORY = "thumbnails";

	private final FileStorageProperties properties;

	@PostConstruct
	void initializeDirectories() {
		try {
			Files.createDirectories(resolveDirectory(TEMP_DIRECTORY));
			Files.createDirectories(resolveDirectory(POST_DIRECTORY));
			Files.createDirectories(resolveDirectory(THUMBNAIL_DIRECTORY));
		} catch (IOException exception) {
			throw new FileStorageException("파일 저장 디렉터리를 생성할 수 없습니다.", exception);
		}
	}

	public List<TempStoredFile> storeTemporary(List<ValidatedUploadFile> uploads) {
		if (uploads.isEmpty()) {
			return List.of();
		}

		List<TempStoredFile> storedFiles = new ArrayList<>(uploads.size());

		try {
			for (ValidatedUploadFile upload : uploads) {
				storedFiles.add(storeTemporary(upload));
			}
			return List.copyOf(storedFiles);
		} catch (RuntimeException exception) {
			deleteTemporaryQuietly(storedFiles);
			throw exception;
		}
	}

	public void delete(String storageKey) {
		Path target = resolveStorageKey(storageKey);

		try {
			Files.deleteIfExists(target);
		} catch (IOException exception) {
			throw new FileStorageException("파일을 삭제할 수 없습니다.", exception);
		}
	}

	public List<StoredFileSnapshot> findFilesOlderThan(Instant cutoff) {
		Objects.requireNonNull(cutoff, "파일 탐색 기준 시각이 필요합니다.");

		List<StoredFileSnapshot> snapshots = new ArrayList<>();

		for (String directoryName : List.of(TEMP_DIRECTORY, POST_DIRECTORY, THUMBNAIL_DIRECTORY)) {
			Path directory = resolveDirectory(directoryName);

			try (Stream<Path> paths = Files.walk(directory)) {
				Iterator<Path> iterator = paths.iterator();

				while (iterator.hasNext()) {
					Path path = iterator.next();
					BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class,
						LinkOption.NOFOLLOW_LINKS);

					if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
						continue;
					}

					Instant lastModifiedAt = attributes.lastModifiedTime().toInstant();

					if (lastModifiedAt.isAfter(cutoff)) {
						continue;
					}

					snapshots.add(new StoredFileSnapshot(toStorageKey(path), attributes.size(), lastModifiedAt));
				}
			} catch (IOException | UncheckedIOException exception) {
				throw new FileStorageException("파일 정리 대상을 탐색할 수 없습니다.", exception);
			}
		}

		return List.copyOf(snapshots);
	}

	public void deleteIfUnchanged(StoredFileSnapshot snapshot, Instant cutoff) {

		Objects.requireNonNull(snapshot, "파일 상태 정보가 필요합니다.");
		Objects.requireNonNull(cutoff, "파일 삭제 기준 시각이 필요합니다.");

		Path target = resolveStorageKey(snapshot.storageKey());

		try {
			BasicFileAttributes attributes = Files.readAttributes(target, BasicFileAttributes.class,
				LinkOption.NOFOLLOW_LINKS);

			if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
				return;
			}

			Instant currentLastModifiedAt = attributes.lastModifiedTime().toInstant();

			boolean changed =
				attributes.size() != snapshot.sizeBytes() || !currentLastModifiedAt.equals(snapshot.lastModifiedAt());

			if (changed || currentLastModifiedAt.isAfter(cutoff)) {
				return;
			}

			Files.deleteIfExists(target);
		} catch (NoSuchFileException ignored) {
			// 다른 정리 작업에서 이미 삭제했으면 완료로 본다.
		} catch (IOException exception) {
			throw new FileStorageException("고아 파일을 삭제할 수 없습니다.", exception);
		}
	}

	private TempStoredFile storeTemporary(ValidatedUploadFile upload) {
		Path target = null;

		try {
			target = Files.createTempFile(resolveDirectory(TEMP_DIRECTORY), "upload-", "." + upload.storageExtension());

			try (InputStream input = upload.source().getInputStream()) {
				Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
			}

			if (Files.size(target) != upload.sizeBytes()) {
				throw new IOException("저장된 파일 크기가 요청 파일과 일치하지 않습니다.");
			}

			String tempKey = TEMP_DIRECTORY + "/" + target.getFileName();

			return new TempStoredFile(tempKey, upload.originalName(), upload.format(), upload.sizeBytes(),
				upload.sortOrder());
		} catch (IOException exception) {
			deletePathQuietly(target);
			throw new FileStorageException("임시 파일을 저장할 수 없습니다.", exception);
		}
	}

	public List<StoredPostFile> moveToPost(Long postId, List<TempStoredFile> temporaryFiles, boolean thumbnailEnabled) {
		if (postId == null || postId <= 0) {
			throw new IllegalArgumentException("게시글 ID는 0보다 커야 합니다.");
		}
		if (temporaryFiles.isEmpty()) {
			return List.of();
		}

		List<StoredPostFile> storedFiles = new ArrayList<>(temporaryFiles.size());
		try {
			for (TempStoredFile file : temporaryFiles) {
				storedFiles.add(moveToPost(postId, file, thumbnailEnabled));
			}
			return List.copyOf(storedFiles);
		} catch (RuntimeException exception) {
			deleteStoredQuietly(storedFiles);
			deleteTemporaryQuietly(temporaryFiles);
			deleteDirectoryQuietly(resolveDirectory(POST_DIRECTORY).resolve(postId.toString()));
			deleteDirectoryQuietly(resolveDirectory(THUMBNAIL_DIRECTORY).resolve(postId.toString()));
			throw exception;
		}
	}

	public Resource load(String storageKey) {
		Path target = resolveStorageKey(storageKey);

		if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(target)) {
			throw new FileStorageException("저장된 파일을 읽을 수 없습니다.", new NoSuchFileException(storageKey));
		}

		return new FileSystemResource(target);
	}

	private StoredPostFile moveToPost(Long postId, TempStoredFile temporaryFile, boolean thumbnailEnabled) {
		Path source = resolveStorageKey(temporaryFile.tempKey());
		if (!source.getParent().equals(resolveDirectory(TEMP_DIRECTORY))) {
			throw new IllegalArgumentException("임시 파일 저장 키가 올바르지 않습니다.");
		}

		Path postDirectory = resolveDirectory(POST_DIRECTORY).resolve(postId.toString());
		String fileName = UUID.randomUUID() + "." + temporaryFile.format().storageExtension();
		Path destination = postDirectory.resolve(fileName);
		String storageKey = POST_DIRECTORY + "/" + postId + "/" + fileName;

		try {
			if (!Files.isRegularFile(source)) {
				throw new IOException("임시 파일을 찾을 수 없습니다.");
			}

			Files.createDirectories(postDirectory);
			moveFile(source, destination);

			String thumbnailKey =
				thumbnailEnabled && temporaryFile.format().isImage() ? createThumbnail(postId, destination) : null;

			return new StoredPostFile(storageKey, temporaryFile.originalName(), temporaryFile.format(),
				temporaryFile.sizeBytes(), temporaryFile.sortOrder(), thumbnailKey);
		} catch (IOException exception) {
			deletePathQuietly(destination);
			throw new FileStorageException("임시 파일을 게시글 저장소로 이동할 수 없습니다.", exception);
		} catch (RuntimeException exception) {
			deletePathQuietly(destination);
			throw exception;
		}
	}

	private String createThumbnail(Long postId, Path original) {
		Path directory = resolveDirectory(THUMBNAIL_DIRECTORY).resolve(postId.toString());
		String fileName = UUID.randomUUID() + ".png";
		Path target = directory.resolve(fileName);

		try {
			Files.createDirectories(directory);
			Thumbnails.of(original.toFile())
				.size(360, 360)
				.crop(Positions.CENTER)
				.outputFormat("png")
				.toFile(target.toFile());

			return THUMBNAIL_DIRECTORY + "/" + postId + "/" + fileName;
		} catch (IOException | RuntimeException exception) {
			deletePathQuietly(target);
			throw new FileStorageException("썸네일을 생성할 수 없습니다.", exception);
		}
	}

	private void moveFile(Path source, Path destination) throws IOException {
		try {
			Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(source, destination);
		}
	}

	private Path resolveDirectory(String directory) {
		return properties.storageRoot().resolve(directory).normalize();
	}

	private Path resolveStorageKey(String storageKey) {
		try {
			Path relativePath = Path.of(storageKey);

			if (relativePath.isAbsolute()) {
				throw new IllegalArgumentException("절대 경로는 저장 키로 사용할 수 없습니다.");
			}

			Path resolvedPath = properties.storageRoot().resolve(relativePath).normalize();

			if (!resolvedPath.startsWith(properties.storageRoot())) {
				throw new IllegalArgumentException("저장소 밖의 경로는 사용할 수 없습니다.");
			}

			return resolvedPath;
		} catch (InvalidPathException exception) {
			throw new IllegalArgumentException("올바르지 않은 파일 저장 키입니다.", exception);
		}
	}

	private void deleteTemporaryQuietly(List<TempStoredFile> files) {
		for (TempStoredFile file : files) {
			deletePathQuietly(resolveStorageKey(file.tempKey()));
		}
	}

	private void deleteStoredQuietly(List<StoredPostFile> files) {
		for (StoredPostFile file : files) {
			if (file.thumbnailKey() != null) {
				deletePathQuietly(resolveStorageKey(file.thumbnailKey()));
			}
			deletePathQuietly(resolveStorageKey(file.storageKey()));
		}
	}

	private void deleteDirectoryQuietly(Path directory) {
		try {
			Files.deleteIfExists(directory);
		} catch (IOException ignored) {
			// 파일이 남아 있으면 고아 파일 스캔 작업이 정리한다.
		}
	}

	private void deletePathQuietly(Path path) {
		if (path == null) {
			return;
		}

		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
			// 남은 임시 파일은 고아 파일 스캔 작업이 정리한다.
		}
	}

	private String toStorageKey(Path path) {
		return properties.storageRoot().relativize(path).toString().replace('\\', '/');
	}
}