package io.github.kwp6zgnrsh.boardkitapi.comment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import io.github.kwp6zgnrsh.boardkitapi.comment.exception.CommentNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.comment.mapper.CommentLikeMapper;
import io.github.kwp6zgnrsh.boardkitapi.comment.mapper.CommentMapper;
import io.github.kwp6zgnrsh.boardkitapi.common.exception.BlindedContentAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberSanctionedException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.SecretPostAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostCommentTarget;
import io.github.kwp6zgnrsh.boardkitapi.post.policy.PostSecretAccess;

@ExtendWith(MockitoExtension.class)
class CommentLikeServiceTest {

	@Mock
	private BoardMapper boardMapper;
	@Mock
	private PostMapper postMapper;
	@Mock
	private CommentMapper commentMapper;
	@Mock
	private CommentLikeMapper commentLikeMapper;
	@Mock
	private MemberMapper memberMapper;
	@Mock
	private PasswordEncoder passwordEncoder;

	private CommentLikeService commentLikeService;

	@BeforeEach
	void setUp() {
		commentLikeService = new CommentLikeService(boardMapper, postMapper, commentMapper, commentLikeMapper,
			memberMapper, new PostSecretAccess(passwordEncoder));
	}

	@ParameterizedTest(name = "secret={0}, locked={1}")
	@CsvSource({"false, false", "false, true", "true, false"})
	@DisplayName("일반 글과 비밀번호 없는 비밀글에서 댓글 잠금과 별개로 댓글 좋아요를 변경한다")
	void allowCommentLikesWithoutSecretPassword(boolean secret, boolean locked) {
		prepareBoard("PUBLIC", true);
		preparePost(20L, secret, null, locked);
		prepareComment();

		commentLikeService.likeComment("free", 100L, 50L, 10L, false, null);
		commentLikeService.unlikeComment("free", 100L, 50L, 10L, false, null);

		verify(commentLikeMapper).insertCommentLike(50L, 10L);
		verify(commentLikeMapper).deleteCommentLike(50L, 10L);
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("댓글 좋아요 기능이 꺼지면 등록과 취소를 모두 거절한다")
	void rejectLikeAndUnlikeWhenLikesAreDisabled() {
		prepareBoard("PUBLIC", false);

		ResponseStatusException likeException = assertThrows(ResponseStatusException.class,
			() -> commentLikeService.likeComment("free", 100L, 50L, 10L, false, null));
		ResponseStatusException unlikeException = assertThrows(ResponseStatusException.class,
			() -> commentLikeService.unlikeComment("free", 100L, 50L, 10L, false, null));

		assertEquals(HttpStatus.FORBIDDEN, likeException.getStatusCode());
		assertEquals(HttpStatus.FORBIDDEN, unlikeException.getStatusCode());
		verifyNoInteractions(postMapper, commentMapper, commentLikeMapper);
	}

	@Test
	@DisplayName("제재 중에는 댓글 좋아요 등록을 거절하지만 취소는 허용한다")
	void rejectLikeButAllowUnlikeForSanctionedMember() {
		prepareBoard("PUBLIC", true);
		preparePost(20L, false, null, false);
		prepareComment();
		when(memberMapper.existsActiveSanctionByMemberId(10L)).thenReturn(true);

		assertThrows(MemberSanctionedException.class,
			() -> commentLikeService.likeComment("free", 100L, 50L, 10L, false, null));

		commentLikeService.unlikeComment("free", 100L, 50L, 10L, false, null);

		verify(commentLikeMapper, never()).insertCommentLike(anyLong(), anyLong());
		verify(commentLikeMapper).deleteCommentLike(50L, 10L);
		verify(memberMapper, times(1)).existsActiveSanctionByMemberId(10L);
	}

	@Test
	@DisplayName("활성 회원이 아니면 댓글 좋아요 등록과 취소를 모두 거절한다")
	void rejectLikeAndUnlikeForUnavailableMember() {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(false);

		assertThrows(MemberUnavailableException.class,
			() -> commentLikeService.likeComment("free", 100L, 50L, 10L, false, null));
		assertThrows(MemberUnavailableException.class,
			() -> commentLikeService.unlikeComment("free", 100L, 50L, 10L, false, null));

		verifyNoInteractions(boardMapper, postMapper, commentMapper, commentLikeMapper);
	}

	@Test
	@DisplayName("게시판 읽기 권한이 없으면 댓글 좋아요 등록과 취소를 모두 거절한다")
	void rejectLikeAndUnlikeWithoutReadPermission() {
		prepareBoard("ADMIN", true);

		assertThrows(BoardAccessDeniedException.class,
			() -> commentLikeService.likeComment("free", 100L, 50L, 10L, false, null));
		assertThrows(BoardAccessDeniedException.class,
			() -> commentLikeService.unlikeComment("free", 100L, 50L, 10L, false, null));

		verifyNoInteractions(postMapper, commentMapper, commentLikeMapper);
	}

	@Test
	@DisplayName("대상 게시글이 없으면 댓글 좋아요 등록과 취소를 모두 거절한다")
	void rejectLikeAndUnlikeWhenPostIsMissing() {
		prepareBoard("PUBLIC", true);
		when(postMapper.selectActiveCommentTargetForShare(1L, 100L)).thenReturn(Optional.empty());

		assertThrows(PostNotFoundException.class,
			() -> commentLikeService.likeComment("free", 100L, 50L, 10L, false, null));
		assertThrows(PostNotFoundException.class,
			() -> commentLikeService.unlikeComment("free", 100L, 50L, 10L, false, null));

		verifyNoInteractions(commentMapper, commentLikeMapper);
	}

	@Test
	@DisplayName("사용할 수 없는 댓글에는 좋아요 등록과 취소를 모두 거절한다")
	void rejectLikeAndUnlikeWhenCommentIsUnavailable() {
		prepareBoard("PUBLIC", true);
		preparePost(20L, false, null, false);
		when(commentMapper.selectActiveLikeTargetForShare(1L, 100L, 50L)).thenReturn(Optional.empty());

		assertThrows(CommentNotFoundException.class,
			() -> commentLikeService.likeComment("free", 100L, 50L, 10L, false, null));
		assertThrows(CommentNotFoundException.class,
			() -> commentLikeService.unlikeComment("free", 100L, 50L, 10L, false, null));

		verifyNoInteractions(commentLikeMapper);
	}

	@Test
	@DisplayName("비밀글 비밀번호가 틀리면 댓글 좋아요 등록과 취소를 모두 거절한다")
	void rejectLikeAndUnlikeWithWrongSecretPassword() {
		prepareBoard("PUBLIC", true);
		preparePost(20L, true, "hash", false);
		when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

		assertThrows(SecretPostAccessDeniedException.class,
			() -> commentLikeService.likeComment("free", 100L, 50L, 10L, false, "wrong"));
		assertThrows(SecretPostAccessDeniedException.class,
			() -> commentLikeService.unlikeComment("free", 100L, 50L, 10L, false, "wrong"));

		verifyNoInteractions(commentMapper, commentLikeMapper);
	}

	@Test
	@DisplayName("비밀글 비밀번호가 맞으면 댓글 좋아요 등록과 취소를 허용한다")
	void allowLikeAndUnlikeWithCorrectSecretPassword() {
		prepareBoard("PUBLIC", true);
		preparePost(20L, true, "hash", false);
		prepareComment();
		when(passwordEncoder.matches("correct", "hash")).thenReturn(true);

		commentLikeService.likeComment("free", 100L, 50L, 10L, false, "correct");
		commentLikeService.unlikeComment("free", 100L, 50L, 10L, false, "correct");

		verify(commentLikeMapper).insertCommentLike(50L, 10L);
		verify(commentLikeMapper).deleteCommentLike(50L, 10L);
		verify(passwordEncoder, times(2)).matches("correct", "hash");
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("게시글 작성자와 관리자는 비밀번호 없이 댓글 좋아요를 변경한다")
	void allowPostAuthorAndAdminWithoutSecretPassword(boolean adminAccess) {
		prepareBoard(adminAccess ? "ADMIN" : "PUBLIC", true);
		preparePost(adminAccess ? 20L : 10L, true, "hash", false);
		prepareComment();

		commentLikeService.likeComment("free", 100L, 50L, 10L, adminAccess, null);
		commentLikeService.unlikeComment("free", 100L, 50L, 10L, adminAccess, null);

		verify(commentLikeMapper).insertCommentLike(50L, 10L);
		verify(commentLikeMapper).deleteCommentLike(50L, 10L);
		verifyNoInteractions(passwordEncoder);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("블라인드 게시글에서는 작성자와 관리자도 댓글 좋아요를 추가하거나 취소할 수 없다")
	void rejectCommentLikesOnBlindedPost(boolean adminAccess) {
		prepareBoard("PUBLIC", true);
		when(postMapper.selectActiveCommentTargetForShare(1L, 100L)).thenReturn(
			Optional.of(new PostCommentTarget(10L, true, "hash", false, LocalDateTime.now())));

		assertThrows(BlindedContentAccessDeniedException.class,
			() -> commentLikeService.likeComment("free", 100L, 50L, 10L, adminAccess, null));
		assertThrows(BlindedContentAccessDeniedException.class,
			() -> commentLikeService.unlikeComment("free", 100L, 50L, 10L, adminAccess, null));

		verifyNoInteractions(commentMapper, commentLikeMapper, passwordEncoder);
	}

	private void prepareBoard(String readPermission, boolean likesEnabled) {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);

		Board board = Board.builder().id(1L).slug("free").build();
		BoardPolicy policy = BoardPolicy.builder()
			.boardId(1L)
			.readPermission(readPermission)
			.commentsEnabled(true)
			.commentRepliesEnabled(true)
			.commentLikesEnabled(likesEnabled)
			.build();

		when(boardMapper.selectActiveBoardBySlug("free")).thenReturn(Optional.of(board));
		when(boardMapper.selectPolicyByBoardId(1L)).thenReturn(Optional.of(policy));
	}

	private void preparePost(Long authorId, boolean secret, String passwordHash, boolean locked) {
		when(postMapper.selectActiveCommentTargetForShare(1L, 100L)).thenReturn(
			Optional.of(new PostCommentTarget(authorId, secret, passwordHash, locked, null)));
	}

	private void prepareComment() {
		when(commentMapper.selectActiveLikeTargetForShare(1L, 100L, 50L)).thenReturn(Optional.of(50L));
	}
}