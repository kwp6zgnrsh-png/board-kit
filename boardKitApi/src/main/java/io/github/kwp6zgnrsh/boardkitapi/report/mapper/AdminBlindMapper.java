package io.github.kwp6zgnrsh.boardkitapi.report.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminBlindedCommentSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminBlindedContentDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminBlindedPostSummaryResponse;

@Mapper
public interface AdminBlindMapper {

	long countBlindedPosts();

	List<AdminBlindedPostSummaryResponse> selectBlindedPosts(
		@Param("limit") int limit,
		@Param("offset") long offset
	);

	long countBlindedComments();

	List<AdminBlindedCommentSummaryResponse> selectBlindedComments(
		@Param("limit") int limit,
		@Param("offset") long offset
	);

	Optional<AdminBlindedContentDetailResponse> selectBlindedPost(
		@Param("postId") Long postId
	);

	Optional<AdminBlindedContentDetailResponse> selectBlindedComment(
		@Param("commentId") Long commentId
	);
}