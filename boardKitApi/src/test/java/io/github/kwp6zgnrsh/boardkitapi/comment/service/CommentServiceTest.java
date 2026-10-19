package io.github.kwp6zgnrsh.boardkitapi.comment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
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

import io.github.kwp6zgnrsh.boardkitapi.board.mapper.BoardMapper;
import io.github.kwp6zgnrsh.boardkitapi.board.model.Board;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardPolicy;
import io.github.kwp6zgnrsh.boardkitapi.comment.converter.CommentConverter;
import io.github.kwp6zgnrsh.boardkitapi.comment.dto.request.CommentCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.comment.dto.request.CommentUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.comment.dto.response.CommentCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.comment.dto.response.CommentItemResponse;
import io.github.kwp6zgnrsh.boardkitapi.comment.dto.response.CommentPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.comment.exception.CommentConflictException;
import io.github.kwp6zgnrsh.boardkitapi.comment.exception.CommentDeleteDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.comment.exception.CommentEditDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.comment.mapper.CommentMapper;
import io.github.kwp6zgnrsh.boardkitapi.comment.model.Comment;
import io.github.kwp6zgnrsh.boardkitapi.common.exception.BlindedContentAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.SecretPostAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostCommentTarget;
import io.github.kwp6zgnrsh.boardkitapi.post.policy.PostSecretAccess;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

	@Mock
	private BoardMapper boardMapper;
	@Mock
	private PostMapper postMapper;
	@Mock
	private CommentMapper commentMapper;
	@Mock
	private MemberMapper memberMapper;
	@Mock
	private CommentConverter commentConverter;
	@Mock
	private PasswordEncoder passwordEncoder;

	private CommentService commentService;

	@BeforeEach
	void setUp() {
		commentService = new CommentService(boardMapper, postMapper, commentMapper, memberMapper, commentConverter,
			new PostSecretAccess(passwordEncoder));
	}

	@Test
	@DisplayName("댓글 사용을 꺼도 기존 댓글은 조회할 수 있다")
	void readExistingCommentsWhenCommentsAreDisabled() {
		prepareReadableBoard(false, false);
		when(postMapper.selectActiveCommentTarget(1L, 100L)).thenReturn(
			Optional.of(new PostCommentTarget(20L, false, null, true, null)));
		when(commentMapper.countActiveComments(100L)).thenReturn(1L);

		CommentItemResponse item = new CommentItemResponse(50L, null, 10L, "작성자", "기존 댓글", false, 0,
			LocalDateTime.of(2026, 9, 25, 10, 0), LocalDateTime.of(2026, 9, 25, 10, 0), 0L, false, false);

		when(commentMapper.selectActiveComments(100L, 20, 0L, null, false)).thenReturn(List.of(item));

		CommentPageResponse response = commentService.getComments("free", 100L, 1, 20, null, false, false, null);

		assertEquals(1, response.totalElements());
		assertEquals("기존 댓글", response.comments().getFirst().content());
	}

	@Test
	@DisplayName("댓글 사용을 끄면 새 댓글 작성은 거절한다")
	void rejectCommentCreationWhenCommentsAreDisabled() {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);
		prepareBoard(false);
		when(postMapper.selectActiveCommentTargetForUpdate(1L, 100L)).thenReturn(
			Optional.of(new PostCommentTarget(20L, false, null, false, null)));

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
			() -> commentService.createComment("free", 100L, new CommentCreateRequest("새 댓글", null), 10L, false, null));

		assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
		verify(commentMapper, never()).insertComment(any());
	}

	@Test
	@DisplayName("비밀번호를 모르면 비밀글의 댓글을 조회할 수 없다")
	void rejectSecretPostCommentsWithoutPassword() {
		prepareReadableBoard(true, false);
		when(postMapper.selectActiveCommentTarget(1L, 100L)).thenReturn(
			Optional.of(new PostCommentTarget(20L, true, "hash", false, null)));

		assertThrows(SecretPostAccessDeniedException.class,
			() -> commentService.getComments("free", 100L, 1, 20, null, false, false, null));

		verifyNoInteractions(commentMapper);
	}

	@Test
	@DisplayName("유효한 댓글은 저장하고 생성된 ID를 반환한다")
	void createCommentWithValidRequest() {
		CommentCreateRequest request = new CommentCreateRequest("새 댓글", null);
		Comment comment = Comment.builder().id(50L).postId(100L).authorId(10L).content("새 댓글").build();

		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);
		prepareBoard(true);
		when(postMapper.selectActiveCommentTargetForUpdate(1L, 100L)).thenReturn(
			Optional.of(new PostCommentTarget(20L, false, null, false, null)));
		when(commentConverter.toComment(request, 100L, 10L)).thenReturn(comment);
		when(commentMapper.insertComment(comment)).thenReturn(1);
		when(commentConverter.toCreateResponse(comment)).thenReturn(new CommentCreateResponse(50L));

		CommentCreateResponse response = commentService.createComment("free", 100L, request, 10L, false, null);

		assertEquals(50L, response.id());
		verify(commentMapper).insertComment(comment);
	}

	@Test
	@DisplayName("댓글 사용 중이고 잠기지 않았다면 작성자는 자기 댓글을 수정할 수 있다")
	void updateOwnCommentWhenCommentWritingIsAllowed() {
		CommentUpdateRequest request = new CommentUpdateRequest("수정된 댓글", 0);

		prepareCommentUpdate(Comment.builder().id(50L).postId(100L).authorId(10L).content("기존 댓글").version(0).build(),
			true, false);

		when(commentMapper.updateComment(1L, 100L, 50L, 10L, "수정된 댓글", 0)).thenReturn(1);

		commentService.updateComment("free", 100L, 50L, request, 10L, false, null);

		verify(commentMapper).updateComment(1L, 100L, 50L, 10L, "수정된 댓글", 0);
	}

	@Test
	@DisplayName("댓글 작성이 잠긴 게시글에서는 기존 댓글도 수정할 수 없다")
	void rejectCommentUpdateWhenCommentsAreLocked() {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);

		prepareBoard(true);

		when(postMapper.selectActiveCommentTargetForUpdate(1L, 100L)).thenReturn(
			Optional.of(new PostCommentTarget(20L, false, null, true, null)));

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
			() -> commentService.updateComment("free", 100L, 50L, new CommentUpdateRequest("수정 시도", 0), 10L, false,
				null));

		assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

		verify(commentMapper, never()).selectActiveComment(anyLong(), anyLong(), anyLong());

		verify(commentMapper, never()).updateComment(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyInt());
	}

	@Test
	@DisplayName("작성자가 아닌 회원은 댓글을 수정할 수 없다")
	void rejectCommentUpdateByNonAuthor() {
		CommentUpdateRequest request = new CommentUpdateRequest("수정 시도", 0);

		prepareCommentUpdate(Comment.builder().id(50L).postId(100L).authorId(20L).content("기존 댓글").version(0).build(),
			true, false);

		assertThrows(CommentEditDeniedException.class,
			() -> commentService.updateComment("free", 100L, 50L, request, 10L, false, null));

		verify(commentMapper, never()).updateComment(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyInt());
	}

	@Test
	@DisplayName("요청 버전이 현재 댓글 버전과 다르면 수정을 거절한다")
	void rejectCommentUpdateWithStaleVersion() {
		CommentUpdateRequest request = new CommentUpdateRequest("수정 시도", 0);

		prepareCommentUpdate(
			Comment.builder().id(50L).postId(100L).authorId(10L).content("다른 요청이 수정한 댓글").version(1).build(), true,
			false);

		assertThrows(CommentConflictException.class,
			() -> commentService.updateComment("free", 100L, 50L, request, 10L, false, null));

		verify(commentMapper, never()).updateComment(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyInt());
	}

	@Test
	@DisplayName("댓글 조회 후 다른 요청이 먼저 수정하면 충돌로 처리한다")
	void rejectCommentUpdateWhenConcurrentUpdateWins() {
		CommentUpdateRequest request = new CommentUpdateRequest("수정 시도", 0);

		prepareCommentUpdate(Comment.builder().id(50L).postId(100L).authorId(10L).content("기존 댓글").version(0).build(),
			true, false);

		when(commentMapper.updateComment(1L, 100L, 50L, 10L, "수정 시도", 0)).thenReturn(0);

		assertThrows(CommentConflictException.class,
			() -> commentService.updateComment("free", 100L, 50L, request, 10L, false, null));
	}

	@ParameterizedTest(name = "memberId={0}, authorId={1}, version={2}, adminAccess={3}")
	@CsvSource({"10, 10, 2, false", "1, 10, 0, true"})
	@DisplayName("작성자와 관리자는 현재 버전으로 댓글을 삭제할 수 있다")
	void deleteCommentByAuthorOrAdmin(Long memberId, Long authorId, int version, boolean adminAccess) {
		prepareCommentDeletion(memberId, authorId, version);
		when(commentMapper.softDeleteComment(1L, 100L, 50L, version)).thenReturn(1);

		commentService.deleteComment("free", 100L, 50L, version, memberId, adminAccess);

		verify(commentMapper).softDeleteComment(1L, 100L, 50L, version);
		verify(memberMapper, never()).existsActiveSanctionByMemberId(anyLong());
	}

	@Test
	@DisplayName("작성자가 아닌 일반 회원은 댓글을 삭제할 수 없다")
	void rejectCommentDeletionByNonAuthor() {
		prepareCommentDeletion(20L, 10L, 0);

		assertThrows(CommentDeleteDeniedException.class,
			() -> commentService.deleteComment("free", 100L, 50L, 0, 20L, false));

		verify(commentMapper, never()).softDeleteComment(anyLong(), anyLong(), anyLong(), anyInt());
	}

	@Test
	@DisplayName("요청 버전이 현재 댓글 버전과 다르면 삭제를 거절한다")
	void rejectCommentDeletionWithStaleVersion() {
		prepareCommentDeletion(10L, 10L, 1);

		assertThrows(CommentConflictException.class,
			() -> commentService.deleteComment("free", 100L, 50L, 0, 10L, false));

		verify(commentMapper, never()).softDeleteComment(anyLong(), anyLong(), anyLong(), anyInt());
	}

	@Test
	@DisplayName("댓글 조회 후 다른 요청이 먼저 변경하면 삭제 충돌로 처리한다")
	void rejectCommentDeletionWhenConcurrentChangeWins() {
		prepareCommentDeletion(10L, 10L, 0);

		when(commentMapper.softDeleteComment(1L, 100L, 50L, 0)).thenReturn(0);

		assertThrows(CommentConflictException.class,
			() -> commentService.deleteComment("free", 100L, 50L, 0, 10L, false));
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	@DisplayName("댓글 기능이 꺼져도 기존 댓글의 좋아요 정보와 회원별 선택 상태를 조회한다")
	void getExistingCommentLikesWhenCommentsAreDisabled(boolean likedByMe) {
		prepareReadableBoard(false, false);

		when(postMapper.selectActiveCommentTarget(1L, 100L)).thenReturn(
			Optional.of(new PostCommentTarget(20L, false, null, true, null)));
		when(commentMapper.countActiveComments(100L)).thenReturn(1L);

		LocalDateTime createdAt = LocalDateTime.of(2026, 10, 17, 12, 0);

		CommentItemResponse item = new CommentItemResponse(50L, null, 10L, "작성자", "기존 댓글", false, 0, createdAt,
			createdAt, 3L, likedByMe, false);

		when(commentMapper.selectActiveComments(100L, 20, 0L, 10L, false)).thenReturn(List.of(item));

		CommentPageResponse response = commentService.getComments("free", 100L, 1, 20, 10L, true, false, null);

		assertEquals(3L, response.comments().getFirst().likeCount());
		assertEquals(likedByMe, response.comments().getFirst().likedByMe());

		verify(commentMapper).selectActiveComments(100L, 20, 0L, 10L, false);
	}

	@ParameterizedTest
	@CsvSource(value = {"null", "10"}, nullValues = "null")
	@DisplayName("다른 사용자는 비밀번호를 알아도 블라인드된 게시글의 댓글을 조회할 수 없다")
	void rejectCommentsOfBlindedPost(Long memberId) {
		prepareReadableBoard(true, false);
		when(postMapper.selectActiveCommentTarget(1L, 100L)).thenReturn(
			Optional.of(new PostCommentTarget(20L, true, "hash", false, LocalDateTime.now())));

		assertThrows(BlindedContentAccessDeniedException.class,
			() -> commentService.getComments("free", 100L, 1, 20, memberId, memberId != null, false,
				"correct-password"));

		verifyNoInteractions(commentMapper, passwordEncoder);
	}

	@ParameterizedTest
	@CsvSource({"20, false", "10, true"})
	@DisplayName("게시글 작성자와 관리자는 블라인드된 게시글의 댓글을 조회할 수 있다")
	void allowCommentsOfBlindedPostForAuthorOrAdmin(Long memberId, boolean adminAccess) {
		prepareReadableBoard(true, adminAccess);
		when(postMapper.selectActiveCommentTarget(1L, 100L)).thenReturn(
			Optional.of(new PostCommentTarget(20L, true, "hash", false, LocalDateTime.now())));
		when(commentMapper.countActiveComments(100L)).thenReturn(1L);

		LocalDateTime createdAt = LocalDateTime.of(2026, 10, 18, 12, 0);
		CommentItemResponse item = new CommentItemResponse(50L, null, 30L, "댓글 작성자", "일반 댓글", false, 0, createdAt,
			createdAt, 0L, false, false);
		when(commentMapper.selectActiveComments(100L, 20, 0L, memberId, adminAccess)).thenReturn(List.of(item));

		var response = commentService.getComments("free", 100L, 1, 20, memberId, true, adminAccess, null);

		assertEquals(List.of(item), response.comments());
		verify(commentMapper).selectActiveComments(100L, 20, 0L, memberId, adminAccess);
		verifyNoInteractions(passwordEncoder);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("게시글 작성자와 관리자도 블라인드된 게시글에 댓글을 작성하거나 수정할 수 없다")
	void rejectCommentWritingOnBlindedPost(boolean adminAccess) {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);
		prepareBoard(true);
		when(postMapper.selectActiveCommentTargetForUpdate(1L, 100L)).thenReturn(
			Optional.of(new PostCommentTarget(10L, true, "hash", false, LocalDateTime.now())));

		assertThrows(BlindedContentAccessDeniedException.class,
			() -> commentService.createComment("free", 100L, new CommentCreateRequest("댓글", null), 10L, adminAccess,
				null));
		assertThrows(BlindedContentAccessDeniedException.class,
			() -> commentService.updateComment("free", 100L, 50L, new CommentUpdateRequest("수정", 0), 10L, adminAccess,
				null));

		verifyNoInteractions(commentMapper, commentConverter, passwordEncoder);
	}

	@Test
	@DisplayName("게시글이 정상이더라도 블라인드된 댓글 자체는 수정할 수 없다")
	void rejectUpdateOfBlindedComment() {
		Comment comment = Comment.builder()
			.id(50L)
			.postId(100L)
			.authorId(10L)
			.content("기존 댓글")
			.version(0)
			.blindedAt(LocalDateTime.now())
			.build();

		prepareCommentUpdate(comment, true, false);

		assertThrows(BlindedContentAccessDeniedException.class,
			() -> commentService.updateComment("free", 100L, 50L, new CommentUpdateRequest("수정", 0), 10L, false, null));

		verify(commentMapper, never()).updateComment(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyInt());
	}

	private void prepareBoard(boolean commentsEnabled) {
		Board board = Board.builder().id(1L).slug("free").build();
		BoardPolicy policy = BoardPolicy.builder()
			.readPermission("PUBLIC")
			.commentPermission("MEMBER")
			.commentsEnabled(commentsEnabled)
			.build();

		when(boardMapper.selectActiveBoardBySlug("free")).thenReturn(Optional.of(board));
		when(boardMapper.selectPolicyByBoardId(1L)).thenReturn(Optional.of(policy));
	}

	private void prepareCommentUpdate(Comment comment, boolean commentsEnabled, boolean commentsLocked) {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);

		prepareBoard(commentsEnabled);

		when(postMapper.selectActiveCommentTargetForUpdate(1L, 100L)).thenReturn(
			Optional.of(new PostCommentTarget(20L, false, null, commentsLocked, null)));

		when(commentMapper.selectActiveComment(1L, 100L, 50L)).thenReturn(Optional.of(comment));
	}

	private void prepareCommentDeletion(Long memberId, Long authorId, Integer version) {
		when(memberMapper.existsActiveMemberById(memberId)).thenReturn(true);

		Board board = Board.builder().id(1L).slug("free").build();

		when(boardMapper.selectActiveBoardBySlug("free")).thenReturn(Optional.of(board));

		Comment comment = Comment.builder()
			.id(50L)
			.postId(100L)
			.authorId(authorId)
			.content("댓글")
			.version(version)
			.build();

		when(commentMapper.selectActiveComment(1L, 100L, 50L)).thenReturn(Optional.of(comment));
	}

	private void prepareReadableBoard(boolean commentsEnabled, boolean adminAccess) {
		Board board = Board.builder().id(1L).slug("free").status("ACTIVE").build();
		BoardPolicy policy = BoardPolicy.builder()
			.readPermission("PUBLIC")
			.commentPermission("MEMBER")
			.commentsEnabled(commentsEnabled)
			.build();
		when(boardMapper.selectReadableBoardBySlug("free", adminAccess)).thenReturn(Optional.of(board));
		when(boardMapper.selectPolicyByBoardId(1L)).thenReturn(Optional.of(policy));
	}
}
