package io.github.kwp6zgnrsh.boardkitapi.post.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import io.github.kwp6zgnrsh.boardkitapi.board.exception.BoardNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.board.mapper.BoardMapper;
import io.github.kwp6zgnrsh.boardkitapi.board.model.Board;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardPolicy;
import io.github.kwp6zgnrsh.boardkitapi.board.policy.BoardReadAccess;
import io.github.kwp6zgnrsh.boardkitapi.file.dto.response.PostFileResponse;
import io.github.kwp6zgnrsh.boardkitapi.file.exception.PostFileNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.file.mapper.FileCleanupTaskMapper;
import io.github.kwp6zgnrsh.boardkitapi.file.mapper.PostFileMapper;
import io.github.kwp6zgnrsh.boardkitapi.file.model.PostFile;
import io.github.kwp6zgnrsh.boardkitapi.file.model.PostFileContent;
import io.github.kwp6zgnrsh.boardkitapi.file.model.StoredPostFile;
import io.github.kwp6zgnrsh.boardkitapi.file.model.TempStoredFile;
import io.github.kwp6zgnrsh.boardkitapi.file.model.ValidatedUploadFile;
import io.github.kwp6zgnrsh.boardkitapi.file.service.FileUploadValidator;
import io.github.kwp6zgnrsh.boardkitapi.file.service.LocalFileStorage;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberSanctionedException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.converter.PostConverter;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostListRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.InvalidPostRequestException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostConflictException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostDeleteDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostEditDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostWriteDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.SecretPostAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.model.Post;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostDetail;
import io.github.kwp6zgnrsh.boardkitapi.post.service.PostDocumentValidator.ImageReference;
import io.github.kwp6zgnrsh.boardkitapi.post.service.PostDocumentValidator.ValidatedDocument;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Service
@Validated
@RequiredArgsConstructor
public class PostService {

	private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

	private final PostMapper postMapper;
	private final BoardMapper boardMapper;
	private final MemberMapper memberMapper;
	private final PostConverter postConverter;
	private final PasswordEncoder passwordEncoder;
	private final PostFileMapper postFileMapper;
	private final FileUploadValidator fileUploadValidator;
	private final LocalFileStorage localFileStorage;
	private final FileCleanupTaskMapper fileCleanupTaskMapper;
	private final PostDocumentValidator postDocumentValidator;

	@Transactional
	public PostCreateResponse createPost(@NotBlank String boardSlug, @NotNull @Valid PostCreateRequest request,
		@NotNull List<MultipartFile> files, @NotNull @Positive Long authorId, boolean adminAccess) {

		PostWriteContext context = requirePostWriteContext(boardSlug, authorId, adminAccess);
		Board board = context.board();
		BoardPolicy policy = context.policy();

		validateCategory(board.getId(), policy, request.categoryId());
		validateCommentsLock(policy, request.commentsLocked());

		String secretPassword = normalizeSecretPassword(request.secretPassword());
		validateSecretPolicy(policy.getSecretMode(), request.isSecret(), secretPassword);

		ValidatedDocument document = validateDocument(request.contentDocument(), policy);

		if (document.text().isBlank() && document.images().isEmpty()) {
			throw new InvalidPostRequestException("본문에 내용이나 이미지를 넣어 주세요.");
		}
		if (policy.isImageRequired() && document.images().isEmpty()) {
			throw new InvalidPostRequestException("본문에 이미지를 한 개 이상 넣어 주세요.");
		}

		List<ValidatedUploadFile> validatedFiles = fileUploadValidator.validate(files, policy);
		Map<Integer, Integer> bodyImageOrder = validateBodyImages(document, validatedFiles, Map.of());

		String secretPasswordHash = encodeSecretPassword(secretPassword);
		Post post = postConverter.toPost(request, board.getId(), authorId, secretPasswordHash, document);

		if (postMapper.insertPost(post) != 1 || post.getId() == null) {
			throw new IllegalStateException("게시글 저장에 실패했습니다.");
		}

		if (validatedFiles.isEmpty()) {
			return postConverter.toCreateResponse(post);
		}

		List<StoredPostFile> storedFiles = List.of();

		try {
			List<TempStoredFile> temporaryFiles = localFileStorage.storeTemporary(validatedFiles);
			storedFiles = localFileStorage.moveToPost(post.getId(), temporaryFiles, policy.isThumbnailEnabled());

			if (storedFiles.size() != validatedFiles.size()) {
				throw new IllegalStateException("첨부파일 저장에 실패했습니다.");
			}

			List<PostFile> postFiles = createPostFiles(post.getId(), storedFiles, bodyImageOrder,
				document.images().size());

			if (postFileMapper.insertPostFiles(postFiles) != postFiles.size()) {
				throw new IllegalStateException("첨부파일 정보 저장에 실패했습니다.");
			}

			if (!bodyImageOrder.isEmpty()) {
				JsonNode resolvedDocument = resolveDocumentFiles(document.document(), storedFiles, postFiles);

				if (postMapper.updateContentDocument(board.getId(), post.getId(), resolvedDocument) != 1) {
					throw new IllegalStateException("본문 이미지 저장에 실패했습니다.");
				}

				if (postMapper.refreshRepresentativeImage(board.getId(), post.getId()) != 1) {
					throw new IllegalStateException("대표 이미지 지정에 실패했습니다.");
				}
			}

			return postConverter.toCreateResponse(post);
		} catch (RuntimeException exception) {
			deleteStoredFilesQuietly(storedFiles);
			throw exception;
		}
	}

	@Transactional(readOnly = true)
	public PostPageResponse getPosts(@NotBlank String boardSlug, @Min(1) int page, @Min(1) @Max(100) int size,
		@NotNull @Valid PostListRequest request, boolean memberAccess, boolean adminAccess) {

		Board board = boardMapper.selectActiveBoardBySlug(boardSlug).orElseThrow(BoardNotFoundException::new);

		BoardPolicy policy = boardMapper.selectPolicyByBoardId(board.getId())
			.orElseThrow(() -> new IllegalStateException("게시판 정책을 찾을 수 없습니다. boardId=" + board.getId()));

		BoardReadAccess.validate(policy.getReadPermission(), memberAccess, adminAccess);

		validateCategoryFilter(board.getId(), policy, request.categoryId());

		String keyword = escapeLikeKeyword(request.keyword());

		long totalElements = postMapper.countPostSummaries(board.getId(), keyword, request.categoryId());

		long offset = (long)(page - 1) * size;

		List<PostSummaryResponse> posts;

		if (totalElements == 0 || offset >= totalElements) {
			posts = List.of();
		} else {
			posts = postMapper.selectPostSummaries(board.getId(), keyword, request.categoryId(), request.sort(), size,
				offset);
		}

		long calculatedTotalPages = totalElements / size + (totalElements % size == 0 ? 0 : 1);

		return new PostPageResponse(posts, page, size, totalElements, Math.toIntExact(calculatedTotalPages));
	}

	@Transactional
	public PostDetailResponse getPostDetail(@NotBlank String boardSlug, @NotNull @Positive Long postId, Long memberId,
		boolean memberAccess, boolean adminAccess, String secretPassword) {

		PostDetail postDetail = requireReadablePost(boardSlug, postId, memberId, memberAccess, adminAccess,
			secretPassword);

		int updatedCount = postMapper.increaseViewCount(postDetail.boardId(), postId);

		if (updatedCount != 1) {
			throw new PostNotFoundException();
		}

		long increasedViewCount = Math.addExact(postDetail.viewCount(), 1L);

		List<PostFileResponse> files = postFileMapper.selectActivePostFiles(postId);

		return postConverter.toDetailResponse(postDetail, increasedViewCount, files);
	}

	@Transactional
	public void updatePost(@NotBlank String boardSlug, @NotNull @Positive Long postId,
		@NotNull @Valid PostUpdateRequest request, List<MultipartFile> files, @NotNull @Positive Long memberId,
		boolean adminAccess) {

		PostWriteContext context = requirePostWriteContext(boardSlug, memberId, adminAccess);
		Board board = context.board();
		BoardPolicy policy = context.policy();

		PostDetail currentPost = postMapper.selectActivePostDetail(board.getId(), postId)
			.orElseThrow(PostNotFoundException::new);

		if (!Objects.equals(currentPost.authorId(), memberId)) {
			throw new PostEditDeniedException();
		}

		validateVersion(currentPost.version(), request.version());
		validateCategory(board.getId(), policy, request.categoryId());
		validateCommentsLock(policy, request.commentsLocked());

		String newSecretPassword = normalizeSecretPassword(request.secretPassword());

		if (request.clearSecretPassword() && newSecretPassword != null) {
			throw new InvalidPostRequestException("비밀글 비밀번호 변경과 삭제를 동시에 요청할 수 없습니다.");
		}

		boolean retainingExistingSecret =
			"OFF".equals(policy.getSecretMode()) && currentPost.secret() && request.isSecret();

		if (!retainingExistingSecret) {
			validateSecretPolicy(policy.getSecretMode(), request.isSecret(), newSecretPassword);
		}

		String secretPasswordHash = resolveSecretPasswordHash(currentPost, request, newSecretPassword);

		ValidatedDocument document = validateDocument(request.contentDocument(), policy);

		Post post = postConverter.toUpdatedPost(request, postId, board.getId(), memberId, secretPasswordHash, document);

		if (postMapper.updatePost(post) != 1) {
			throw new PostConflictException();
		}

		updatePostFiles(board.getId(), postId, policy, request, files, document);
	}

	@Transactional
	public void deletePost(@NotBlank String boardSlug, @NotNull @Positive Long postId,
		@NotNull @PositiveOrZero Integer version, @NotNull @Positive Long memberId, boolean adminAccess) {

		validateActiveMember(memberId);

		Board board = boardMapper.selectActiveBoardBySlug(boardSlug).orElseThrow(BoardNotFoundException::new);

		PostDetail currentPost = postMapper.selectActivePostDetail(board.getId(), postId)
			.orElseThrow(PostNotFoundException::new);

		boolean author = Objects.equals(currentPost.authorId(), memberId);

		if (!author && !adminAccess) {
			throw new PostDeleteDeniedException();
		}

		validateVersion(currentPost.version(), version);

		int deletedCount = postMapper.softDeletePost(board.getId(), postId, version);

		if (deletedCount != 1) {
			throw new PostConflictException();
		}

		List<PostFile> files = postFileMapper.selectActivePostFilesForUpdate(postId);
		postFileMapper.softDeleteAllPostFiles(postId);

		List<String> targetKeys = new ArrayList<>();
		for (PostFile file : files) {
			targetKeys.add(file.getStorageKey());
			if (file.getThumbnailKey() != null) {
				targetKeys.add(file.getThumbnailKey());
			}
		}

		if (!targetKeys.isEmpty()) {
			fileCleanupTaskMapper.enqueueDeletes(targetKeys);
		}
	}

	@Transactional(readOnly = true)
	public PostFileContent getPostFile(String boardSlug, Long postId, Long fileId, boolean thumbnail, Long memberId,
		boolean memberAccess, boolean adminAccess, String secretPassword) {

		requireReadablePost(boardSlug, postId, memberId, memberAccess, adminAccess, secretPassword);

		PostFile file = postFileMapper.selectActivePostFile(postId, fileId).orElseThrow(PostFileNotFoundException::new);

		String storageKey = thumbnail ? file.getThumbnailKey() : file.getStorageKey();

		if (storageKey == null) {
			throw new PostFileNotFoundException();
		}

		return new PostFileContent(localFileStorage.load(storageKey), thumbnail ? "image/png" : file.getContentType(),
			file.getOriginalName(), !thumbnail && "ATTACHMENT".equals(file.getFileUsage()));
	}

	private void validateSecretPostAccess(PostDetail postDetail, Long memberId, boolean adminAccess,
		String secretPassword) {
		if (!postDetail.secret() || postDetail.secretPasswordHash() == null) {
			return;
		}

		if (adminAccess || Objects.equals(postDetail.authorId(), memberId)) {
			return;
		}

		String normalizedPassword = normalizeSecretPassword(secretPassword);

		if (normalizedPassword == null
			|| normalizedPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES
			|| !passwordEncoder.matches(normalizedPassword, postDetail.secretPasswordHash())) {
			throw new SecretPostAccessDeniedException();
		}
	}

	private void validateMember(Long memberId) {
		validateActiveMember(memberId);

		if (memberMapper.existsActiveSanctionByMemberId(memberId)) {
			throw new MemberSanctionedException();
		}
	}

	private void validateWritePermission(BoardPolicy policy, boolean adminAccess) {
		switch (policy.getWritePermission()) {
			case "MEMBER" -> {
				// 인증된 회원과 관리자 모두 작성할 수 있다.
			}
			case "ADMIN" -> {
				if (!adminAccess) {
					throw new PostWriteDeniedException();
				}
			}
			default -> throw new IllegalStateException("지원하지 않는 작성 권한입니다: " + policy.getWritePermission());
		}
	}

	private PostDetail requireReadablePost(String boardSlug, Long postId, Long memberId, boolean memberAccess,
		boolean adminAccess, String secretPassword) {

		Board board = boardMapper.selectActiveBoardBySlug(boardSlug).orElseThrow(BoardNotFoundException::new);
		BoardPolicy policy = boardMapper.selectPolicyByBoardId(board.getId())
			.orElseThrow(() -> new IllegalStateException("게시판 정책을 찾을 수 없습니다. boardId=" + board.getId()));

		BoardReadAccess.validate(policy.getReadPermission(), memberAccess, adminAccess);

		PostDetail post = postMapper.selectActivePostDetail(board.getId(), postId)
			.orElseThrow(PostNotFoundException::new);

		validateSecretPostAccess(post, memberId, adminAccess, secretPassword);

		return post;
	}

	private void validateCategory(Long boardId, BoardPolicy policy, Long categoryId) {
		if (!policy.isCategoriesEnabled()) {
			if (categoryId != null) {
				throw new InvalidPostRequestException("카테고리를 사용하지 않는 게시판입니다.");
			}

			return;
		}

		if (policy.isCategoryRequired() && categoryId == null) {
			throw new InvalidPostRequestException("카테고리를 선택해야 합니다.");
		}

		if (categoryId != null && !boardMapper.existsActiveCategory(boardId, categoryId)) {
			throw new InvalidPostRequestException("사용할 수 없는 카테고리입니다.");
		}
	}

	private void validateCommentsLock(BoardPolicy policy, boolean commentsLocked) {
		if (!policy.isCommentsEnabled() && commentsLocked) {
			throw new InvalidPostRequestException("댓글을 사용하지 않는 게시판에서는 댓글을 잠글 수 없습니다.");
		}
	}

	private void validateSecretPolicy(String secretMode, boolean secret, String secretPassword) {
		switch (secretMode) {
			case "OFF" -> {
				if (secret || secretPassword != null) {
					throw new InvalidPostRequestException("비밀글을 사용할 수 없는 게시판입니다.");
				}
			}
			case "OPTIONAL" -> {
				if (!secret && secretPassword != null) {
					throw new InvalidPostRequestException("일반 게시글에는 비밀번호를 설정할 수 없습니다.");
				}
			}
			case "REQUIRED" -> {
				if (!secret) {
					throw new InvalidPostRequestException("비밀글로 작성해야 하는 게시판입니다.");
				}
			}
			default -> throw new IllegalStateException("지원하지 않는 비밀글 정책입니다: " + secretMode);
		}
	}

	private String normalizeSecretPassword(String password) {
		if (password == null || password.isBlank()) {
			return null;
		}

		return password;
	}

	private String encodeSecretPassword(String password) {
		if (password == null) {
			return null;
		}

		int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;

		if (passwordBytes > BCRYPT_MAX_PASSWORD_BYTES) {
			throw new InvalidPostRequestException("비밀글 비밀번호가 너무 깁니다.");
		}

		return passwordEncoder.encode(password);
	}

	private void validateVersion(Integer currentVersion, Integer requestVersion) {
		if (!Objects.equals(currentVersion, requestVersion)) {
			throw new PostConflictException();
		}
	}

	private String resolveSecretPasswordHash(PostDetail currentPost, PostUpdateRequest request,
		String newSecretPassword) {

		if (!request.isSecret() || request.clearSecretPassword()) {
			return null;
		}

		if (newSecretPassword == null) {
			return currentPost.secretPasswordHash();
		}

		return encodeSecretPassword(newSecretPassword);
	}

	private void validateActiveMember(Long memberId) {
		if (!memberMapper.existsActiveMemberById(memberId)) {
			throw new MemberUnavailableException();
		}
	}

	private PostWriteContext requirePostWriteContext(String boardSlug, Long memberId, boolean adminAccess) {
		validateMember(memberId);

		Board board = boardMapper.selectActiveBoardBySlug(boardSlug).orElseThrow(BoardNotFoundException::new);

		BoardPolicy policy = boardMapper.selectPolicyByBoardId(board.getId())
			.orElseThrow(() -> new IllegalStateException("게시판 정책을 찾을 수 없습니다. boardId=" + board.getId()));

		BoardReadAccess.validate(policy.getReadPermission(), true, adminAccess);

		validateWritePermission(policy, adminAccess);

		return new PostWriteContext(board, policy);
	}

	private void validateCategoryFilter(Long boardId, BoardPolicy policy, Long categoryId) {
		if (categoryId == null) {
			return;
		}

		if (!policy.isCategoriesEnabled()) {
			throw new InvalidPostRequestException("카테고리 필터를 사용할 수 없는 게시판입니다.");
		}

		if (!boardMapper.existsActiveCategory(boardId, categoryId)) {
			throw new InvalidPostRequestException("사용할 수 없는 카테고리입니다.");
		}
	}

	private String escapeLikeKeyword(String keyword) {
		if (keyword == null) {
			return null;
		}

		return keyword.replace("=", "==").replace("%", "=%").replace("_", "=_");
	}

	private List<PostFile> createPostFiles(Long postId, List<StoredPostFile> storedFiles,
		Map<Integer, Integer> bodyImageOrder, int attachmentStartOrder) {

		List<PostFile> postFiles = new ArrayList<>(storedFiles.size());
		Set<Integer> indexes = new HashSet<>();
		int attachmentOrder = attachmentStartOrder;

		for (StoredPostFile stored : storedFiles) {
			int index = stored.sortOrder();

			if (index < 0 || index >= storedFiles.size() || !indexes.add(index)) {
				throw new IllegalStateException("저장된 첨부파일 순서가 올바르지 않습니다.");
			}

			Integer imageOrder = bodyImageOrder.get(index);

			postFiles.add(PostFile.builder()
				.postId(postId)
				.originalName(stored.originalName())
				.storageKey(stored.storageKey())
				.contentType(stored.contentType())
				.fileUsage(imageOrder == null ? "ATTACHMENT" : "BODY_IMAGE")
				.sizeBytes(stored.sizeBytes())
				.thumbnailKey(stored.thumbnailKey())
				.sortOrder(imageOrder == null ? attachmentOrder++ : imageOrder)
				.build());
		}

		return postFiles;
	}

	private void deleteStoredFilesQuietly(List<StoredPostFile> storedFiles) {
		for (StoredPostFile file : storedFiles) {
			deleteFileQuietly(file.thumbnailKey());
			deleteFileQuietly(file.storageKey());
		}
	}

	private void deleteFileQuietly(String storageKey) {
		if (storageKey == null) {
			return;
		}
		try {
			localFileStorage.delete(storageKey);
		} catch (RuntimeException ignored) {
			// 남은 파일은 고아 파일 스캔 작업에서 정리한다.
		}
	}

	private void updatePostFiles(Long boardId, Long postId, BoardPolicy policy, PostUpdateRequest request,
		List<MultipartFile> files, ValidatedDocument document) {

		if (files == null || request.retainedFileIds() == null) {
			throw new InvalidPostRequestException("첨부파일 정보가 누락되었습니다.");
		}

		List<PostFile> activeFiles = postFileMapper.selectActivePostFilesForUpdate(postId);
		Map<Long, PostFile> activeById = new HashMap<>();
		for (PostFile file : activeFiles) {
			activeById.put(file.getId(), file);
		}

		Map<Long, PostFile> retainedFiles = new HashMap<>();
		long retainedSize = 0L;

		for (Long id : request.retainedFileIds()) {
			PostFile file = id == null ? null : activeById.get(id);

			if (file == null || retainedFiles.putIfAbsent(id, file) != null) {
				throw new InvalidPostRequestException("사용할 수 없는 첨부파일이 있습니다.");
			}
			if (!files.isEmpty()) {
				retainedSize = Math.addExact(retainedSize, file.getSizeBytes());
			}
		}

		List<ValidatedUploadFile> newFiles = fileUploadValidator.validateWithRetainedFiles(files, policy,
			retainedFiles.size(), retainedSize);

		Map<Integer, Integer> newBodyImageOrder = validateBodyImages(document, newFiles, retainedFiles);

		Set<Long> existingBodyImageIds = new HashSet<>();
		for (var image : document.images()) {
			if (image.fileId() != null) {
				existingBodyImageIds.add(image.fileId());
			}
		}

		List<PostFile> removedFiles = activeFiles.stream()
			.filter(file -> !retainedFiles.containsKey(file.getId()))
			.toList();

		int bodyImageCount = document.images().size();
		int retainedAttachmentCount = retainedFiles.size() - existingBodyImageIds.size();
		List<StoredPostFile> storedFiles = List.of();

		try {
			if (!newFiles.isEmpty()) {
				List<TempStoredFile> temporaryFiles = localFileStorage.storeTemporary(newFiles);
				storedFiles = localFileStorage.moveToPost(postId, temporaryFiles, policy.isThumbnailEnabled());

				if (storedFiles.size() != newFiles.size()) {
					throw new IllegalStateException("첨부파일 저장에 실패했습니다.");
				}

				List<PostFile> newRows = createPostFiles(postId, storedFiles, newBodyImageOrder,
					bodyImageCount + retainedAttachmentCount);

				if (postFileMapper.insertPostFiles(newRows) != newRows.size()) {
					throw new IllegalStateException("첨부파일 정보 저장에 실패했습니다.");
				}

				if (!newBodyImageOrder.isEmpty()) {
					JsonNode resolvedDocument = resolveDocumentFiles(document.document(), storedFiles, newRows);

					if (postMapper.updateContentDocument(boardId, postId, resolvedDocument) != 1) {
						throw new IllegalStateException("본문 이미지 저장에 실패했습니다.");
					}
				}
			}

			for (int order = 0; order < document.images().size(); order++) {
				var image = document.images().get(order);

				if (image.fileId() != null) {
					postFileMapper.updateFileMetadata(postId, image.fileId(), "BODY_IMAGE", order);
				}
			}

			int attachmentOrder = bodyImageCount;
			for (Long id : request.retainedFileIds()) {
				if (!existingBodyImageIds.contains(id)) {
					postFileMapper.updateFileMetadata(postId, id, "ATTACHMENT", attachmentOrder++);
				}
			}

			if (!removedFiles.isEmpty()) {
				List<Long> removedIds = removedFiles.stream().map(PostFile::getId).toList();

				if (postFileMapper.softDeletePostFiles(postId, removedIds) != removedIds.size()) {
					throw new PostConflictException();
				}

				List<String> targetKeys = new ArrayList<>();
				for (PostFile file : removedFiles) {
					targetKeys.add(file.getStorageKey());
					if (file.getThumbnailKey() != null) {
						targetKeys.add(file.getThumbnailKey());
					}
				}
				fileCleanupTaskMapper.enqueueDeletes(targetKeys);
			}

			postMapper.refreshRepresentativeImage(boardId, postId);
		} catch (RuntimeException exception) {
			deleteStoredFilesQuietly(storedFiles);
			throw exception;
		}
	}

	private Map<Integer, Integer> validateBodyImages(ValidatedDocument document, List<ValidatedUploadFile> newFiles,
		Map<Long, PostFile> retainedFiles) {

		Map<Integer, Integer> orderByIndex = new HashMap<>();

		for (int order = 0; order < document.images().size(); order++) {
			ImageReference image = document.images().get(order);

			if (image.fileId() != null) {
				PostFile file = retainedFiles.get(image.fileId());

				if (file == null || !file.getContentType().startsWith("image/")) {
					throw new InvalidPostRequestException("본문 이미지를 확인할 수 없습니다. 이미지를 다시 선택해 주세요.");
				}
			} else {
				int index = image.fileIndex();

				if (index >= newFiles.size() || !newFiles.get(index).isImage()) {
					throw new InvalidPostRequestException("본문 이미지를 확인할 수 없습니다. 이미지를 다시 첨부해 주세요.");
				}
				orderByIndex.put(index, order);
			}
		}

		return orderByIndex;
	}

	private JsonNode resolveDocumentFiles(JsonNode document, List<StoredPostFile> storedFiles,
		List<PostFile> postFiles) {
		Map<Integer, Long> idByIndex = new HashMap<>();

		for (int index = 0; index < postFiles.size(); index++) {
			Long fileId = postFiles.get(index).getId();

			if (fileId == null || fileId <= 0) {
				throw new IllegalStateException("첨부파일 ID를 확인할 수 없습니다.");
			}

			idByIndex.put(storedFiles.get(index).sortOrder(), fileId);
		}

		JsonNode resolved = document.deepCopy();
		replaceDocumentFileIndexes(resolved, idByIndex);
		return resolved;
	}

	private void replaceDocumentFileIndexes(JsonNode node, Map<Integer, Long> idByIndex) {
		if ("image".equals(node.path("type").asString(""))) {
			if (node.path("attrs").hasNonNull("fileId")) {
				return;
			}

			int fileIndex = node.path("attrs").path("fileIndex").intValue();
			Long fileId = idByIndex.get(fileIndex);

			if (fileId == null) {
				throw new IllegalStateException("본문 이미지의 파일 ID를 확인할 수 없습니다.");
			}

			((ObjectNode)node).putObject("attrs").put("fileId", fileId);
			return;
		}

		for (JsonNode child : node.path("content")) {
			replaceDocumentFileIndexes(child, idByIndex);
		}
	}

	private ValidatedDocument validateDocument(JsonNode source, BoardPolicy policy) {
		ValidatedDocument document = postDocumentValidator.validate(source);

		if (document.text().isBlank() && document.images().isEmpty()) {
			throw new InvalidPostRequestException("본문에 내용이나 이미지를 넣어 주세요.");
		}
		if (policy.isImageRequired() && document.images().isEmpty()) {
			throw new InvalidPostRequestException("본문에 이미지를 한 개 이상 넣어 주세요.");
		}

		return document;
	}

	private record PostWriteContext(Board board, BoardPolicy policy) {
	}
}