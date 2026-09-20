package io.github.kwp6zgnrsh.boardkitapi.post.mapper;

import io.github.kwp6zgnrsh.boardkitapi.post.model.Post;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PostMapper {

	int insertPost(Post post);
}