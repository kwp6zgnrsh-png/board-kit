package io.github.kwp6zgnrsh.boardkitapi.file.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.kwp6zgnrsh.boardkitapi.file.service.FileCleanupService;
import io.github.kwp6zgnrsh.boardkitapi.file.service.OrphanFileCleanupService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FileCleanupScheduler {

	private final FileCleanupService cleanupService;
	private final OrphanFileCleanupService orphanFileCleanupService;

	@Scheduled(fixedDelayString = "${boardkit.file-cleanup.interval:PT1M}")
	public void processCleanupTasks() {
		cleanupService.processDueTasks();
	}

	@Scheduled(fixedDelayString = "${boardkit.file-cleanup.orphan-scan-interval:PT6H}", initialDelayString = "${boardkit.file-cleanup.orphan-scan-initial-delay:PT1M}")
	public void cleanupOrphanFiles() {
		orphanFileCleanupService.cleanupOrphanFiles();
	}
}