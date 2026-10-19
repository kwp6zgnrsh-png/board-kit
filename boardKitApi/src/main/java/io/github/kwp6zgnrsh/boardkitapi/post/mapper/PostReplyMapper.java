package io.github.kwp6zgnrsh.boardkitapi.post.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostReplyFilter;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.AdminPostReplySummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostReplyResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostReply;
import tools.jackson.databind.JsonNode;

@Mapper
public interface PostReplyMapper {

	int insertPostReply(PostReply reply);

	Optional<PostReplyResponse> selectPostReply(
		@Param("boardId") Long boardId,
		@Param("postId") Long postId
	);

	int updatePostReply(
		@Param("boardId") Long boardId,
		@Param("postId") Long postId,
		@Param("replyId") Long replyId,
		@Param("content") String content,
		@Param("contentDocument") JsonNode contentDocument,
		@Param("version") Integer version
	);

	int deletePostReply(
		@Param("boardId") Long boardId,
		@Param("postId") Long postId,
		@Param("replyId") Long replyId,
		@Param("version") Integer version
	);

	long countAdminPostReplyTargets(@Param("filter") PostReplyFilter filter);

	List<AdminPostReplySummaryResponse> selectAdminPostReplyTargets(
		@Param("filter") PostReplyFilter filter,
		@Param("limit") int limit,
		@Param("offset") long offset
	);
}