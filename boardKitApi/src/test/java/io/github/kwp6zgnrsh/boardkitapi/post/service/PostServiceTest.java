package io.github.kwp6zgnrsh.boardkitapi.post.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.kwp6zgnrsh.boardkitapi.board.exception.BoardAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.board.mapper.BoardMapper;
import io.github.kwp6zgnrsh.boardkitapi.board.model.Board;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardPolicy;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberSanctionedException;
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
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostWriteDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.SecretPostAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.model.Post;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostDetail;

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

	@BeforeEach
	void setUp() {
		postService = new PostService(
			postMapper,
			boardMapper,
			memberMapper,
			postConverter,
			passwordEncoder
		);
	}

	@Test
	@DisplayName("유효한 요청이면 게시글을 저장한다")
	void createPostWithValidRequest() {
		PostCreateRequest request = request(false, null);
		BoardPolicy policy = policy("MEMBER", "OPTIONAL", false, false);
		Post post = Post.builder()
			.id(100L)
			.boardId(1L)
			.authorId(10L)
			.title("제목")
			.content("본문")
			.build();

		allowActiveMember();
		prepareBoard("free",policy);

		when(postConverter.toPost(request, 1L, 10L, null))
			.thenReturn(post);
		when(postMapper.insertPost(post))
			.thenReturn(1);
		when(postConverter.toCreateResponse(post))
			.thenReturn(new PostCreateResponse(100L));

		PostCreateResponse response = postService.createPost("free", request, 10L, false);

		assertEquals(100L, response.id());
		verify(postMapper).insertPost(post);
	}

	@Test
	@DisplayName("제재 중인 회원은 게시글을 작성할 수 없다")
	void rejectPostCreationForSanctionedMember() {
		when(memberMapper.existsActiveMemberById(10L))
			.thenReturn(true);
		when(memberMapper.existsActiveSanctionByMemberId(10L))
			.thenReturn(true);

		assertThrows(
			MemberSanctionedException.class,
			() -> postService.createPost(
				"free",
				request(false, null),
				10L,
				false
			)
		);

		verifyNoInteractions(boardMapper, postMapper, postConverter);
	}

	@Test
	@DisplayName("관리자 전용 게시판에는 일반 회원이 글을 작성할 수 없다")
	void rejectMemberWhenBoardRequiresAdmin() {
		allowActiveMember();
		prepareBoard("admin",policy("ADMIN", "OFF", false, false));

		assertThrows(
			PostWriteDeniedException.class,
			() -> postService.createPost(
				"admin",
				request(false, null),
				10L,
				false
			)
		);

		verifyNoInteractions(postMapper, postConverter);
	}

	@Test
	@DisplayName("필수 카테고리를 선택하지 않으면 게시글을 작성할 수 없다")
	void rejectPostWhenRequiredCategoryIsMissing() {
		allowActiveMember();
		prepareBoard("free", policy("MEMBER", "OFF", true, true));

		assertThrows(
			InvalidPostRequestException.class,
			() -> postService.createPost(
				"free",
				request(false, null),
				10L,
				false
			)
		);

		verifyNoInteractions(postMapper, postConverter);
	}

	@Test
	@DisplayName("비밀글을 지원하지 않는 게시판에는 비밀글을 작성할 수 없다")
	void rejectSecretPostWhenSecretModeIsOff() {
		allowActiveMember();
		prepareBoard("free",policy("MEMBER", "OFF", false, false));

		assertThrows(
			InvalidPostRequestException.class,
			() -> postService.createPost(
				"free",
				request(true, "1234"),
				10L,
				false
			)
		);

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
				11L,
				"게시글 제목",
				10L,
				"작성자",
				null,
				null,
				false,
				false,
				3L,
				2L,
				createdAt
			)
		);

		when(postMapper.countActivePostsByBoardId(1L))
			.thenReturn(21L);
		when(postMapper.selectPostSummaries(1L, 10, 10L))
			.thenReturn(posts);

		PostPageResponse response = postService.getPosts(
			"free",
			2,
			10,
			false,
			false
		);

		assertAll(
			() -> assertEquals(posts, response.posts()),
			() -> assertEquals(2, response.page()),
			() -> assertEquals(10, response.size()),
			() -> assertEquals(21L, response.totalElements()),
			() -> assertEquals(3, response.totalPages())
		);

		verify(postMapper).selectPostSummaries(1L, 10, 10L);
	}

	@Test
	@DisplayName("일반 회원은 관리자 전용 게시판 목록을 조회할 수 없다")
	void rejectMemberFromAdminBoardPostList() {
		prepareBoard("admin", readPolicy("ADMIN"));

		assertThrows(
			BoardAccessDeniedException.class,
			() -> postService.getPosts(
				"admin",
				1,
				10,
				true,
				false
			)
		);

		verifyNoInteractions(postMapper);
	}

	@Test
	@DisplayName("마지막 페이지를 넘으면 빈 게시글 목록을 반환한다")
	void returnEmptyPostsWhenPageExceedsLastPage() {
		prepareBoard("free", readPolicy("PUBLIC"));

		when(postMapper.countActivePostsByBoardId(1L))
			.thenReturn(11L);

		PostPageResponse response = postService.getPosts(
			"free",
			3,
			10,
			false,
			false
		);

		assertAll(
			() -> assertTrue(response.posts().isEmpty()),
			() -> assertEquals(3, response.page()),
			() -> assertEquals(11L, response.totalElements()),
			() -> assertEquals(2, response.totalPages())
		);

		verify(postMapper).countActivePostsByBoardId(1L);
		verifyNoMoreInteractions(postMapper);
	}

	@Test
	@DisplayName("일반 게시글 상세를 조회하고 조회수를 증가시킨다")
	void getRegularPostDetailAndIncreaseViewCount() {
		PostDetail postDetail = postDetail(false, null);
		PostDetailResponse expected = detailResponse();

		prepareBoard("free", readPolicy("PUBLIC"));

		when(postMapper.selectActivePostDetail(1L, 100L))
			.thenReturn(Optional.of(postDetail));
		when(postMapper.increaseViewCount(1L, 100L))
			.thenReturn(1);
		when(postConverter.toDetailResponse(postDetail, 6L))
			.thenReturn(expected);

		PostDetailResponse response =
			postService.getPostDetail(
				"free",
				100L,
				null,
				false,
				false,
				null
			);

		assertEquals(expected, response);
		verify(postMapper).increaseViewCount(1L, 100L);
	}

	@Test
	@DisplayName("비밀번호가 없는 비밀글은 누구나 조회할 수 있다")
	void allowPasswordlessSecretPostForAnyone() {
		PostDetail postDetail = postDetail(true, null);
		PostDetailResponse expected = detailResponse();

		prepareBoard("free", readPolicy("PUBLIC"));

		when(postMapper.selectActivePostDetail(1L, 100L))
			.thenReturn(Optional.of(postDetail));
		when(postMapper.increaseViewCount(1L, 100L))
			.thenReturn(1);
		when(postConverter.toDetailResponse(postDetail, 6L))
			.thenReturn(expected);

		PostDetailResponse response =
			postService.getPostDetail(
				"free",
				100L,
				null,
				false,
				false,
				null
			);

		assertEquals(expected, response);
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("비밀글 비밀번호가 일치하면 게시글을 조회할 수 있다")
	void allowSecretPostWithCorrectPassword() {
		PostDetail postDetail = postDetail(true, "encoded-password");
		PostDetailResponse expected = detailResponse();

		prepareBoard("free", readPolicy("PUBLIC"));

		when(postMapper.selectActivePostDetail(1L, 100L))
			.thenReturn(Optional.of(postDetail));
		when(passwordEncoder.matches(
			"1234",
			"encoded-password"
		)).thenReturn(true);
		when(postMapper.increaseViewCount(1L, 100L))
			.thenReturn(1);
		when(postConverter.toDetailResponse(postDetail, 6L))
			.thenReturn(expected);

		PostDetailResponse response =
			postService.getPostDetail(
				"free",
				100L,
				null,
				false,
				false,
				"1234"
			);

		assertEquals(expected, response);
		verify(passwordEncoder).matches(
			"1234",
			"encoded-password"
		);
	}

	@Test
	@DisplayName("비밀글 비밀번호가 일치하지 않으면 조회할 수 없다")
	void rejectSecretPostWithWrongPassword() {
		PostDetail postDetail = postDetail(true, "encoded-password");

		prepareBoard("free", readPolicy("PUBLIC"));

		when(postMapper.selectActivePostDetail(1L, 100L))
			.thenReturn(Optional.of(postDetail));
		when(passwordEncoder.matches(
			"wrong-password",
			"encoded-password"
		)).thenReturn(false);

		assertThrows(
			SecretPostAccessDeniedException.class,
			() -> postService.getPostDetail(
				"free",
				100L,
				null,
				false,
				false,
				"wrong-password"
			)
		);

		verify(postMapper, never())
			.increaseViewCount(1L, 100L);
		verifyNoInteractions(postConverter);
	}

	@Test
	@DisplayName("작성자는 비밀번호 없이 자신의 비밀글을 조회할 수 있다")
	void allowAuthorToReadSecretPostWithoutPassword() {
		PostDetail postDetail = postDetail(true, "encoded-password");
		PostDetailResponse expected = detailResponse();

		prepareBoard("free", readPolicy("PUBLIC"));

		when(postMapper.selectActivePostDetail(1L, 100L))
			.thenReturn(Optional.of(postDetail));
		when(postMapper.increaseViewCount(1L, 100L))
			.thenReturn(1);
		when(postConverter.toDetailResponse(postDetail, 6L))
			.thenReturn(expected);

		PostDetailResponse response =
			postService.getPostDetail(
				"free",
				100L,
				10L,
				true,
				false,
				null
			);

		assertEquals(expected, response);
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("작성자는 기존 비밀글 비밀번호를 유지하며 게시글을 수정할 수 있다")
	void updateOwnPostAndKeepSecretPassword() {
		PostUpdateRequest request = new PostUpdateRequest(
			"수정 제목",
			"수정 본문",
			null,
			true,
			null,
			false,
			false,
			0
		);

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
		prepareBoard(
			"free",
			policy("MEMBER", "OPTIONAL", false, false)
		);

		when(postMapper.selectActivePostDetail(1L, 100L))
			.thenReturn(Optional.of(currentPost));
		when(postConverter.toUpdatedPost(
			request,
			100L,
			1L,
			10L,
			"existing-hash"
		)).thenReturn(updatedPost);
		when(postMapper.updatePost(updatedPost))
			.thenReturn(1);

		postService.updatePost(
			"free",
			100L,
			request,
			10L,
			false
		);

		verify(postMapper).updatePost(updatedPost);
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("비밀글 정책을 끈 뒤에도 기존 비밀글의 비밀번호를 유지하며 수정할 수 있다")
	void updateExistingSecretPostWhenSecretModeIsOff() {
		PostUpdateRequest request = new PostUpdateRequest(
			"수정 제목", "수정 본문", null, true, null, false, false, 0
		);
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
		when(postMapper.selectActivePostDetail(1L, 100L))
			.thenReturn(Optional.of(currentPost));
		when(postConverter.toUpdatedPost(request, 100L, 1L, 10L, "existing-hash"))
			.thenReturn(updatedPost);
		when(postMapper.updatePost(updatedPost)).thenReturn(1);

		postService.updatePost("free", 100L, request, 10L, false);

		verify(postMapper).updatePost(updatedPost);
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("비밀글 정책이 꺼져 있으면 일반 글을 비밀글로 전환할 수 없다")
	void rejectSecretConversionWhenSecretModeIsOff() {
		PostUpdateRequest request = new PostUpdateRequest(
			"수정 제목", "수정 본문", null, true, null, false, false, 0
		);

		allowActiveMember();
		prepareBoard("free", policy("MEMBER", "OFF", false, false));
		when(postMapper.selectActivePostDetail(1L, 100L))
			.thenReturn(Optional.of(postDetail(10L, false, null, 0)));

		assertThrows(
			InvalidPostRequestException.class,
			() -> postService.updatePost("free", 100L, request, 10L, false)
		);

		verify(postMapper, never()).updatePost(any());
		verifyNoInteractions(postConverter);
	}

	@Test
	@DisplayName("비밀글 정책이 꺼져 있어도 작성자가 기존 비밀글을 명시적으로 공개할 수 있다")
	void publishExistingSecretPostWhenSecretModeIsOff() {
		PostUpdateRequest request = new PostUpdateRequest(
			"수정 제목", "수정 본문", null, false, null, false, false, 0
		);
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
		when(postMapper.selectActivePostDetail(1L, 100L))
			.thenReturn(Optional.of(currentPost));
		when(postConverter.toUpdatedPost(request, 100L, 1L, 10L, null))
			.thenReturn(updatedPost);
		when(postMapper.updatePost(updatedPost)).thenReturn(1);

		postService.updatePost("free", 100L, request, 10L, false);

		verify(postMapper).updatePost(updatedPost);
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("작성자가 아니면 게시글을 수정할 수 없다")
	void rejectPostUpdateByNonAuthor() {
		PostUpdateRequest request = new PostUpdateRequest(
			"수정 제목",
			"수정 본문",
			null,
			false,
			null,
			false,
			false,
			0
		);

		allowActiveMember();
		prepareBoard(
			"free",
			policy("MEMBER", "OPTIONAL", false, false)
		);

		when(postMapper.selectActivePostDetail(1L, 100L))
			.thenReturn(Optional.of(postDetail(20L, false, null, 0)));

		assertThrows(
			PostEditDeniedException.class,
			() -> postService.updatePost(
				"free",
				100L,
				request,
				10L,
				false
			)
		);

		verify(postMapper, never()).updatePost(any());
		verifyNoInteractions(postConverter);
	}

	@Test
	@DisplayName("이전 버전으로 게시글을 수정하면 충돌이 발생한다")
	void rejectPostUpdateWithStaleVersion() {
		PostUpdateRequest request = new PostUpdateRequest(
			"수정 제목",
			"수정 본문",
			null,
			false,
			null,
			false,
			false,
			1
		);

		allowActiveMember();
		prepareBoard(
			"free",
			policy("MEMBER", "OPTIONAL", false, false)
		);

		when(postMapper.selectActivePostDetail(1L, 100L))
			.thenReturn(Optional.of(
				postDetail(10L, false, null, 0)
			));

		assertThrows(
			PostConflictException.class,
			() -> postService.updatePost(
				"free",
				100L,
				request,
				10L,
				false
			)
		);

		verify(postMapper, never()).updatePost(any());
		verifyNoInteractions(postConverter);
	}

	@Test
	@DisplayName("관리자는 다른 회원의 게시글을 삭제할 수 있다")
	void deletePostByAdmin() {
		when(memberMapper.existsActiveMemberById(1L))
			.thenReturn(true);

		prepareBoard("free");

		when(postMapper.selectActivePostDetail(1L, 100L))
			.thenReturn(Optional.of(
				postDetail(10L, false, null, 0)
			));
		when(postMapper.softDeletePost(1L, 100L, 0))
			.thenReturn(1);

		postService.deletePost(
			"free",
			100L,
			0,
			1L,
			true
		);

		verify(postMapper).softDeletePost(1L, 100L, 0);
		verify(
			memberMapper,
			never()
		).existsActiveSanctionByMemberId(anyLong());
	}

	@Test
	@DisplayName("작성자도 관리자가 아니면 게시글을 삭제할 수 없다")
	void rejectPostDeletionByNonAuthor() {
		when(memberMapper.existsActiveMemberById(10L))
			.thenReturn(true);

		prepareBoard("free");

		when(postMapper.selectActivePostDetail(1L, 100L))
			.thenReturn(Optional.of(
				postDetail(20L, false, null, 0)
			));

		assertThrows(
			PostDeleteDeniedException.class,
			() -> postService.deletePost(
				"free",
				100L,
				0,
				10L,
				false
			)
		);

		verify(postMapper, never())
			.softDeletePost(anyLong(), anyLong(), anyInt());
	}

	private BoardPolicy readPolicy(String readPermission) {
		return BoardPolicy.builder()
			.readPermission(readPermission)
			.build();
	}

	private void allowActiveMember() {
		when(memberMapper.existsActiveMemberById(10L))
			.thenReturn(true);
		when(memberMapper.existsActiveSanctionByMemberId(10L))
			.thenReturn(false);
	}

	private PostCreateRequest request(boolean secret, String password) {
		return new PostCreateRequest(
			"제목",
			"본문",
			null,
			secret,
			password,
			false
		);
	}

	private BoardPolicy policy(String writePermission,
							   String secretMode,
							   boolean categoriesEnabled,
							   boolean categoryRequired) {
		return BoardPolicy.builder()
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
		LocalDateTime createdAt =
			LocalDateTime.of(2026, 9, 21, 10, 0);

		return new PostDetail(
			100L,
			1L,
			"게시글 제목",
			"게시글 본문",
			authorId,
			"작성자",
			null,
			null,
			secret,
			passwordHash,
			false,
			false,
			5L,
			version,
			createdAt,
			createdAt
		);
	}

	private PostDetailResponse detailResponse() {
		LocalDateTime createdAt = LocalDateTime.of(2026, 9, 21, 10, 0);

		return new PostDetailResponse(
			100L,
			1L,
			"게시글 제목",
			"게시글 본문",
			10L,
			"작성자",
			null,
			null,
			false,
			false,
			false,
			6L,
			0,
			createdAt,
			createdAt
		);
	}

	private void prepareBoard(String boardSlug) {
		Board board = Board.builder()
			.id(1L)
			.slug(boardSlug)
			.build();

		when(boardMapper.selectActiveBoardBySlug(boardSlug))
			.thenReturn(Optional.of(board));
	}

	private void prepareBoard(String boardSlug, BoardPolicy policy) {
		prepareBoard(boardSlug);

		when(boardMapper.selectPolicyByBoardId(1L))
			.thenReturn(Optional.of(policy));
	}
}
