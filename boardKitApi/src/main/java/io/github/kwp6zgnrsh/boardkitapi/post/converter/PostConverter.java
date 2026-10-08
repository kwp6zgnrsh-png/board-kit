package io.github.kwp6zgnrsh.boardkitapi.post.converter;

import java.util.List;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import io.github.kwp6zgnrsh.boardkitapi.file.dto.response.PostFileResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.model.Post;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostDetail;
import io.github.kwp6zgnrsh.boardkitapi.post.service.PostDocumentValidator.ValidatedDocument;

@Mapper(componentModel = "spring")
public interface PostConverter {

	@BeanMapping(ignoreByDefault = true)
	@Mapping(target = "boardId", source = "boardId")
	@Mapping(target = "authorId", source = "authorId")
	@Mapping(target = "categoryId", source = "request.categoryId")
	@Mapping(target = "title", source = "request.title")
	@Mapping(target = "content", source = "document.text")
	@Mapping(target = "contentDocument", source = "document.document")
	@Mapping(target = "isSecret", source = "request.isSecret")
	@Mapping(target = "secretPasswordHash", source = "secretPasswordHash")
	@Mapping(target = "isPinned", constant = "false")
	@Mapping(target = "commentsLocked", source = "request.commentsLocked")
	@Mapping(target = "viewCount", constant = "0L")
	@Mapping(target = "version", constant = "0")
	Post toPost(PostCreateRequest request, Long boardId, Long authorId, String secretPasswordHash,
		ValidatedDocument document);

	PostCreateResponse toCreateResponse(Post post);

	@Mapping(target = "viewCount", source = "increasedViewCount")
	@Mapping(target = "files", source = "files")
	PostDetailResponse toDetailResponse(PostDetail postDetail, long increasedViewCount, List<PostFileResponse> files);

	@BeanMapping(ignoreByDefault = true)
	@Mapping(target = "id", source = "postId")
	@Mapping(target = "boardId", source = "boardId")
	@Mapping(target = "authorId", source = "authorId")
	@Mapping(target = "categoryId", source = "request.categoryId")
	@Mapping(target = "title", source = "request.title")
	@Mapping(target = "content", source = "document.text")
	@Mapping(target = "contentDocument", source = "document.document")
	@Mapping(target = "isSecret", source = "request.isSecret")
	@Mapping(target = "secretPasswordHash", source = "secretPasswordHash")
	@Mapping(target = "commentsLocked", source = "request.commentsLocked")
	@Mapping(target = "version", source = "request.version")
	Post toUpdatedPost(PostUpdateRequest request, Long postId, Long boardId, Long authorId, String secretPasswordHash,
		ValidatedDocument document);
}