package io.github.kwp6zgnrsh.boardkitapi.file.model;

import org.springframework.core.io.Resource;

public record PostFileContent(
	Resource resource,
	String contentType,
	String originalName,
	boolean attachment
) {}