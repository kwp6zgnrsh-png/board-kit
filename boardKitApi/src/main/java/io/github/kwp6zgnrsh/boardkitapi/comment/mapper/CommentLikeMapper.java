package io.github.kwp6zgnrsh.boardkitapi.comment.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommentLikeMapper {

	void insertCommentLike(
		@Param("commentId") Long commentId,
		@Param("memberId") Long memberId
	);

	void deleteCommentLike(
		@Param("commentId") Long commentId,
		@Param("memberId") Long memberId
	);
}