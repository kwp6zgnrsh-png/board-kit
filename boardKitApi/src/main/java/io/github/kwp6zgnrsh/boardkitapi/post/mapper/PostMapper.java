package io.github.kwp6zgnrsh.boardkitapi.post.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostSort;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.model.Post;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostCommentTarget;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostDetail;
import tools.jackson.databind.JsonNode;

@Mapper
public interface PostMapper {

	int insertPost(Post post);

	List<PostSummaryResponse> selectPostSummaries(
		@Param("boardId") Long boardId,
		@Param("keyword") String keyword,
		@Param("categoryId") Long categoryId,
		@Param("sort") PostSort sort,
		@Param("limit") int limit,
		@Param("offset") long offset,
		@Param("memberId") Long memberId,
		@Param("adminAccess") boolean adminAccess
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

	Optional<PostCommentTarget> selectActiveCommentTargetForUpdate(
		@Param("boardId") Long boardId,
		@Param("postId") Long postId
	);

	Optional<PostCommentTarget> selectActiveCommentTarget(
		@Param("boardId") Long boardId,
		@Param("postId") Long postId
	);

	int refreshRepresentativeImage(
		@Param("boardId") Long boardId,
		@Param("postId") Long postId
	);

	int updateContentDocument(
		@Param("boardId") Long boardId,
		@Param("postId") Long postId,
		@Param("contentDocument") JsonNode contentDocument
	);

	Optional<Post> selectPostForModerationForUpdate(@Param("postId") Long postId);

	int blindPost(
		@Param("postId") Long postId,
		@Param("version") Integer version
	);

	int unblindPost(
		@Param("postId") Long postId,
		@Param("version") Integer version,
		@Param("acceptedReportCount") long acceptedReportCount
	);

	Optional<PostCommentTarget> selectActiveCommentTargetForShare(
		@Param("boardId") Long boardId,
		@Param("postId") Long postId
	);
}