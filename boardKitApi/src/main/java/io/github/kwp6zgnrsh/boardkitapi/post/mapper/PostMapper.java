package io.github.kwp6zgnrsh.boardkitapi.post.mapper;

import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.model.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
}