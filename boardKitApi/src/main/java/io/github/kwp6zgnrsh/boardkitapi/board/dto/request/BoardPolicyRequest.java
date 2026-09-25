package io.github.kwp6zgnrsh.boardkitapi.board.dto.request;

import jakarta.validation.constraints.*;

public record BoardPolicyRequest(

	@NotBlank
	@Pattern(regexp = "PUBLIC|MEMBER|ADMIN")
	String readPermission,

	@NotBlank
	@Pattern(regexp = "MEMBER|ADMIN")
	String writePermission,

	@NotBlank
	@Pattern(regexp = "MEMBER|ADMIN")
	String commentPermission,

	@NotBlank
	@Pattern(regexp = "OFF|OPTIONAL|REQUIRED")
	String secretMode,

	@NotNull
	Boolean categoriesEnabled,

	@NotNull
	Boolean categoryRequired,

	@NotNull
	Boolean attachmentsEnabled,

	@NotBlank
	@Pattern(regexp = "IMAGE|IMAGE_AND_ZIP")
	String uploadProfile,

	@NotNull
	@PositiveOrZero
	Integer maxFileCount,

	@NotNull
	@PositiveOrZero
	Long maxFileSizeBytes,

	@NotNull
	Boolean imageRequired,

	@NotNull
	Boolean thumbnailEnabled,

	@NotBlank
	@Pattern(regexp = "FIRST_IMAGE|MANUAL")
	String representativeImageMode,

	@NotNull
	Boolean commentsEnabled,

	@NotNull
	Boolean commentRepliesEnabled,

	@NotNull
	Boolean postLikesEnabled,

	@NotNull
	Boolean commentLikesEnabled,

	@NotNull
	Boolean officialReplyEnabled

) {
}