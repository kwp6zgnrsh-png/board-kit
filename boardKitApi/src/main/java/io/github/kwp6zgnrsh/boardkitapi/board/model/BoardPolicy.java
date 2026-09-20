package io.github.kwp6zgnrsh.boardkitapi.board.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class BoardPolicy {

	private Long boardId;

	// 접근 권한
	private String readPermission;
	private String writePermission;
	private String commentPermission;

	// 비밀글
	private String secretMode;

	// 카테고리
	private boolean categoriesEnabled;
	private boolean categoryRequired;

	// 첨부파일·이미지
	private boolean attachmentsEnabled;
	private String uploadProfile;
	private Integer maxFileCount;
	private Long maxFileSizeBytes;
	private boolean imageRequired;
	private boolean thumbnailEnabled;
	private String representativeImageMode;

	// 댓글·좋아요·공식 답변
	private boolean commentsEnabled;
	private boolean commentRepliesEnabled;
	private boolean postLikesEnabled;
	private boolean commentLikesEnabled;
	private boolean officialReplyEnabled;

	private Integer version;
	private LocalDateTime updatedAt;
}