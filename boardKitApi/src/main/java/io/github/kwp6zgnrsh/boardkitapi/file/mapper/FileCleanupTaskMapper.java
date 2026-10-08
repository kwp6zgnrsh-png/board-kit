package io.github.kwp6zgnrsh.boardkitapi.file.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import io.github.kwp6zgnrsh.boardkitapi.file.model.FileCleanupTask;

@Mapper
public interface FileCleanupTaskMapper {
	void enqueueDeletes(@Param("targetKeys") List<String> targetKeys);

	List<FileCleanupTask> selectDueTasks(@Param("limit") int limit);

	int deleteTask(@Param("taskId") Long taskId);

	int recordFailure(
		@Param("taskId") Long taskId,
		@Param("retryDelaySeconds") long retryDelaySeconds,
		@Param("lastError") String lastError
	);

	boolean existsReferenceOrCleanupTask(
		@Param("targetKey") String targetKey
	);
}