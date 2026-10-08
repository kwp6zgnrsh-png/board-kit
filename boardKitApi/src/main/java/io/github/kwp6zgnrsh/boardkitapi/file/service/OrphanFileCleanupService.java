package io.github.kwp6zgnrsh.boardkitapi.file.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import io.github.kwp6zgnrsh.boardkitapi.file.config.FileStorageProperties;
import io.github.kwp6zgnrsh.boardkitapi.file.exception.FileStorageException;
import io.github.kwp6zgnrsh.boardkitapi.file.mapper.FileCleanupTaskMapper;
import io.github.kwp6zgnrsh.boardkitapi.file.model.StoredFileSnapshot;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrphanFileCleanupService {

	private final FileStorageProperties properties;
	private final FileCleanupTaskMapper cleanupTaskMapper;
	private final LocalFileStorage localFileStorage;

	public void cleanupOrphanFiles() {
		Instant cutoff = Instant.now().minus(properties.orphanMinimumAge());

		List<StoredFileSnapshot> candidates = localFileStorage.findFilesOlderThan(cutoff);

		for (StoredFileSnapshot candidate : candidates) {
			cleanupCandidate(candidate, cutoff);
		}
	}

	private void cleanupCandidate(StoredFileSnapshot candidate, Instant cutoff) {

		String targetKey = candidate.storageKey();

		if (cleanupTaskMapper.existsReferenceOrCleanupTask(targetKey)) {
			return;
		}

		try {
			localFileStorage.deleteIfUnchanged(candidate, cutoff);
		} catch (FileStorageException exception) {
			cleanupTaskMapper.enqueueDeletes(List.of(targetKey));
		}
	}
}