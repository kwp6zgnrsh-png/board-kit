package io.github.kwp6zgnrsh.boardkitapi.file.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.github.kwp6zgnrsh.boardkitapi.file.mapper.FileCleanupTaskMapper;
import io.github.kwp6zgnrsh.boardkitapi.file.model.FileCleanupTask;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileCleanupService {

	private static final int BATCH_SIZE = 100;
	private static final long INITIAL_RETRY_DELAY_SECONDS = 60L;
	private static final long MAX_RETRY_DELAY_SECONDS = 86_400L;
	private static final int MAX_ERROR_LENGTH = 1_000;

	private final FileCleanupTaskMapper cleanupTaskMapper;
	private final LocalFileStorage localFileStorage;

	public void processDueTasks() {
		List<FileCleanupTask> tasks = cleanupTaskMapper.selectDueTasks(BATCH_SIZE);

		for (FileCleanupTask task : tasks) {
			processTask(task);
		}
	}

	private void processTask(FileCleanupTask task) {
		try {
			localFileStorage.delete(task.getTargetKey());
		} catch (RuntimeException exception) {
			recordFailure(task, exception);
			return;
		}

		int deletedCount = cleanupTaskMapper.deleteTask(task.getId());

		if (deletedCount != 1) {
			throw new IllegalStateException("파일 정리 작업 완료 처리에 실패했습니다. taskId=" + task.getId());
		}
	}

	private void recordFailure(FileCleanupTask task, RuntimeException exception) {

		long retryDelaySeconds = calculateRetryDelaySeconds(task.getAttempts());

		int updatedCount = cleanupTaskMapper.recordFailure(task.getId(), retryDelaySeconds, toErrorMessage(exception));

		if (updatedCount != 1) {
			throw new IllegalStateException("파일 정리 작업 실패 기록에 실패했습니다. taskId=" + task.getId());
		}
	}

	private long calculateRetryDelaySeconds(int attempts) {
		int exponent = Math.clamp(attempts, 0, 11);
		long delay = INITIAL_RETRY_DELAY_SECONDS * (1L << exponent);

		return Math.min(delay, MAX_RETRY_DELAY_SECONDS);
	}

	private String toErrorMessage(RuntimeException exception) {
		String message = exception.getMessage();
		String result =
			exception.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);

		return result.length() <= MAX_ERROR_LENGTH ? result : result.substring(0, MAX_ERROR_LENGTH);
	}
}