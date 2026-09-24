package io.github.kwp6zgnrsh.boardkitapi.post.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import io.github.kwp6zgnrsh.boardkitapi.board.exception.BoardAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.board.exception.BoardNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.board.mapper.BoardMapper;
import io.github.kwp6zgnrsh.boardkitapi.board.model.Board;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardPolicy;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberSanctionedException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.converter.PostConverter;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostCreateRequest;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

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

	@Transactional
	public PostCreateResponse createPost(@NotBlank String boardSlug,
										 @NotNull @Valid PostCreateRequest request,
										 @NotNull @Positive Long authorId,
										 boolean adminAccess) {
		validateMember(authorId);

		Board board = boardMapper
			.selectActiveBoardBySlug(boardSlug)
			.orElseThrow(BoardNotFoundException::new);

		BoardPolicy policy = boardMapper
			.selectPolicyByBoardId(board.getId())
			.orElseThrow(() -> new IllegalStateException(
				"게시판 정책을 찾을 수 없습니다. boardId=" + board.getId()
			));

		validateWritePermission(policy, adminAccess);
		validateCategory(board.getId(), policy, request.categoryId());
		validateCommentsLock(policy, request.commentsLocked());

		String secretPassword = normalizeSecretPassword(
			request.secretPassword()
		);

		validateSecretPolicy(
			policy.getSecretMode(),
			request.isSecret(),
			secretPassword
		);

		validateImagePolicy(policy);

		String secretPasswordHash = encodeSecretPassword(secretPassword);

		Post post = postConverter.toPost(
			request,
			board.getId(),
			authorId,
			secretPasswordHash
		);

		int insertedCount = postMapper.insertPost(post);

		if (insertedCount != 1 || post.getId() == null) {
			throw new IllegalStateException("게시글 저장에 실패했습니다.");
		}

		return postConverter.toCreateResponse(post);
	}

	@Transactional(readOnly = true)
	public PostPageResponse getPosts(@NotBlank String boardSlug,
									 @Min(1) int page,
									 @Min(1) @Max(100) int size,
									 boolean memberAccess,
									 boolean adminAccess) {

		Board board = boardMapper
			.selectActiveBoardBySlug(boardSlug)
			.orElseThrow(BoardNotFoundException::new);

		BoardPolicy policy = boardMapper
			.selectPolicyByBoardId(board.getId())
			.orElseThrow(() -> new IllegalStateException(
				"게시판 정책을 찾을 수 없습니다. boardId=" + board.getId()
			));

		validateReadPermission(policy.getReadPermission(), memberAccess, adminAccess);

		long totalElements = postMapper.countActivePostsByBoardId(board.getId());

		long offset = (long) (page - 1) * size;

		List<PostSummaryResponse> posts;

		if (totalElements == 0 || offset >= totalElements) {
			posts = List.of();
		} else {
			posts = postMapper.selectPostSummaries(
				board.getId(),
				size,
				offset
			);
		}

		long calculatedTotalPages =
			totalElements / size + (totalElements % size == 0 ? 0 : 1);

		int totalPages = Math.toIntExact(calculatedTotalPages);

		return new PostPageResponse(
			posts,
			page,
			size,
			totalElements,
			totalPages
		);
	}

	@Transactional
	public PostDetailResponse getPostDetail(@NotBlank String boardSlug, @NotNull @Positive Long postId, Long memberId,
											boolean memberAccess, boolean adminAccess, String secretPassword) {

		Board board = boardMapper
			.selectActiveBoardBySlug(boardSlug)
			.orElseThrow(BoardNotFoundException::new);

		BoardPolicy policy = boardMapper
			.selectPolicyByBoardId(board.getId())
			.orElseThrow(() -> new IllegalStateException(
				"게시판 정책을 찾을 수 없습니다. boardId=" +
					board.getId()
			));

		validateReadPermission(policy.getReadPermission(), memberAccess, adminAccess);

		PostDetail postDetail = postMapper
			.selectActivePostDetail(board.getId(), postId)
			.orElseThrow(PostNotFoundException::new);

		validateSecretPostAccess(
			postDetail,
			memberId,
			adminAccess,
			secretPassword
		);

		int updatedCount = postMapper.increaseViewCount(
			board.getId(),
			postId
		);

		if (updatedCount != 1) {
			throw new PostNotFoundException();
		}

		long increasedViewCount = Math.addExact(postDetail.viewCount(), 1L);

		return postConverter.toDetailResponse(
			postDetail,
			increasedViewCount
		);
	}

	@Transactional
	public void updatePost(@NotBlank String boardSlug,
						   @NotNull @Positive Long postId,
						   @NotNull @Valid PostUpdateRequest request,
						   @NotNull @Positive Long memberId,
						   boolean adminAccess) {

		validateMember(memberId);

		Board board = boardMapper
			.selectActiveBoardBySlug(boardSlug)
			.orElseThrow(BoardNotFoundException::new);

		BoardPolicy policy = boardMapper
			.selectPolicyByBoardId(board.getId())
			.orElseThrow(() -> new IllegalStateException(
				"게시판 정책을 찾을 수 없습니다. boardId=" + board.getId()
			));

		validateWritePermission(policy, adminAccess);

		PostDetail currentPost = postMapper
			.selectActivePostDetail(board.getId(), postId)
			.orElseThrow(PostNotFoundException::new);

		if (!Objects.equals(currentPost.authorId(), memberId)) {
			throw new PostEditDeniedException();
		}

		validateVersion(currentPost.version(), request.version());
		validateCategory(board.getId(), policy, request.categoryId());
		validateCommentsLock(policy, request.commentsLocked());

		String newSecretPassword =
			normalizeSecretPassword(request.secretPassword());

		if (request.clearSecretPassword() && newSecretPassword != null) {
			throw new InvalidPostRequestException("비밀글 비밀번호 변경과 삭제를 동시에 요청할 수 없습니다.");
		}

		boolean retainingExistingSecret =
			"OFF".equals(policy.getSecretMode())
				&& currentPost.secret()
				&& request.isSecret();

		if (!retainingExistingSecret) {
			validateSecretPolicy(
				policy.getSecretMode(),
				request.isSecret(),
				newSecretPassword
			);
		}

		String secretPasswordHash = resolveSecretPasswordHash(
			currentPost,
			request,
			newSecretPassword
		);

		Post post = postConverter.toUpdatedPost(
			request,
			postId,
			board.getId(),
			memberId,
			secretPasswordHash
		);

		int updatedCount = postMapper.updatePost(post);

		if (updatedCount != 1) {
			throw new PostConflictException();
		}
	}

	@Transactional
	public void deletePost(@NotBlank String boardSlug,
						   @NotNull @Positive Long postId,
						   @NotNull @PositiveOrZero Integer version,
						   @NotNull @Positive Long memberId,
						   boolean adminAccess) {

		validateActiveMember(memberId);

		Board board = boardMapper
			.selectActiveBoardBySlug(boardSlug)
			.orElseThrow(BoardNotFoundException::new);

		PostDetail currentPost = postMapper
			.selectActivePostDetail(board.getId(), postId)
			.orElseThrow(PostNotFoundException::new);

		boolean author = Objects.equals(currentPost.authorId(), memberId);

		if (!author && !adminAccess) {
			throw new PostDeleteDeniedException();
		}

		validateVersion(currentPost.version(), version);

		int deletedCount = postMapper.softDeletePost(
			board.getId(),
			postId,
			version
		);

		if (deletedCount != 1) {
			throw new PostConflictException();
		}
	}

	private void validateSecretPostAccess(PostDetail postDetail, Long memberId, boolean adminAccess, String secretPassword) {
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

	private static void validateReadPermission(String readPermission, boolean memberAccess, boolean adminAccess) {
		boolean readable = switch (readPermission) {
			case "PUBLIC" -> true;
			case "MEMBER" -> memberAccess || adminAccess;
			case "ADMIN" -> adminAccess;
			default -> throw new IllegalStateException(
				"지원하지 않는 읽기 권한입니다: " +
					readPermission
			);
		};

		if (!readable) {
			throw new BoardAccessDeniedException();
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
			default -> throw new IllegalStateException(
				"지원하지 않는 작성 권한입니다: " + policy.getWritePermission()
			);
		}
	}

	private void validateCategory(Long boardId, BoardPolicy policy, Long categoryId) {
		if (!policy.isCategoriesEnabled()) {
			if (categoryId != null) {
				throw new InvalidPostRequestException(
					"카테고리를 사용하지 않는 게시판입니다."
				);
			}

			return;
		}

		if (policy.isCategoryRequired() && categoryId == null) {
			throw new InvalidPostRequestException(
				"카테고리를 선택해야 합니다."
			);
		}

		if (categoryId != null &&
			!boardMapper.existsActiveCategory(boardId, categoryId)
		) {
			throw new InvalidPostRequestException(
				"사용할 수 없는 카테고리입니다."
			);
		}
	}

	private void validateCommentsLock(BoardPolicy policy, boolean commentsLocked) {
		if (!policy.isCommentsEnabled() && commentsLocked) {
			throw new InvalidPostRequestException(
				"댓글을 사용하지 않는 게시판에서는 댓글을 잠글 수 없습니다."
			);
		}
	}

	private void validateSecretPolicy(String secretMode, boolean secret, String secretPassword) {
		switch (secretMode) {
			case "OFF" -> {
				if (secret || secretPassword != null) {
					throw new InvalidPostRequestException(
						"비밀글을 사용할 수 없는 게시판입니다."
					);
				}
			}
			case "OPTIONAL" -> {
				if (!secret && secretPassword != null) {
					throw new InvalidPostRequestException(
						"일반 게시글에는 비밀번호를 설정할 수 없습니다."
					);
				}
			}
			case "REQUIRED" -> {
				if (!secret) {
					throw new InvalidPostRequestException(
						"비밀글로 작성해야 하는 게시판입니다."
					);
				}
			}
			default -> throw new IllegalStateException(
				"지원하지 않는 비밀글 정책입니다: " + secretMode
			);
		}
	}

	private void validateImagePolicy(BoardPolicy policy) {
		if (policy.isImageRequired()) {
			throw new InvalidPostRequestException(
				"이미지 첨부가 필요한 게시판입니다."
			);
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

		int passwordBytes =
			password.getBytes(StandardCharsets.UTF_8).length;

		if (passwordBytes > BCRYPT_MAX_PASSWORD_BYTES) {
			throw new InvalidPostRequestException(
				"비밀글 비밀번호가 너무 깁니다."
			);
		}

		return passwordEncoder.encode(password);
	}

	private void validateVersion(Integer currentVersion, Integer requestVersion) {
		if (!Objects.equals(currentVersion, requestVersion)) {
			throw new PostConflictException();
		}
	}

	private String resolveSecretPasswordHash(PostDetail currentPost,
											 PostUpdateRequest request,
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
}