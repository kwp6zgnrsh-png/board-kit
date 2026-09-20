package io.github.kwp6zgnrsh.boardkitapi.board.dto.response;

public record BoardPolicyResponse(
	String readPermission,
	String writePermission,
	String commentPermission,
	String secretMode,

	boolean categoriesEnabled,
	boolean categoryRequired,

	boolean attachmentsEnabled,
	String uploadProfile,
	Integer maxFileCount,
	Long maxFileSizeBytes,
	boolean imageRequired,
	boolean thumbnailEnabled,
	String representativeImageMode,

	boolean commentsEnabled,
	boolean commentRepliesEnabled,
	boolean postLikesEnabled,
	boolean commentLikesEnabled,
	boolean officialReplyEnabled
) {
}