package io.github.kwp6zgnrsh.boardkitapi.post.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PostLikeMapper {

	void insertPostLike(@Param("postId") Long postId, @Param("memberId") Long memberId);

	void deletePostLike(@Param("postId") Long postId, @Param("memberId") Long memberId);

	long countPostLikes(@Param("postId") Long postId);

	boolean existsPostLike(@Param("postId") Long postId, @Param("memberId") Long memberId);
}