package io.github.kwp6zgnrsh.boardkitapi.comment.converter;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import io.github.kwp6zgnrsh.boardkitapi.comment.dto.request.CommentCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.comment.dto.response.CommentCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.comment.model.Comment;

@Mapper(componentModel = "spring")
public interface CommentConverter {

	@BeanMapping(ignoreByDefault = true)
	@Mapping(target = "postId", source = "postId")
	@Mapping(target = "authorId", source = "authorId")
	@Mapping(target = "parentId", source = "request.parentId")
	@Mapping(target = "content", source = "request.content")
	Comment toComment(CommentCreateRequest request, Long postId, Long authorId);

	CommentCreateResponse toCreateResponse(Comment comment);
}