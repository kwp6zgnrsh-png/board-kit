package io.github.kwp6zgnrsh.boardkitapi.board.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BoardCategory {

	private Long id;
	private Long boardId;
	private String name;
	private Integer sortOrder;
	private boolean active;
}