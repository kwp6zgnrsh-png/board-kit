package io.github.kwp6zgnrsh.boardkitapi.post.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Post {

	private Long id;
	private Long boardId;
	private Long authorId;
	private Long categoryId;

	private String title;
	private String content;

	private boolean isSecret;
	private String secretPasswordHash;

	private boolean isPinned;
	private boolean commentsLocked;

	private Long representativeFileId;
	private Long viewCount;
	private Integer version;

	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
	private LocalDateTime deletedAt;
}