package io.github.kwp6zgnrsh.boardkitapi.file.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties(prefix = "boardkit.file")
public record FileStorageProperties(

	@NotNull Path storageRoot,

	@Positive int maxFileCount,

	@NotNull DataSize maxFileSize,

	@NotNull DataSize maxPostSize,

	@NotNull Duration orphanMinimumAge) {

	public FileStorageProperties {
		if (storageRoot != null) {
			storageRoot = storageRoot.toAbsolutePath().normalize();
		}
	}

	@AssertTrue(message = "파일 크기 제한은 0보다 커야 하며 게시글 전체 용량은 파일 하나의 용량 이상이어야 합니다.")
	public boolean isValidSizeLimits() {
		if (maxFileSize == null || maxPostSize == null) {
			return true;
		}

		return maxFileSize.toBytes() > 0 && maxPostSize.toBytes() >= maxFileSize.toBytes();
	}

	@AssertTrue(message = "고아 파일의 최소 경과 시간은 0보다 커야 합니다.")
	public boolean isValidOrphanMinimumAge() {
		return orphanMinimumAge == null || (!orphanMinimumAge.isZero() && !orphanMinimumAge.isNegative());
	}

	@AssertTrue(message = "파일 저장 루트는 파일시스템의 루트 디렉터리일 수 없습니다.")
	public boolean isValidStorageRoot() {
		return storageRoot == null || storageRoot.getNameCount() > 0;
	}
}