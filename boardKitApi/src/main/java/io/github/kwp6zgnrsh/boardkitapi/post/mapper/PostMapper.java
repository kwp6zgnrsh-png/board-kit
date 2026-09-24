package io.github.kwp6zgnrsh.boardkitapi.post.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.model.Post;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostDetail;

@Mapper
public interface PostMapper {

	int insertPost(Post post);

	List<PostSummaryResponse> selectPostSummaries(
		@Param("boardId") Long boardId,
		@Param("limit") int limit,
		@Param("offset") long offset
	);

	long countActivePostsByBoardId(
		@Param("boardId") Long boardId
	);

	Optional<PostDetail> selectActivePostDetail(
		@Param("boardId") Long boardId,
		@Param("postId") Long postId
	);

	int increaseViewCount(
		@Param("boardId") Long boardId,
		@Param("postId") Long postId
	);

	int updatePost(Post post);

	int softDeletePost(
		@Param("boardId") Long boardId,
		@Param("postId") Long postId,
		@Param("version") Integer version
	);
}