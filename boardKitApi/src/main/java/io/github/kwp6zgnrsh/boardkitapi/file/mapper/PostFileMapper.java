package io.github.kwp6zgnrsh.boardkitapi.file.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import io.github.kwp6zgnrsh.boardkitapi.file.dto.response.PostFileResponse;
import io.github.kwp6zgnrsh.boardkitapi.file.model.PostFile;

@Mapper
public interface PostFileMapper {

	int insertPostFiles(@Param("files") List<PostFile> files);

	List<PostFileResponse> selectActivePostFiles(@Param("postId") Long postId);

	Optional<PostFile> selectActivePostFile(
		@Param("postId") Long postId,
		@Param("fileId") Long fileId
	);

	List<PostFile> selectActivePostFilesForUpdate(@Param("postId") Long postId);

	void updateFileMetadata(
		@Param("postId") Long postId,
		@Param("fileId") Long fileId,
		@Param("fileUsage") String fileUsage,
		@Param("sortOrder") int sortOrder
	);

	int softDeletePostFiles(
		@Param("postId") Long postId,
		@Param("fileIds") List<Long> fileIds
	);

	void softDeleteAllPostFiles(@Param("postId") Long postId);
}