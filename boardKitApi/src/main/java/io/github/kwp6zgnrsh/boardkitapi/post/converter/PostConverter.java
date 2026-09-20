package io.github.kwp6zgnrsh.boardkitapi.post.converter;

import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.model.Post;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PostConverter {

	@BeanMapping(ignoreByDefault = true)
	@Mapping(target = "boardId", source = "boardId")
	@Mapping(target = "authorId", source = "authorId")
	@Mapping(target = "categoryId", source = "request.categoryId")
	@Mapping(target = "title", source = "request.title")
	@Mapping(target = "content", source = "request.content")
	@Mapping(target = "isSecret", source = "request.isSecret")
	@Mapping(
		target = "secretPasswordHash",
		source = "secretPasswordHash"
	)
	@Mapping(target = "isPinned", constant = "false")
	@Mapping(
		target = "commentsLocked",
		source = "request.commentsLocked"
	)
	@Mapping(target = "viewCount", constant = "0L")
	@Mapping(target = "version", constant = "0")
	Post toPost(
		PostCreateRequest request,
		Long boardId,
		Long authorId,
		String secretPasswordHash
	);

	PostCreateResponse toCreateResponse(Post post);
}