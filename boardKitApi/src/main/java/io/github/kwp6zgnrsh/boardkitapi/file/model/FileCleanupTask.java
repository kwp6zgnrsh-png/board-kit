package io.github.kwp6zgnrsh.boardkitapi.file.model;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FileCleanupTask {
	private Long id;
	private String targetKey;
	private int attempts;
	private LocalDateTime nextAttemptAt;
	private String lastError;
	private LocalDateTime createdAt;
}