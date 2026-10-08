package io.github.kwp6zgnrsh.boardkitapi.file.model;

import java.time.Instant;

public record StoredFileSnapshot(
	String storageKey,
	long sizeBytes,
	Instant lastModifiedAt
) {
}