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
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.InvalidPostRequestException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostWriteDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.model.Post;

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

	private void prepareBoard(String boardSlug, BoardPolicy policy) {
		Board board = Board.builder()
			.id(1L)
			.slug(boardSlug)
			.build();

		when(boardMapper.selectActiveBoardBySlug(boardSlug))
			.thenReturn(Optional.of(board));
		when(boardMapper.selectPolicyByBoardId(1L))
			.thenReturn(Optional.of(policy));
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
}