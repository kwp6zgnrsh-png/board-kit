package io.github.kwp6zgnrsh.boardkitapi.post.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

import io.github.kwp6zgnrsh.boardkitapi.board.exception.BoardAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.board.mapper.BoardMapper;
import io.github.kwp6zgnrsh.boardkitapi.board.model.Board;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardPolicy;
import io.github.kwp6zgnrsh.boardkitapi.file.config.FileStorageProperties;
import io.github.kwp6zgnrsh.boardkitapi.file.mapper.FileCleanupTaskMapper;
import io.github.kwp6zgnrsh.boardkitapi.file.mapper.PostFileMapper;
import io.github.kwp6zgnrsh.boardkitapi.file.model.FileFormat;
import io.github.kwp6zgnrsh.boardkitapi.file.model.PostFile;
import io.github.kwp6zgnrsh.boardkitapi.file.model.PostFileContent;
import io.github.kwp6zgnrsh.boardkitapi.file.model.StoredPostFile;
import io.github.kwp6zgnrsh.boardkitapi.file.model.TempStoredFile;
import io.github.kwp6zgnrsh.boardkitapi.file.service.FileSignatureDetector;
import io.github.kwp6zgnrsh.boardkitapi.file.service.FileUploadValidator;
import io.github.kwp6zgnrsh.boardkitapi.file.service.LocalFileStorage;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberSanctionedException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.converter.PostConverter;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostListRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostSort;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.InvalidPostRequestException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostConflictException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostDeleteDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostEditDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostWriteDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.SecretPostAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostLikeMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.model.Post;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostDetail;
import io.github.kwp6zgnrsh.boardkitapi.post.policy.PostSecretAccess;
import io.github.kwp6zgnrsh.boardkitapi.post.service.PostDocumentValidator.ValidatedDocument;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

	@Mock
	private PostMapper postMapper;

	@Mock
	private BoardMapper boardMapper;

	@Mock
	private MemberMapper memberMapper;

	@Mock
	private PostConverter postConverter;

	@Mock
	private PasswordEncoder passwordEncoder;

	private PostService postService;

	@Mock
	private PostFileMapper postFileMapper;

	@Mock
	private LocalFileStorage localFileStorage;

	@Mock
	private PostLikeMapper postLikeMapper;

	private final FileStorageProperties properties = new FileStorageProperties(Path.of("build/test-store"), 20,
		DataSize.ofMegabytes(20), DataSize.ofMegabytes(50), Duration.ofHours(24));

	@Mock
	private FileCleanupTaskMapper fileCleanupTaskMapper;

	@BeforeEach
	void setUp() {
		postService = new PostService(
			postMapper, boardMapper, memberMapper, postConverter, passwordEncoder,
			postFileMapper,
			new FileUploadValidator(properties, new FileSignatureDetector()),
			localFileStorage, fileCleanupTaskMapper, new PostDocumentValidator(),
			postLikeMapper, new PostSecretAccess(passwordEncoder)
		);
	}

	@Test
	@DisplayName("유효한 요청이면 게시글을 저장한다")
	void createPostWithValidRequest() {
		PostCreateRequest request = request(false, null);
		BoardPolicy policy = policy("MEMBER", "OPTIONAL", false, false);
		Post post = Post.builder().id(100L).boardId(1L).authorId(10L).title("제목").content("본문").build();

		allowActiveMember();
		prepareBoard("free", policy);

		when(postConverter.toPost(eq(request), eq(1L), eq(10L), isNull(), any(ValidatedDocument.class))).thenReturn(
			post);
		when(postMapper.insertPost(post)).thenReturn(1);
		when(postConverter.toCreateResponse(post)).thenReturn(new PostCreateResponse(100L));

		PostCreateResponse response = postService.createPost("free", request, List.of(), 10L, false);

		assertEquals(100L, response.id());
		verify(postMapper).insertPost(post);
	}

	@Test
	@DisplayName("제재 중인 회원은 게시글을 작성할 수 없다")
	void rejectPostCreationForSanctionedMember() {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);
		when(memberMapper.existsActiveSanctionByMemberId(10L)).thenReturn(true);

		assertThrows(MemberSanctionedException.class,
			() -> postService.createPost("free", request(false, null), List.of(), 10L, false));

		verifyNoInteractions(boardMapper, postMapper, postConverter);
	}

	@Test
	@DisplayName("관리자 전용 게시판에는 일반 회원이 글을 작성할 수 없다")
	void rejectMemberWhenBoardRequiresAdmin() {
		allowActiveMember();
		prepareBoard("admin", policy("ADMIN", "OFF", false, false));

		assertThrows(PostWriteDeniedException.class,
			() -> postService.createPost("admin", request(false, null), List.of(), 10L, false));

		verifyNoInteractions(postMapper, postConverter);
	}

	@Test
	@DisplayName("필수 카테고리를 선택하지 않으면 게시글을 작성할 수 없다")
	void rejectPostWhenRequiredCategoryIsMissing() {
		allowActiveMember();
		prepareBoard("free", policy("MEMBER", "OFF", true, true));

		assertThrows(InvalidPostRequestException.class,
			() -> postService.createPost("free", request(false, null), List.of(), 10L, false));

		verifyNoInteractions(postMapper, postConverter);
	}

	@Test
	@DisplayName("비밀글을 지원하지 않는 게시판에는 비밀글을 작성할 수 없다")
	void rejectSecretPostWhenSecretModeIsOff() {
		allowActiveMember();
		prepareBoard("free", policy("MEMBER", "OFF", false, false));

		assertThrows(InvalidPostRequestException.class,
			() -> postService.createPost("free", request(true, "1234"), List.of(), 10L, false));

		verifyNoInteractions(postMapper, postConverter);
	}

	@Test
	@DisplayName("공개 게시판의 두 번째 페이지를 조회한다")
	void getSecondPageFromPublicBoard() {
		BoardPolicy policy = readPolicy("PUBLIC");

		prepareBoard("free", policy);

		LocalDateTime createdAt = LocalDateTime.of(2026, 9, 21, 10, 0);

		List<PostSummaryResponse> posts = List.of(
			new PostSummaryResponse(
				11L, "게시글 제목", 10L, "작성자",
				null, null, false, false,
				3L, 2L, createdAt, null, false, 7L
			)
		);

		when(postMapper.countPostSummaries(1L, null, null)).thenReturn(21L);

		when(postMapper.selectPostSummaries(1L, null, null, PostSort.LATEST, 10, 10L)).thenReturn(posts);

		PostPageResponse response = postService.getPosts("free", 2, 10, defaultPostListRequest(), false, false);

		assertAll(() -> assertEquals(posts, response.posts()), () -> assertEquals(2, response.page()),
			() -> assertEquals(10, response.size()), () -> assertEquals(21L, response.totalElements()),
			() -> assertEquals(3, response.totalPages()));

		verify(postMapper).selectPostSummaries(1L, null, null, PostSort.LATEST, 10, 10L);

		assertEquals(7L, response.posts().getFirst().likeCount());
		verifyNoInteractions(postLikeMapper);
	}

	@Test
	@DisplayName("일반 회원은 관리자 전용 게시판 목록을 조회할 수 없다")
	void rejectMemberFromAdminBoardPostList() {
		prepareBoard("admin", readPolicy("ADMIN"));

		assertThrows(BoardAccessDeniedException.class,
			() -> postService.getPosts("admin", 1, 10, defaultPostListRequest(), true, false));

		verifyNoInteractions(postMapper);
	}

	@Test
	@DisplayName("마지막 페이지를 넘으면 빈 게시글 목록을 반환한다")
	void returnEmptyPostsWhenPageExceedsLastPage() {
		prepareBoard("free", readPolicy("PUBLIC"));

		when(postMapper.countPostSummaries(1L, null, null)).thenReturn(11L);

		PostPageResponse response = postService.getPosts("free", 3, 10, defaultPostListRequest(), false, false);

		assertAll(() -> assertTrue(response.posts().isEmpty()), () -> assertEquals(3, response.page()),
			() -> assertEquals(11L, response.totalElements()), () -> assertEquals(2, response.totalPages()));

		verify(postMapper).countPostSummaries(1L, null, null);
		verifyNoMoreInteractions(postMapper);
	}

	@Test
	@DisplayName("일반 게시글 상세를 조회하고 조회수를 증가시킨다")
	void getRegularPostDetailAndIncreaseViewCount() {
		PostDetail postDetail = postDetail(false, null);
		PostDetailResponse expected = detailResponse(0L, false);

		prepareBoard("free", readPolicy("PUBLIC"));

		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(postDetail));
		when(postMapper.increaseViewCount(1L, 100L)).thenReturn(1);
		when(postConverter.toDetailResponse(postDetail, 6L, List.of(), 0L, false))
			.thenReturn(expected);
		PostDetailResponse response = postService.getPostDetail("free", 100L, null, false, false, null);

		assertEquals(expected, response);
		verify(postMapper).increaseViewCount(1L, 100L);
	}

	@Test
	@DisplayName("비로그인은 좋아요 수를 조회하고 내 좋아요 여부는 조회하지 않는다")
	void getLikeCountWithoutAuthentication() {
		PostDetail postDetail = postDetail(false, null);
		PostDetailResponse expected = detailResponse(4L, false);

		prepareBoard("free", readPolicy("PUBLIC"));

		when(postMapper.selectActivePostDetail(1L, 100L))
			.thenReturn(Optional.of(postDetail));
		when(postMapper.increaseViewCount(1L, 100L)).thenReturn(1);
		when(postLikeMapper.countPostLikes(100L)).thenReturn(4L);
		when(postConverter.toDetailResponse(
			postDetail, 6L, List.of(), 4L, false
		)).thenReturn(expected);

		PostDetailResponse response = postService.getPostDetail(
			"free", 100L, null, false, false, null
		);

		assertEquals(4L, response.likeCount());
		assertFalse(response.likedByMe());

		verify(postLikeMapper).countPostLikes(100L);
		verify(postLikeMapper, never()).existsPostLike(anyLong(), anyLong());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	@DisplayName("좋아요 기능이 꺼져도 기존 좋아요 수와 내 좋아요 상태를 조회한다")
	void getExistingLikesWhenLikesAreDisabled(boolean likedByMe) {
		PostDetail postDetail = postDetail(false, null);
		PostDetailResponse expected = detailResponse(4L, likedByMe);

		BoardPolicy policy = BoardPolicy.builder()
			.readPermission("PUBLIC")
			.postLikesEnabled(false)
			.build();

		prepareBoard("free", policy);

		when(postMapper.selectActivePostDetail(1L, 100L))
			.thenReturn(Optional.of(postDetail));
		when(postMapper.increaseViewCount(1L, 100L)).thenReturn(1);
		when(postLikeMapper.countPostLikes(100L)).thenReturn(4L);
		when(postLikeMapper.existsPostLike(100L, 10L)).thenReturn(likedByMe);
		when(postConverter.toDetailResponse(
			postDetail, 6L, List.of(), 4L, likedByMe
		)).thenReturn(expected);

		PostDetailResponse response = postService.getPostDetail(
			"free", 100L, 10L, true, false, null
		);

		assertEquals(4L, response.likeCount());
		assertEquals(likedByMe, response.likedByMe());

		verify(postLikeMapper).countPostLikes(100L);
		verify(postLikeMapper).existsPostLike(100L, 10L);
	}

	@Test
	@DisplayName("비밀번호가 없는 비밀글은 누구나 조회할 수 있다")
	void allowPasswordlessSecretPostForAnyone() {
		PostDetail postDetail = postDetail(true, null);
		PostDetailResponse expected = detailResponse(0L, false);

		prepareBoard("free", readPolicy("PUBLIC"));

		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(postDetail));
		when(postMapper.increaseViewCount(1L, 100L)).thenReturn(1);
		when(postConverter.toDetailResponse(postDetail, 6L, List.of(), 0L, false))
			.thenReturn(expected);
		PostDetailResponse response = postService.getPostDetail("free", 100L, null, false, false, null);

		assertEquals(expected, response);
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("비밀글 비밀번호가 일치하면 게시글을 조회할 수 있다")
	void allowSecretPostWithCorrectPassword() {
		PostDetail postDetail = postDetail(true, "encoded-password");
		PostDetailResponse expected = detailResponse(0L, false);

		prepareBoard("free", readPolicy("PUBLIC"));

		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(postDetail));
		when(passwordEncoder.matches("1234", "encoded-password")).thenReturn(true);
		when(postMapper.increaseViewCount(1L, 100L)).thenReturn(1);
		when(postConverter.toDetailResponse(postDetail, 6L, List.of(), 0L, false))
			.thenReturn(expected);
		PostDetailResponse response = postService.getPostDetail("free", 100L, null, false, false, "1234");

		assertEquals(expected, response);
		verify(passwordEncoder).matches("1234", "encoded-password");
	}

	@Test
	@DisplayName("비밀글 비밀번호가 일치하지 않으면 조회할 수 없다")
	void rejectSecretPostWithWrongPassword() {
		PostDetail postDetail = postDetail(true, "encoded-password");

		prepareBoard("free", readPolicy("PUBLIC"));

		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(postDetail));
		when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

		assertThrows(SecretPostAccessDeniedException.class,
			() -> postService.getPostDetail("free", 100L, null, false, false, "wrong-password"));

		verify(postMapper, never()).increaseViewCount(1L, 100L);
		verifyNoInteractions(postConverter);
		verifyNoInteractions(postLikeMapper);
	}

	@Test
	@DisplayName("작성자는 비밀번호 없이 자신의 비밀글을 조회할 수 있다")
	void allowAuthorToReadSecretPostWithoutPassword() {
		PostDetail postDetail = postDetail(true, "encoded-password");
		PostDetailResponse expected = detailResponse(0L, false);

		prepareBoard("free", readPolicy("PUBLIC"));

		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(postDetail));
		when(postMapper.increaseViewCount(1L, 100L)).thenReturn(1);
		when(postConverter.toDetailResponse(postDetail, 6L, List.of(), 0L, false))
			.thenReturn(expected);
		PostDetailResponse response = postService.getPostDetail("free", 100L, 10L, true, false, null);

		assertEquals(expected, response);
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("작성자는 기존 비밀글 비밀번호를 유지하며 게시글을 수정할 수 있다")
	void updateOwnPostAndKeepSecretPassword() {
		PostUpdateRequest request = new PostUpdateRequest("수정 제목", document("수정 본문"), null, true, null, false, false, 0,
			List.of());

		PostDetail currentPost = postDetail(10L, true, "existing-hash", 0);

		Post updatedPost = Post.builder()
			.id(100L)
			.boardId(1L)
			.authorId(10L)
			.title("수정 제목")
			.content("수정 본문")
			.isSecret(true)
			.secretPasswordHash("existing-hash")
			.commentsLocked(false)
			.version(0)
			.build();

		allowActiveMember();
		prepareBoard("free", policy("MEMBER", "OPTIONAL", false, false));

		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(currentPost));
		when(postConverter.toUpdatedPost(eq(request), eq(100L), eq(1L), eq(10L), eq("existing-hash"),
			any(ValidatedDocument.class))).thenReturn(updatedPost);
		when(postMapper.updatePost(updatedPost)).thenReturn(1);

		postService.updatePost("free", 100L, request, List.of(), 10L, false);

		verify(postMapper).updatePost(updatedPost);
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("비밀글 정책을 끈 뒤에도 기존 비밀글의 비밀번호를 유지하며 수정할 수 있다")
	void updateExistingSecretPostWhenSecretModeIsOff() {
		PostUpdateRequest request = new PostUpdateRequest("수정 제목", document("수정 본문"), null, true, null, false, false, 0,
			List.of());
		PostDetail currentPost = postDetail(10L, true, "existing-hash", 0);
		Post updatedPost = Post.builder()
			.id(100L)
			.boardId(1L)
			.authorId(10L)
			.title("수정 제목")
			.content("수정 본문")
			.isSecret(true)
			.secretPasswordHash("existing-hash")
			.commentsLocked(false)
			.version(0)
			.build();

		allowActiveMember();
		prepareBoard("free", policy("MEMBER", "OFF", false, false));
		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(currentPost));
		when(postConverter.toUpdatedPost(eq(request), eq(100L), eq(1L), eq(10L), eq("existing-hash"),
			any(ValidatedDocument.class))).thenReturn(updatedPost);
		when(postMapper.updatePost(updatedPost)).thenReturn(1);

		postService.updatePost("free", 100L, request, List.of(), 10L, false);

		verify(postMapper).updatePost(updatedPost);
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("비밀글 정책이 꺼져 있으면 일반 글을 비밀글로 전환할 수 없다")
	void rejectSecretConversionWhenSecretModeIsOff() {
		PostUpdateRequest request = new PostUpdateRequest("수정 제목", document("수정 본문"), null, true, null, false, false, 0,
			List.of());

		allowActiveMember();
		prepareBoard("free", policy("MEMBER", "OFF", false, false));
		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(postDetail(10L, false, null, 0)));

		assertThrows(InvalidPostRequestException.class,
			() -> postService.updatePost("free", 100L, request, List.of(), 10L, false));

		verify(postMapper, never()).updatePost(any());
		verifyNoInteractions(postConverter);
	}

	@Test
	@DisplayName("비밀글 정책이 꺼져 있어도 작성자가 기존 비밀글을 명시적으로 공개할 수 있다")
	void publishExistingSecretPostWhenSecretModeIsOff() {
		PostUpdateRequest request = new PostUpdateRequest("수정 제목", document("수정 본문"), null, false, null, false, false,
			0, List.of());
		PostDetail currentPost = postDetail(10L, true, "existing-hash", 0);
		Post updatedPost = Post.builder()
			.id(100L)
			.boardId(1L)
			.authorId(10L)
			.title("수정 제목")
			.content("수정 본문")
			.isSecret(false)
			.secretPasswordHash(null)
			.commentsLocked(false)
			.version(0)
			.build();

		allowActiveMember();
		prepareBoard("free", policy("MEMBER", "OFF", false, false));
		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(currentPost));
		when(postConverter.toUpdatedPost(eq(request), eq(100L), eq(1L), eq(10L), isNull(),
			any(ValidatedDocument.class))).thenReturn(updatedPost);
		when(postMapper.updatePost(updatedPost)).thenReturn(1);

		postService.updatePost("free", 100L, request, List.of(), 10L, false);

		verify(postMapper).updatePost(updatedPost);
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("작성자가 아니면 게시글을 수정할 수 없다")
	void rejectPostUpdateByNonAuthor() {
		PostUpdateRequest request = new PostUpdateRequest("수정 제목", document("수정 본문"), null, true, null, false, false, 0,
			List.of());

		allowActiveMember();
		prepareBoard("free", policy("MEMBER", "OPTIONAL", false, false));

		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(postDetail(20L, false, null, 0)));

		assertThrows(PostEditDeniedException.class,
			() -> postService.updatePost("free", 100L, request, List.of(), 10L, false));

		verify(postMapper, never()).updatePost(any());
		verifyNoInteractions(postConverter);
	}

	@Test
	@DisplayName("이전 버전으로 게시글을 수정하면 충돌이 발생한다")
	void rejectPostUpdateWithStaleVersion() {
		PostUpdateRequest request = new PostUpdateRequest("수정 제목", document("수정 본문"), null, true, null, false, false, 1,
			List.of());

		allowActiveMember();
		prepareBoard("free", policy("MEMBER", "OPTIONAL", false, false));

		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(postDetail(10L, false, null, 0)));

		assertThrows(PostConflictException.class,
			() -> postService.updatePost("free", 100L, request, List.of(), 10L, false));

		verify(postMapper, never()).updatePost(any());
		verifyNoInteractions(postConverter);
	}

	@Test
	@DisplayName("기존 이미지 순서를 변경하면 대표 이미지를 다시 선정한다")
	void reorderExistingImagesAndRefreshRepresentative() {
		PostUpdateRequest request = new PostUpdateRequest("수정 제목", existingImageDocument("수정 본문", 12L, 11L), null, true,
			null, false, false, 0, List.of(11L, 12L));

		Post updatedPost = Post.builder().id(100L).boardId(1L).authorId(10L).version(0).build();

		PostFile firstImage = PostFile.builder().id(11L).postId(100L).contentType("image/png").sortOrder(0).build();

		PostFile secondImage = PostFile.builder().id(12L).postId(100L).contentType("image/jpeg").sortOrder(1).build();

		allowActiveMember();
		prepareBoard("free", policy("MEMBER", "OPTIONAL", false, false));

		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(postDetail(10L, false, null, 0)));
		when(postConverter.toUpdatedPost(eq(request), eq(100L), eq(1L), eq(10L), isNull(),
			any(ValidatedDocument.class))).thenReturn(updatedPost);
		when(postMapper.updatePost(updatedPost)).thenReturn(1);
		when(postFileMapper.selectActivePostFilesForUpdate(100L)).thenReturn(List.of(firstImage, secondImage));

		postService.updatePost("free", 100L, request, List.of(), 10L, false);

		InOrder order = inOrder(postMapper, postFileMapper);
		order.verify(postMapper).updatePost(updatedPost);
		order.verify(postFileMapper).selectActivePostFilesForUpdate(100L);
		order.verify(postFileMapper).updateFileMetadata(100L, 12L, "BODY_IMAGE", 0);
		order.verify(postFileMapper).updateFileMetadata(100L, 11L, "BODY_IMAGE", 1);
		order.verify(postMapper).refreshRepresentativeImage(1L, 100L);

		verifyNoInteractions(localFileStorage);
	}

	@Test
	@DisplayName("기존 이미지를 삭제하면 정리 작업을 등록하고 대표 이미지를 다시 선정한다")
	void removeExistingImageAndRefreshRepresentative() {
		PostUpdateRequest request = new PostUpdateRequest("수정 제목", existingImageDocument("수정 본문", 12L), null, true,
			null, false, false, 0, List.of(12L));
		PostFile removed = PostFile.builder()
			.id(11L)
			.postId(100L)
			.contentType("image/png")
			.storageKey("posts/100/11.png")
			.thumbnailKey("thumbnails/100/11.png")
			.build();
		PostFile retained = PostFile.builder()
			.id(12L)
			.postId(100L)
			.contentType("image/jpeg")
			.storageKey("posts/100/12.jpg")
			.build();
		Post updatedPost = Post.builder().id(100L).boardId(1L).authorId(10L).version(0).build();

		allowActiveMember();
		prepareBoard("free", policy("MEMBER", "OPTIONAL", false, false));
		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(postDetail(10L, false, null, 0)));
		when(postConverter.toUpdatedPost(eq(request), eq(100L), eq(1L), eq(10L), isNull(),
			any(ValidatedDocument.class))).thenReturn(updatedPost);
		when(postMapper.updatePost(updatedPost)).thenReturn(1);
		when(postFileMapper.selectActivePostFilesForUpdate(100L)).thenReturn(List.of(removed, retained));
		when(postFileMapper.softDeletePostFiles(100L, List.of(11L))).thenReturn(1);

		postService.updatePost("free", 100L, request, List.of(), 10L, false);

		InOrder order = inOrder(postMapper, postFileMapper, fileCleanupTaskMapper);
		order.verify(postMapper).updatePost(updatedPost);
		order.verify(postFileMapper).selectActivePostFilesForUpdate(100L);
		order.verify(postFileMapper).updateFileMetadata(100L, 12L, "BODY_IMAGE", 0);
		order.verify(postFileMapper).softDeletePostFiles(100L, List.of(11L));
		order.verify(fileCleanupTaskMapper).enqueueDeletes(List.of("posts/100/11.png", "thumbnails/100/11.png"));
		order.verify(postMapper).refreshRepresentativeImage(1L, 100L);

		verifyNoInteractions(localFileStorage);
	}

	@Test
	@DisplayName("관리자는 다른 회원의 게시글을 삭제할 수 있다")
	void deletePostByAdmin() {
		when(memberMapper.existsActiveMemberById(1L)).thenReturn(true);

		prepareBoard("free");

		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(postDetail(10L, false, null, 0)));
		when(postMapper.softDeletePost(1L, 100L, 0)).thenReturn(1);
		when(postFileMapper.selectActivePostFilesForUpdate(100L)).thenReturn(List.of());

		postService.deletePost("free", 100L, 0, 1L, true);

		InOrder order = inOrder(postMapper, postFileMapper);

		order.verify(postMapper).softDeletePost(1L, 100L, 0);
		order.verify(postFileMapper).selectActivePostFilesForUpdate(100L);
		order.verify(postFileMapper).softDeleteAllPostFiles(100L);

		verify(memberMapper, never()).existsActiveSanctionByMemberId(anyLong());

		verifyNoInteractions(fileCleanupTaskMapper, localFileStorage);
	}

	@Test
	@DisplayName("작성자도 관리자가 아니면 게시글을 삭제할 수 없다")
	void rejectPostDeletionByNonAuthor() {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);

		prepareBoard("free");

		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(postDetail(20L, false, null, 0)));

		assertThrows(PostDeleteDeniedException.class, () -> postService.deletePost("free", 100L, 0, 10L, false));

		verify(postMapper, never()).softDeletePost(anyLong(), anyLong(), anyInt());
	}

	@Test
	@DisplayName("검색어와 카테고리와 정렬 조건을 적용해 게시글 목록을 조회한다")
	void getPostsWithSearchFilters() {
		BoardPolicy policy = policy("MEMBER", "OFF", true, false);

		prepareBoard("free", policy);

		when(boardMapper.existsActiveCategory(1L, 2L)).thenReturn(true);

		when(postMapper.countPostSummaries(1L, "50=%=_off==", 2L)).thenReturn(1L);

		when(postMapper.selectPostSummaries(1L, "50=%=_off==", 2L, PostSort.VIEWS, 10, 0L)).thenReturn(List.of());

		PostListRequest request = new PostListRequest("50%_off=", 2L, PostSort.VIEWS);

		PostPageResponse response = postService.getPosts("free", 1, 10, request, false, false);

		assertAll(() -> assertEquals(1L, response.totalElements()), () -> assertEquals(1, response.totalPages()));

		verify(boardMapper).existsActiveCategory(1L, 2L);

		verify(postMapper).selectPostSummaries(1L, "50=%=_off==", 2L, PostSort.VIEWS, 10, 0L);
	}

	@Test
	@DisplayName("카테고리를 사용하지 않는 게시판은 카테고리 필터를 거절한다")
	void rejectCategoryFilterWhenCategoriesAreDisabled() {
		prepareBoard("free", readPolicy("PUBLIC"));

		PostListRequest request = new PostListRequest(null, 2L, PostSort.LATEST);

		assertThrows(InvalidPostRequestException.class,
			() -> postService.getPosts("free", 1, 10, request, false, false));

		verifyNoInteractions(postMapper);
	}

	@Test
	@DisplayName("다른 게시판이거나 비활성화된 카테고리 필터를 거절한다")
	void rejectUnavailableCategoryFilter() {
		BoardPolicy policy = policy("MEMBER", "OFF", true, false);

		prepareBoard("free", policy);

		when(boardMapper.existsActiveCategory(1L, 2L)).thenReturn(false);

		PostListRequest request = new PostListRequest(null, 2L, PostSort.LATEST);

		assertThrows(InvalidPostRequestException.class,
			() -> postService.getPosts("free", 1, 10, request, false, false));

		verifyNoInteractions(postMapper);
	}

	@Test
	@DisplayName("기존 이미지를 유지하면서 새 ZIP 파일을 추가한다")
	void addZipWhileRetainingExistingImage() {
		byte[] zipBytes = {0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0};
		MockMultipartFile zip = new MockMultipartFile("files", "new.zip", "application/zip", zipBytes);
		PostUpdateRequest request = new PostUpdateRequest("수정 제목", existingImageDocument("수정 본문", 11L), null, true,
			null, false, false, 0, List.of(11L));
		BoardPolicy filePolicy = BoardPolicy.builder()
			.readPermission("PUBLIC")
			.writePermission("MEMBER")
			.secretMode("OPTIONAL")
			.commentsEnabled(true)
			.attachmentsEnabled(true)
			.uploadProfile("IMAGE_AND_ZIP")
			.maxFileCount(3)
			.maxFileSizeBytes(1024L)
			.imageRequired(true)
			.thumbnailEnabled(true)
			.build();
		PostFile existingImage = PostFile.builder()
			.id(11L)
			.postId(100L)
			.contentType("image/png")
			.sizeBytes(100L)
			.build();
		Post updatedPost = Post.builder().id(100L).boardId(1L).authorId(10L).version(0).build();
		List<TempStoredFile> temporary = List.of(new TempStoredFile("temp/new.zip", "new.zip", FileFormat.ZIP, 8L, 0));
		List<StoredPostFile> stored = List.of(
			new StoredPostFile("posts/100/new.zip", "new.zip", FileFormat.ZIP, 8L, 0, null));

		allowActiveMember();
		prepareBoard("free", filePolicy);
		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(postDetail(10L, false, null, 0)));
		when(postConverter.toUpdatedPost(eq(request), eq(100L), eq(1L), eq(10L), isNull(),
			any(ValidatedDocument.class))).thenReturn(updatedPost);
		when(postMapper.updatePost(updatedPost)).thenReturn(1);
		when(postFileMapper.selectActivePostFilesForUpdate(100L)).thenReturn(List.of(existingImage));
		when(localFileStorage.storeTemporary(anyList())).thenReturn(temporary);
		when(localFileStorage.moveToPost(100L, temporary, true)).thenReturn(stored);
		when(postFileMapper.insertPostFiles(anyList())).thenReturn(1);

		postService.updatePost("free", 100L, request, List.of(zip), 10L, false);

		verify(postFileMapper).insertPostFiles(
			argThat(rows -> rows.size() == 1 && "posts/100/new.zip".equals(rows.getFirst().getStorageKey())));
		verify(postMapper).refreshRepresentativeImage(1L, 100L);
		verifyNoInteractions(fileCleanupTaskMapper);
	}

	@Test
	@DisplayName("유지하지 않는 기존 이미지를 본문에 참조하면 수정을 거절한다")
	void rejectBodyImageNotRetained() {
		PostUpdateRequest request = new PostUpdateRequest("수정 제목", existingImageDocument("수정 본문", 12L), null, false,
			null, false, false, 0, List.of(11L));
		PostFile file = PostFile.builder().id(11L).postId(100L).contentType("image/png").build();
		Post updatedPost = Post.builder().id(100L).boardId(1L).authorId(10L).version(0).build();

		allowActiveMember();
		prepareBoard("free", policy("MEMBER", "OPTIONAL", false, false));
		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(postDetail(10L, false, null, 0)));
		when(postConverter.toUpdatedPost(eq(request), eq(100L), eq(1L), eq(10L), isNull(),
			any(ValidatedDocument.class))).thenReturn(updatedPost);
		when(postMapper.updatePost(updatedPost)).thenReturn(1);
		when(postFileMapper.selectActivePostFilesForUpdate(100L)).thenReturn(List.of(file));

		assertThrows(InvalidPostRequestException.class,
			() -> postService.updatePost("free", 100L, request, List.of(), 10L, false));

		verify(postFileMapper, never()).updateFileMetadata(anyLong(), anyLong(), anyString(), anyInt());
		verify(postFileMapper, never()).insertPostFiles(anyList());
		verify(postFileMapper, never()).softDeletePostFiles(anyLong(), anyList());
		verify(postMapper, never()).refreshRepresentativeImage(anyLong(), anyLong());
		verifyNoInteractions(localFileStorage, fileCleanupTaskMapper);
	}

	@Test
	@DisplayName("이미지만 있는 글을 등록하고 본문 순서와 첨부 이미지 용도를 구분한다")
	void createImageOnlyPostWithBodyOrderAndAttachment() throws Exception {
		MockMultipartFile first = createImage("A.png");
		MockMultipartFile second = createImage("B.png");
		MockMultipartFile attachment = createImage("C.png");
		long size = first.getSize();

		// 업로드는 A, B, C. 본문에는 B, A만 배치한다.
		PostCreateRequest request = new PostCreateRequest("제목", document("", 1, 0), null, false, null, false);

		Post post = Post.builder().id(100L).boardId(1L).authorId(10L).build();

		List<TempStoredFile> temporary = List.of(new TempStoredFile("temp/A.png", "A.png", FileFormat.PNG, size, 0),
			new TempStoredFile("temp/B.png", "B.png", FileFormat.PNG, size, 1),
			new TempStoredFile("temp/C.png", "C.png", FileFormat.PNG, size, 2));

		List<StoredPostFile> stored = List.of(
			new StoredPostFile("posts/100/A.png", "A.png", FileFormat.PNG, size, 0, "thumbnails/100/A.png"),
			new StoredPostFile("posts/100/B.png", "B.png", FileFormat.PNG, size, 1, "thumbnails/100/B.png"),
			new StoredPostFile("posts/100/C.png", "C.png", FileFormat.PNG, size, 2, "thumbnails/100/C.png"));

		allowActiveMember();
		prepareBoard("free", filePolicy(false));

		when(postConverter.toPost(eq(request), eq(1L), eq(10L), isNull(), any(ValidatedDocument.class))).thenReturn(
			post);
		when(postMapper.insertPost(post)).thenReturn(1);
		when(localFileStorage.storeTemporary(anyList())).thenReturn(temporary);
		when(localFileStorage.moveToPost(100L, temporary, true)).thenReturn(stored);

		when(postFileMapper.insertPostFiles(anyList())).thenAnswer(invocation -> {
			List<PostFile> rows = invocation.getArgument(0);
			for (int index = 0; index < rows.size(); index++) {
				ReflectionTestUtils.setField(rows.get(index), "id", 11L + index);
			}
			return rows.size();
		});

		when(postMapper.updateContentDocument(eq(1L), eq(100L), any(JsonNode.class))).thenReturn(1);
		when(postMapper.refreshRepresentativeImage(1L, 100L)).thenReturn(1);
		when(postConverter.toCreateResponse(post)).thenReturn(new PostCreateResponse(100L));

		PostCreateResponse response = postService.createPost("free", request, List.of(first, second, attachment), 10L,
			false);

		assertEquals(100L, response.id());

		verify(postFileMapper).insertPostFiles(argThat(
			rows -> rows.size() == 3 && "BODY_IMAGE".equals(rows.get(0).getFileUsage()) && Integer.valueOf(1)
				.equals(rows.get(0).getSortOrder()) && "BODY_IMAGE".equals(rows.get(1).getFileUsage())
				&& Integer.valueOf(0).equals(rows.get(1).getSortOrder()) && "ATTACHMENT".equals(
				rows.get(2).getFileUsage()) && Integer.valueOf(2).equals(rows.get(2).getSortOrder())));

		ArgumentCaptor<JsonNode> documentCaptor = ArgumentCaptor.forClass(JsonNode.class);
		verify(postMapper).updateContentDocument(eq(1L), eq(100L), documentCaptor.capture());

		JsonNode body = documentCaptor.getValue().path("content");
		assertEquals(12L, body.get(0).path("attrs").path("fileId").longValue());
		assertEquals(11L, body.get(1).path("attrs").path("fileId").longValue());
		assertFalse(body.get(0).path("attrs").has("fileIndex"));
		assertFalse(body.get(1).path("attrs").has("fileIndex"));

		verify(postMapper).refreshRepresentativeImage(1L, 100L);
	}

	@Test
	@DisplayName("다운로드용 이미지만 첨부하면 본문 이미지 필수 조건을 충족하지 못한다")
	void rejectAttachmentImageForRequiredBodyImage() throws Exception {
		allowActiveMember();
		prepareBoard("free", filePolicy(true));

		assertThrows(InvalidPostRequestException.class,
			() -> postService.createPost("free", request(false, null), List.of(createImage("A.png")), 10L, false));

		verifyNoInteractions(postMapper, postFileMapper, postConverter, localFileStorage);
	}

	@Test
	@DisplayName("실제로 첨부하지 않은 본문 이미지는 파일 저장 전에 거절한다")
	void rejectBodyImageOutsideUploadedFiles() throws Exception {
		allowActiveMember();
		prepareBoard("free", filePolicy(false));

		PostCreateRequest request = new PostCreateRequest("제목", document("본문", 1), null, false, null, false);

		assertThrows(InvalidPostRequestException.class,
			() -> postService.createPost("free", request, List.of(createImage("A.png")), 10L, false));

		verifyNoInteractions(postMapper, postFileMapper, postConverter, localFileStorage);
	}

	@Test
	@DisplayName("수정 시 다운로드용 이미지만 유지하면 본문 이미지 필수 조건을 충족하지 못한다")
	void rejectUpdateWithoutRequiredBodyImage() {
		PostUpdateRequest request = new PostUpdateRequest("수정 제목", document("수정 본문"), null, false, null, false, false,
			0, List.of(11L));

		allowActiveMember();
		prepareBoard("free", filePolicy(true));
		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(postDetail(10L, false, null, 0)));

		assertThrows(InvalidPostRequestException.class,
			() -> postService.updatePost("free", 100L, request, List.of(), 10L, false));

		verify(postMapper, never()).updatePost(any());
		verifyNoInteractions(postConverter, postFileMapper, localFileStorage);
	}

	@Test
	@DisplayName("새 이미지와 기존 이미지 사이의 텍스트를 유지하고 다운로드용 첨부파일을 구분한다")
	void updateDocumentWithNewAndExistingImages() throws Exception {
		MockMultipartFile image = createImage("new.png");
		MockMultipartFile zip = new MockMultipartFile("files", "new.zip", "application/zip",
			new byte[] {0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0});

		ObjectNode body = (ObjectNode)document("앞 내용", 0);
		ArrayNode blocks = (ArrayNode)body.path("content");
		ObjectNode paragraph = blocks.addObject();
		paragraph.put("type", "paragraph");
		paragraph.putArray("content").addObject().put("type", "text").put("text", "중간 내용");
		ObjectNode existingImage = blocks.addObject();
		existingImage.put("type", "image");
		existingImage.putObject("attrs").put("fileId", 11L);

		PostUpdateRequest request = new PostUpdateRequest("수정 제목", body, null, false, null, false, false, 0,
			List.of(11L));
		Post updatedPost = Post.builder().id(100L).boardId(1L).authorId(10L).version(0).build();
		PostFile retained = PostFile.builder().id(11L).postId(100L).contentType("image/png").sizeBytes(100L).build();
		PostFile removed = PostFile.builder()
			.id(12L)
			.postId(100L)
			.contentType("image/png")
			.storageKey("posts/100/old.png")
			.thumbnailKey("thumbnails/100/old.png")
			.build();

		List<TempStoredFile> temporary = List.of(
			new TempStoredFile("temp/new.png", "new.png", FileFormat.PNG, image.getSize(), 0),
			new TempStoredFile("temp/new.zip", "new.zip", FileFormat.ZIP, 8L, 1));
		List<StoredPostFile> stored = List.of(
			new StoredPostFile("posts/100/new.png", "new.png", FileFormat.PNG, image.getSize(), 0,
				"thumbnails/100/new.png"),
			new StoredPostFile("posts/100/new.zip", "new.zip", FileFormat.ZIP, 8L, 1, null));

		allowActiveMember();
		prepareBoard("free", filePolicy(true));
		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(postDetail(10L, false, null, 0)));
		when(postConverter.toUpdatedPost(eq(request), eq(100L), eq(1L), eq(10L), isNull(),
			any(ValidatedDocument.class))).thenReturn(updatedPost);
		when(postMapper.updatePost(updatedPost)).thenReturn(1);
		when(postFileMapper.selectActivePostFilesForUpdate(100L)).thenReturn(List.of(retained, removed));
		when(localFileStorage.storeTemporary(anyList())).thenReturn(temporary);
		when(localFileStorage.moveToPost(100L, temporary, true)).thenReturn(stored);
		when(postFileMapper.insertPostFiles(anyList())).thenAnswer(invocation -> {
			List<PostFile> rows = invocation.getArgument(0);
			ReflectionTestUtils.setField(rows.get(0), "id", 20L);
			ReflectionTestUtils.setField(rows.get(1), "id", 21L);
			return rows.size();
		});
		when(postMapper.updateContentDocument(eq(1L), eq(100L), any(JsonNode.class))).thenReturn(1);
		when(postFileMapper.softDeletePostFiles(100L, List.of(12L))).thenReturn(1);

		postService.updatePost("free", 100L, request, List.of(image, zip), 10L, false);

		ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
		verify(postMapper).updateContentDocument(eq(1L), eq(100L), captor.capture());
		JsonNode saved = captor.getValue().path("content");

		assertEquals("앞 내용", saved.get(0).path("content").get(0).path("text").asString());
		assertEquals(20L, saved.get(1).path("attrs").path("fileId").longValue());
		assertEquals("중간 내용", saved.get(2).path("content").get(0).path("text").asString());
		assertEquals(11L, saved.get(3).path("attrs").path("fileId").longValue());
		assertFalse(saved.get(1).path("attrs").has("fileIndex"));

		verify(postFileMapper).insertPostFiles(argThat(
			rows -> rows.size() == 2 && "BODY_IMAGE".equals(rows.getFirst().getFileUsage()) && Integer.valueOf(0)
				.equals(rows.getFirst().getSortOrder()) && "ATTACHMENT".equals(rows.get(1).getFileUsage())
				&& Integer.valueOf(2).equals(rows.get(1).getSortOrder())));
		verify(postFileMapper).updateFileMetadata(100L, 11L, "BODY_IMAGE", 1);
		verify(fileCleanupTaskMapper).enqueueDeletes(List.of("posts/100/old.png", "thumbnails/100/old.png"));
		verify(postMapper).refreshRepresentativeImage(1L, 100L);
	}

	@ParameterizedTest
	@CsvSource({"notes.txt, text/plain;charset=UTF-8", "guide.pdf, application/pdf", "photo.png, image/png",
		"archive.zip, application/zip"})
	@DisplayName("다운로드용 파일은 실제 형식과 관계없이 첨부 응답으로 반환한다")
	void returnAttachmentByFileUsage(String name, String contentType) {
		PostFile file = PostFile.builder()
			.id(11L)
			.postId(100L)
			.originalName(name)
			.contentType(contentType)
			.fileUsage("ATTACHMENT")
			.storageKey("posts/100/file")
			.build();
		ByteArrayResource resource = new ByteArrayResource(new byte[] {1, 2, 3});

		prepareBoard("free", readPolicy("PUBLIC"));
		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(postDetail(false, null)));
		when(postFileMapper.selectActivePostFile(100L, 11L)).thenReturn(Optional.of(file));
		when(localFileStorage.load("posts/100/file")).thenReturn(resource);

		PostFileContent result = postService.getPostFile("free", 100L, 11L, false, null, false, false, null);

		assertTrue(result.attachment());
		assertEquals(contentType, result.contentType());
		assertSame(resource, result.resource());

		verify(postMapper).selectActivePostDetail(1L, 100L);
		verifyNoMoreInteractions(postMapper);
	}

	@Test
	@DisplayName("본문 이미지는 다운로드용 첨부 응답으로 반환하지 않는다")
	void returnBodyImageWithoutAttachmentDisposition() {
		PostFile file = PostFile.builder()
			.id(11L)
			.postId(100L)
			.originalName("body.png")
			.contentType("image/png")
			.fileUsage("BODY_IMAGE")
			.storageKey("posts/100/body.png")
			.build();

		prepareBoard("free", readPolicy("PUBLIC"));
		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(postDetail(false, null)));
		when(postFileMapper.selectActivePostFile(100L, 11L)).thenReturn(Optional.of(file));
		when(localFileStorage.load("posts/100/body.png")).thenReturn(new ByteArrayResource(new byte[] {1, 2, 3}));

		PostFileContent result = postService.getPostFile("free", 100L, 11L, false, null, false, false, null);

		assertFalse(result.attachment());
		assertEquals("image/png", result.contentType());
	}

	@Test
	@DisplayName("인용문 안의 새 이미지를 실제 파일 ID로 치환하여 저장한다")
	void createPostWithNestedBodyImage() throws Exception {
		MockMultipartFile image = createImage("A.png");
		long size = image.getSize();

		ObjectNode body = (ObjectNode)document("", 0);
		ArrayNode imageBlocks = (ArrayNode)body.get("content");
		body.putArray("content").addObject().put("type", "blockquote").set("content", imageBlocks);

		PostCreateRequest request = new PostCreateRequest("제목", body, null, false, null, false);
		Post post = Post.builder().id(100L).boardId(1L).authorId(10L).build();

		List<TempStoredFile> temporary = List.of(new TempStoredFile("temp/A.png", "A.png", FileFormat.PNG, size, 0));
		List<StoredPostFile> stored = List.of(
			new StoredPostFile("posts/100/A.png", "A.png", FileFormat.PNG, size, 0, "thumbnails/100/A.png"));

		allowActiveMember();
		prepareBoard("free", filePolicy(true));

		when(postConverter.toPost(eq(request), eq(1L), eq(10L), isNull(), any(ValidatedDocument.class))).thenReturn(
			post);
		when(postMapper.insertPost(post)).thenReturn(1);
		when(localFileStorage.storeTemporary(anyList())).thenReturn(temporary);
		when(localFileStorage.moveToPost(100L, temporary, true)).thenReturn(stored);

		when(postFileMapper.insertPostFiles(anyList())).thenAnswer(invocation -> {
			List<PostFile> rows = invocation.getArgument(0);
			ReflectionTestUtils.setField(rows.get(0), "id", 11L);
			return rows.size();
		});

		when(postMapper.updateContentDocument(eq(1L), eq(100L), any(JsonNode.class))).thenReturn(1);
		when(postMapper.refreshRepresentativeImage(1L, 100L)).thenReturn(1);
		when(postConverter.toCreateResponse(post)).thenReturn(new PostCreateResponse(100L));

		PostCreateResponse response = postService.createPost("free", request, List.of(image), 10L, false);

		assertEquals(100L, response.id());

		ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
		verify(postMapper).updateContentDocument(eq(1L), eq(100L), captor.capture());

		JsonNode savedImage = captor.getValue().at("/content/0/content/0");
		assertEquals("image", savedImage.path("type").asString());
		assertEquals(11L, savedImage.path("attrs").path("fileId").longValue());
		assertFalse(savedImage.path("attrs").has("fileIndex"));

		verify(postFileMapper).insertPostFiles(argThat(
			rows -> rows.size() == 1 && "BODY_IMAGE".equals(rows.get(0).getFileUsage()) && Integer.valueOf(0)
				.equals(rows.get(0).getSortOrder())));
		verify(postMapper).refreshRepresentativeImage(1L, 100L);
	}

	private MockMultipartFile createImage(String name) throws Exception {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", output);
		return new MockMultipartFile("files", name, "image/png", output.toByteArray());
	}

	private BoardPolicy filePolicy(boolean imageRequired) {
		return BoardPolicy.builder()
			.readPermission("PUBLIC")
			.writePermission("MEMBER")
			.secretMode("OPTIONAL")
			.commentsEnabled(true)
			.attachmentsEnabled(true)
			.uploadProfile("IMAGE_AND_ZIP")
			.maxFileCount(3)
			.maxFileSizeBytes(1024L)
			.imageRequired(imageRequired)
			.thumbnailEnabled(true)
			.build();
	}

	private BoardPolicy readPolicy(String readPermission) {
		return BoardPolicy.builder().readPermission(readPermission).build();
	}

	private void allowActiveMember() {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);
		when(memberMapper.existsActiveSanctionByMemberId(10L)).thenReturn(false);
	}

	private PostCreateRequest request(boolean secret, String password) {
		return new PostCreateRequest("제목", document("본문"), null, secret, password, false);
	}

	private JsonNode document(String text, int... imageIndexes) {
		ObjectNode root = JsonNodeFactory.instance.objectNode();
		root.put("type", "doc");
		var blocks = root.putArray("content");

		if (!text.isEmpty()) {
			ObjectNode paragraph = blocks.addObject();
			paragraph.put("type", "paragraph");
			paragraph.putArray("content").addObject().put("type", "text").put("text", text);
		}

		for (int index : imageIndexes) {
			ObjectNode image = blocks.addObject();
			image.put("type", "image");
			image.putObject("attrs").put("fileIndex", index);
		}

		return root;
	}

	private JsonNode existingImageDocument(String text, Long... fileIds) {
		ObjectNode root = (ObjectNode)document(text);
		ArrayNode blocks = (ArrayNode)root.path("content");

		for (Long fileId : fileIds) {
			ObjectNode image = blocks.addObject();
			image.put("type", "image");
			image.putObject("attrs").put("fileId", fileId);
		}

		return root;
	}

	private BoardPolicy policy(String writePermission, String secretMode, boolean categoriesEnabled,
		boolean categoryRequired) {
		return BoardPolicy.builder()
			.readPermission("PUBLIC")
			.writePermission(writePermission)
			.secretMode(secretMode)
			.categoriesEnabled(categoriesEnabled)
			.categoryRequired(categoryRequired)
			.commentsEnabled(true)
			.imageRequired(false)
			.build();
	}

	private PostDetail postDetail(boolean secret, String passwordHash) {
		return postDetail(10L, secret, passwordHash, 0);
	}

	private PostDetail postDetail(Long authorId, boolean secret, String passwordHash, int version) {
		LocalDateTime createdAt = LocalDateTime.of(2026, 9, 21, 10, 0);

		return new PostDetail(100L, 1L, "게시글 제목", "게시글 본문", authorId, "작성자", null, null, secret, passwordHash, false,
			false, 5L, version, createdAt, createdAt, null, null);
	}

	private PostDetailResponse detailResponse(long likeCount, boolean likedByMe) {
		LocalDateTime createdAt = LocalDateTime.of(2026, 9, 21, 10, 0);

		return new PostDetailResponse(
			100L, 1L, "게시글 제목", "게시글 본문",
			10L, "작성자", null, null, false, false, false,
			6L, 0, createdAt, createdAt, null, List.of(), null,
			likeCount, likedByMe
		);
	}

	private PostListRequest defaultPostListRequest() {
		return new PostListRequest(null, null, PostSort.LATEST);
	}

	private void prepareBoard(String boardSlug) {
		Board board = Board.builder().id(1L).slug(boardSlug).build();

		when(boardMapper.selectActiveBoardBySlug(boardSlug)).thenReturn(Optional.of(board));
	}

	private void prepareBoard(String boardSlug, BoardPolicy policy) {
		prepareBoard(boardSlug);

		when(boardMapper.selectPolicyByBoardId(1L)).thenReturn(Optional.of(policy));
	}
}
