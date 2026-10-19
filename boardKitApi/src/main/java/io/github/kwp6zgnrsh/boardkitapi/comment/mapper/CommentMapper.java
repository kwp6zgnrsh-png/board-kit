package io.github.kwp6zgnrsh.boardkitapi.comment.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import io.github.kwp6zgnrsh.boardkitapi.comment.dto.response.CommentItemResponse;
import io.github.kwp6zgnrsh.boardkitapi.comment.model.Comment;

@Mapper
public interface CommentMapper {

	int insertComment(Comment comment);

	Optional<Long> selectActiveRootIdForUpdate(
		@Param("postId") Long postId,
		@Param("parentId") Long parentId
	);

	List<CommentItemResponse> selectActiveComments(
		@Param("postId") Long postId,
		@Param("limit") int limit,
		@Param("offset") long offset,
		@Param("memberId") Long memberId,
		@Param("adminAccess") boolean adminAccess
	);

	Optional<Comment> selectActiveComment(
		@Param("boardId") Long boardId,
		@Param("postId") Long postId,
		@Param("commentId") Long commentId
	);

	int updateComment(
		@Param("boardId") Long boardId,
		@Param("postId") Long postId,
		@Param("commentId") Long commentId,
		@Param("authorId") Long authorId,
		@Param("content") String content,
		@Param("version") Integer version
	);

	long countActiveComments(
		@Param("postId") Long postId
	);

	int softDeleteComment(
		@Param("boardId") Long boardId,
		@Param("postId") Long postId,
		@Param("commentId") Long commentId,
		@Param("version") Integer version
	);

	Optional<Long> selectActiveLikeTargetForShare(
		@Param("boardId") Long boardId,
		@Param("postId") Long postId,
		@Param("commentId") Long commentId
	);

	Optional<Comment> selectCommentForModerationForUpdate(
		@Param("postId") Long postId,
		@Param("commentId") Long commentId
	);

	int blindComment(
		@Param("postId") Long postId,
		@Param("commentId") Long commentId,
		@Param("version") Integer version
	);

	int unblindComment(
		@Param("postId") Long postId,
		@Param("commentId") Long commentId,
		@Param("version") Integer version,
		@Param("acceptedReportCount") long acceptedReportCount
	);
}