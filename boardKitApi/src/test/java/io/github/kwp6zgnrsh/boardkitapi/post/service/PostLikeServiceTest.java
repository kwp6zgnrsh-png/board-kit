package io.github.kwp6zgnrsh.boardkitapi.post.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.board.exception.BoardAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.board.mapper.BoardMapper;
import io.github.kwp6zgnrsh.boardkitapi.board.model.Board;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardPolicy;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberSanctionedException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.SecretPostAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostLikeMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostDetail;
import io.github.kwp6zgnrsh.boardkitapi.post.policy.PostSecretAccess;

@ExtendWith(MockitoExtension.class)
class PostLikeServiceTest {

	@Mock
	private BoardMapper boardMapper;
	@Mock
	private PostMapper postMapper;
	@Mock
	private PostLikeMapper postLikeMapper;
	@Mock
	private MemberMapper memberMapper;
	@Mock
	private PasswordEncoder passwordEncoder;

	private PostLikeService postLikeService;

	@BeforeEach
	void setUp() {
		postLikeService = new PostLikeService(
			boardMapper, postMapper, postLikeMapper, memberMapper,
			new PostSecretAccess(passwordEncoder)
		);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("일반 글과 비밀번호 없는 비밀글에는 비밀번호 없이 좋아요를 등록한다")
	void likePostWithoutPasswordWhenNoPasswordHashExists(boolean secret) {
		prepareBoard("PUBLIC", true);
		preparePost(20L, secret, null);

		postLikeService.likePost("free", 100L, 10L, false, null);

		verify(postLikeMapper).insertPostLike(100L, 10L);
		verify(postMapper, never()).increaseViewCount(anyLong(), anyLong());
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("좋아요 기능이 꺼지면 등록과 취소를 모두 거절한다")
	void rejectLikeAndUnlikeWhenLikesAreDisabled() {
		prepareBoard("PUBLIC", false);

		ResponseStatusException likeException = assertThrows(ResponseStatusException.class,
			() -> postLikeService.likePost("free", 100L, 10L, false, null));
		ResponseStatusException unlikeException = assertThrows(ResponseStatusException.class,
			() -> postLikeService.unlikePost("free", 100L, 10L, false, null));

		assertEquals(HttpStatus.FORBIDDEN, likeException.getStatusCode());
		assertEquals(HttpStatus.FORBIDDEN, unlikeException.getStatusCode());
		verifyNoInteractions(postMapper, postLikeMapper);
	}

	@Test
	@DisplayName("제재 중에는 좋아요 등록을 거절하지만 취소는 허용한다")
	void rejectLikeButAllowUnlikeForSanctionedMember() {
		prepareBoard("PUBLIC", true);
		preparePost(20L, false, null);
		when(memberMapper.existsActiveSanctionByMemberId(10L)).thenReturn(true);

		assertThrows(MemberSanctionedException.class, () -> postLikeService.likePost("free", 100L, 10L, false, null));

		postLikeService.unlikePost("free", 100L, 10L, false, null);

		verify(postLikeMapper, never()).insertPostLike(anyLong(), anyLong());
		verify(postLikeMapper).deletePostLike(100L, 10L);
		verify(memberMapper, times(1)).existsActiveSanctionByMemberId(10L);
	}

	@Test
	@DisplayName("활성 회원이 아니면 좋아요 등록과 취소를 모두 거절한다")
	void rejectLikeAndUnlikeForUnavailableMember() {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(false);

		assertThrows(MemberUnavailableException.class, () -> postLikeService.likePost("free", 100L, 10L, false, null));
		assertThrows(MemberUnavailableException.class,
			() -> postLikeService.unlikePost("free", 100L, 10L, false, null));

		verifyNoInteractions(boardMapper, postMapper, postLikeMapper);
	}

	@Test
	@DisplayName("게시판 읽기 권한이 없으면 좋아요 등록과 취소를 모두 거절한다")
	void rejectLikeAndUnlikeWithoutBoardReadPermission() {
		prepareBoard("ADMIN", true);

		assertThrows(BoardAccessDeniedException.class, () -> postLikeService.likePost("free", 100L, 10L, false, null));
		assertThrows(BoardAccessDeniedException.class,
			() -> postLikeService.unlikePost("free", 100L, 10L, false, null));

		verifyNoInteractions(postMapper, postLikeMapper);
	}

	@Test
	@DisplayName("대상 게시글이 없으면 좋아요 등록과 취소를 모두 거절한다")
	void rejectLikeAndUnlikeWhenPostIsMissing() {
		prepareBoard("PUBLIC", true);
		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.empty());

		assertThrows(PostNotFoundException.class, () -> postLikeService.likePost("free", 100L, 10L, false, null));
		assertThrows(PostNotFoundException.class, () -> postLikeService.unlikePost("free", 100L, 10L, false, null));

		verifyNoInteractions(postLikeMapper);
	}

	@Test
	@DisplayName("비밀글 비밀번호가 틀리면 좋아요 등록과 취소를 모두 거절한다")
	void rejectLikeAndUnlikeWithWrongSecretPassword() {
		prepareBoard("PUBLIC", true);
		preparePost(20L, true, "hash");
		when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

		assertThrows(SecretPostAccessDeniedException.class,
			() -> postLikeService.likePost("free", 100L, 10L, false, "wrong"));
		assertThrows(SecretPostAccessDeniedException.class,
			() -> postLikeService.unlikePost("free", 100L, 10L, false, "wrong"));

		verifyNoInteractions(postLikeMapper);
	}

	@Test
	@DisplayName("비밀글 비밀번호가 맞으면 좋아요 등록과 취소를 허용한다")
	void allowLikeAndUnlikeWithCorrectSecretPassword() {
		prepareBoard("PUBLIC", true);
		preparePost(20L, true, "hash");
		when(passwordEncoder.matches("correct", "hash")).thenReturn(true);

		postLikeService.likePost("free", 100L, 10L, false, "correct");
		postLikeService.unlikePost("free", 100L, 10L, false, "correct");

		verify(postLikeMapper).insertPostLike(100L, 10L);
		verify(postLikeMapper).deletePostLike(100L, 10L);
		verify(passwordEncoder, times(2)).matches("correct", "hash");
		verify(postMapper, never()).increaseViewCount(anyLong(), anyLong());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("작성자와 관리자는 비밀글 비밀번호 없이 좋아요 등록과 취소를 할 수 있다")
	void allowAuthorAndAdminWithoutSecretPassword(boolean adminAccess) {
		prepareBoard(adminAccess ? "ADMIN" : "PUBLIC", true);
		preparePost(adminAccess ? 20L : 10L, true, "hash");

		postLikeService.likePost("free", 100L, 10L, adminAccess, null);
		postLikeService.unlikePost("free", 100L, 10L, adminAccess, null);

		verify(postLikeMapper).insertPostLike(100L, 10L);
		verify(postLikeMapper).deletePostLike(100L, 10L);
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("비밀글 비밀번호가 없거나 너무 길면 암호 비교 전에 거절한다")
	void rejectMissingOrOversizedSecretPassword() {
		prepareBoard("PUBLIC", true);
		preparePost(20L, true, "hash");

		assertThrows(SecretPostAccessDeniedException.class,
			() -> postLikeService.likePost("free", 100L, 10L, false, null));
		assertThrows(SecretPostAccessDeniedException.class,
			() -> postLikeService.unlikePost("free", 100L, 10L, false, "가".repeat(25)));

		verifyNoInteractions(passwordEncoder, postLikeMapper);
	}

	private void prepareBoard(String readPermission, boolean likesEnabled) {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);

		Board board = Board.builder().id(1L).slug("free").build();

		BoardPolicy policy = BoardPolicy.builder()
			.boardId(1L)
			.readPermission(readPermission)
			.postLikesEnabled(likesEnabled)
			.build();

		when(boardMapper.selectActiveBoardBySlug("free")).thenReturn(Optional.of(board));
		when(boardMapper.selectPolicyByBoardId(1L)).thenReturn(Optional.of(policy));
	}

	private void preparePost(Long authorId, boolean secret, String passwordHash) {
		LocalDateTime createdAt = LocalDateTime.of(2026, 10, 15, 12, 0);

		PostDetail post = new PostDetail(100L, 1L, "제목", "본문", authorId, "작성자", null, null, secret, passwordHash, false,
			false, 0L, 0, createdAt, createdAt, null, null);

		when(postMapper.selectActivePostDetail(1L, 100L)).thenReturn(Optional.of(post));
	}
}