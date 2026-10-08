package io.github.kwp6zgnrsh.boardkitapi.file.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

import io.github.kwp6zgnrsh.boardkitapi.file.config.FileStorageProperties;
import io.github.kwp6zgnrsh.boardkitapi.file.exception.FileStorageException;
import io.github.kwp6zgnrsh.boardkitapi.file.mapper.FileCleanupTaskMapper;
import io.github.kwp6zgnrsh.boardkitapi.file.model.StoredFileSnapshot;

@ExtendWith(MockitoExtension.class)
class OrphanFileCleanupServiceTest {

	@Mock
	private FileCleanupTaskMapper cleanupTaskMapper;

	@Mock
	private LocalFileStorage localFileStorage;

	private OrphanFileCleanupService cleanupService;

	@BeforeEach
	void setUp() {
		FileStorageProperties properties =
			new FileStorageProperties(
				Path.of("build/test-store"),
				20,
				DataSize.ofMegabytes(20),
				DataSize.ofMegabytes(50),
				Duration.ofHours(24)
			);

		cleanupService = new OrphanFileCleanupService(properties, cleanupTaskMapper, localFileStorage);
	}

	@Test
	@DisplayName("DB 참조와 대기 작업이 없는 오래된 파일을 삭제한다")
	void deleteUnreferencedOldFile() {
		StoredFileSnapshot candidate = candidate(
			"posts/100/orphan.png"
		);

		when(localFileStorage.findFilesOlderThan(any(Instant.class)))
			.thenReturn(List.of(candidate));
		when(cleanupTaskMapper
			.existsReferenceOrCleanupTask(candidate.storageKey()))
			.thenReturn(false);

		cleanupService.cleanupOrphanFiles();

		verify(localFileStorage).deleteIfUnchanged(
			eq(candidate),
			any(Instant.class)
		);
		verify(cleanupTaskMapper, never())
			.enqueueDeletes(anyList());
	}

	@Test
	@DisplayName("DB 참조나 대기 작업이 있는 파일은 삭제하지 않는다")
	void keepReferencedOrPendingFile() {
		StoredFileSnapshot candidate = candidate(
			"posts/100/active.png"
		);

		when(localFileStorage.findFilesOlderThan(any(Instant.class)))
			.thenReturn(List.of(candidate));
		when(cleanupTaskMapper
			.existsReferenceOrCleanupTask(candidate.storageKey()))
			.thenReturn(true);

		cleanupService.cleanupOrphanFiles();

		verify(localFileStorage, never())
			.deleteIfUnchanged(any(), any());
		verify(cleanupTaskMapper, never())
			.enqueueDeletes(anyList());
	}

	@Test
	@DisplayName("고아 파일 삭제에 실패하면 재시도 작업을 등록한다")
	void enqueueCleanupTaskAfterDeletionFailure() {
		StoredFileSnapshot candidate = candidate(
			"thumbnails/100/orphan.png"
		);

		when(localFileStorage.findFilesOlderThan(any(Instant.class)))
			.thenReturn(List.of(candidate));
		when(cleanupTaskMapper
			.existsReferenceOrCleanupTask(candidate.storageKey()))
			.thenReturn(false);
		doThrow(new FileStorageException(
			"고아 파일을 삭제할 수 없습니다.",
			new RuntimeException("permission denied")
		)).when(localFileStorage)
			.deleteIfUnchanged(eq(candidate), any(Instant.class));

		cleanupService.cleanupOrphanFiles();

		verify(cleanupTaskMapper)
			.enqueueDeletes(List.of(candidate.storageKey()));
	}

	private StoredFileSnapshot candidate(String storageKey) {
		return new StoredFileSnapshot(
			storageKey,
			100L,
			Instant.now().minus(Duration.ofHours(25))
		);
	}
}