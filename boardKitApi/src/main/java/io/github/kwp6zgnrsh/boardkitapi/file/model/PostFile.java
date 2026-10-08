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
public class PostFile {

	private Long id;
	private Long postId;
	private String originalName;
	private String storageKey;
	private String contentType;
	private String fileUsage;
	private Long sizeBytes;
	private String thumbnailKey;
	private Integer sortOrder;
	private LocalDateTime createdAt;
	private LocalDateTime deletedAt;
}