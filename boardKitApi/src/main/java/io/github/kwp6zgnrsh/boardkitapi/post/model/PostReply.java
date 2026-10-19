package io.github.kwp6zgnrsh.boardkitapi.post.model;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PostReply {

	private Long id;
	private Long postId;
	private Long authorId;
	private String content;
	private JsonNode contentDocument;
	private Integer version;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}