package io.github.kwp6zgnrsh.boardkitapi.file.service;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.kwp6zgnrsh.boardkitapi.file.exception.FileStorageException;
import io.github.kwp6zgnrsh.boardkitapi.file.mapper.FileCleanupTaskMapper;
import io.github.kwp6zgnrsh.boardkitapi.file.model.FileCleanupTask;

@ExtendWith(MockitoExtension.class)
class FileCleanupServiceTest {

	@Mock
	private FileCleanupTaskMapper cleanupTaskMapper;

	@Mock
	private LocalFileStorage localFileStorage;

	private FileCleanupService cleanupService;

	@BeforeEach
	void setUp() {
		cleanupService = new FileCleanupService(cleanupTaskMapper, localFileStorage);
	}

	@Test
	@DisplayName("파일 삭제에 성공하면 정리 작업을 완료한다")
	void completeTaskAfterFileDeletion() {
		FileCleanupTask task = task(1L, "posts/100/image.png", 0);

		when(cleanupTaskMapper.selectDueTasks(100))
			.thenReturn(List.of(task));
		when(cleanupTaskMapper.deleteTask(1L))
			.thenReturn(1);

		cleanupService.processDueTasks();

		InOrder order = inOrder(localFileStorage, cleanupTaskMapper);
		order.verify(localFileStorage)
			.delete("posts/100/image.png");
		order.verify(cleanupTaskMapper)
			.deleteTask(1L);

		verify(cleanupTaskMapper, never())
			.recordFailure(anyLong(), anyLong(), anyString());
	}

	@Test
	@DisplayName("파일 삭제에 실패하면 재시도 정보를 기록한다")
	void recordRetryAfterFileDeletionFailure() {
		FileCleanupTask task = task(2L, "posts/100/image.png", 2);

		when(cleanupTaskMapper.selectDueTasks(100))
			.thenReturn(List.of(task));
		doThrow(new FileStorageException(
			"파일을 삭제할 수 없습니다.",
			new RuntimeException("permission denied")
		)).when(localFileStorage)
			.delete("posts/100/image.png");
		when(cleanupTaskMapper.recordFailure(
			eq(2L),
			eq(240L),
			anyString()
		)).thenReturn(1);

		cleanupService.processDueTasks();

		verify(cleanupTaskMapper).recordFailure(
			eq(2L),
			eq(240L),
			contains("파일을 삭제할 수 없습니다.")
		);
		verify(cleanupTaskMapper, never()).deleteTask(anyLong());
	}

	@Test
	@DisplayName("한 파일의 삭제 실패 이후에도 다음 작업을 계속 처리한다")
	void continueProcessingAfterFileDeletionFailure() {
		FileCleanupTask failed =
			task(1L, "posts/100/failed.png", 0);
		FileCleanupTask successful =
			task(2L, "posts/100/success.png", 0);

		when(cleanupTaskMapper.selectDueTasks(100))
			.thenReturn(List.of(failed, successful));
		doThrow(new FileStorageException(
			"파일을 삭제할 수 없습니다.",
			new RuntimeException()
		)).when(localFileStorage)
			.delete("posts/100/failed.png");
		when(cleanupTaskMapper.recordFailure(
			eq(1L), eq(60L), anyString()
		)).thenReturn(1);
		when(cleanupTaskMapper.deleteTask(2L))
			.thenReturn(1);

		cleanupService.processDueTasks();

		verify(cleanupTaskMapper).recordFailure(
			eq(1L), eq(60L), anyString()
		);
		verify(localFileStorage).delete("posts/100/success.png");
		verify(cleanupTaskMapper).deleteTask(2L);
	}

	private FileCleanupTask task(Long id, String targetKey, int attempts) {

		return FileCleanupTask.builder()
			.id(id)
			.targetKey(targetKey)
			.attempts(attempts)
			.build();
	}
}