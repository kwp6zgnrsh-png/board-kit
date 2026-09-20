package io.github.kwp6zgnrsh.boardkitapi.board.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Board {
	private Long id;
	private String name;
	private String slug;
	private String description;
	private String displayType;
	private String status;
	private Integer sortOrder;
	private Long createdBy;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}