package io.github.kwp6zgnrsh.boardkitapi.report.service;

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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.board.exception.BoardAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.board.mapper.BoardMapper;
import io.github.kwp6zgnrsh.boardkitapi.board.model.Board;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardPolicy;
import io.github.kwp6zgnrsh.boardkitapi.comment.exception.CommentNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.common.exception.BlindedContentAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.SecretPostAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostCommentTarget;
import io.github.kwp6zgnrsh.boardkitapi.post.policy.PostSecretAccess;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.report.exception.DuplicateReportException;
import io.github.kwp6zgnrsh.boardkitapi.report.mapper.ReportMapper;
import io.github.kwp6zgnrsh.boardkitapi.report.model.Report;
import io.github.kwp6zgnrsh.boardkitapi.report.model.ReportReason;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

	@Mock
	private BoardMapper boardMapper;
	@Mock
	private MemberMapper memberMapper;
	@Mock
	private ReportMapper reportMapper;
	@Mock
	private PasswordEncoder passwordEncoder;

	private ReportService service;

	@BeforeEach
	void setUp() {
		service = new ReportService(boardMapper, memberMapper, reportMapper, new PostSecretAccess(passwordEncoder));
	}

	@Test
	@DisplayName("접근 가능한 게시글을 신고하고 생성 ID를 반환한다")
	void createPostReportWithAuthenticatedMember() {
		prepareBoard("PUBLIC");
		preparePost(20L, false, null);
		allowInsert();

		Long id = service.createPostReport("free", 100L, request(), 10L, false, null);

		ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
		verify(reportMapper).insertReport(captor.capture());
		Report saved = captor.getValue();

		assertAll(() -> assertEquals(Long.valueOf(300L), id),
			() -> assertEquals(Long.valueOf(10L), saved.getReporterId()),
			() -> assertEquals(Long.valueOf(100L), saved.getPostId()), () -> assertNull(saved.getCommentId()),
			() -> assertEquals(ReportReason.SPAM, saved.getReason()),
			() -> assertEquals("광고가 반복됩니다.", saved.getDetail()));
	}

	@Test
	@DisplayName("댓글 사용 OFF와 작성 잠금 상태에서도 자기 글의 다른 사람 댓글을 신고할 수 있다")
	void reportOthersCommentOnOwnPostRegardlessOfCommentSettings() {
		prepareBoard("PUBLIC");
		preparePost(10L, false, null);
		when(reportMapper.selectActiveCommentAuthorForShare(100L, 50L)).thenReturn(Optional.of(20L));
		allowInsert();

		Long id = service.createCommentReport("free", 100L, 50L, request(), 10L, false, null);

		ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
		verify(reportMapper).insertReport(captor.capture());

		assertAll(() -> assertEquals(Long.valueOf(300L), id), () -> assertNull(captor.getValue().getPostId()),
			() -> assertEquals(Long.valueOf(50L), captor.getValue().getCommentId()));
	}

	@Test
	@DisplayName("자기 게시글은 신고할 수 없다")
	void rejectReportForOwnPost() {
		prepareBoard("PUBLIC");
		preparePost(10L, false, null);

		assertForbidden(() -> service.createPostReport("free", 100L, request(), 10L, false, null));

		verify(reportMapper, never()).insertReport(any(Report.class));
	}

	@Test
	@DisplayName("자기 댓글은 신고할 수 없다")
	void rejectReportForOwnComment() {
		prepareBoard("PUBLIC");
		preparePost(20L, false, null);
		when(reportMapper.selectActiveCommentAuthorForShare(100L, 50L)).thenReturn(Optional.of(10L));

		assertForbidden(() -> service.createCommentReport("free", 100L, 50L, request(), 10L, false, null));

		verify(reportMapper, never()).insertReport(any(Report.class));
	}

	@Test
	@DisplayName("활성 회원이 아니면 신고할 수 없다")
	void rejectReportForUnavailableMember() {
		when(boardMapper.selectActiveBoardBySlugForShare("free")).thenReturn(Optional.of(board()));
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(false);

		assertThrows(MemberUnavailableException.class,
			() -> service.createPostReport("free", 100L, request(), 10L, false, null));

		verifyNoInteractions(reportMapper);
	}

	@Test
	@DisplayName("제재 중인 회원은 신고할 수 없다")
	void rejectReportForSanctionedMember() {
		when(boardMapper.selectActiveBoardBySlugForShare("free")).thenReturn(Optional.of(board()));
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);
		when(memberMapper.existsActiveSanctionByMemberId(10L)).thenReturn(true);

		assertForbidden(() -> service.createPostReport("free", 100L, request(), 10L, false, null));

		verifyNoInteractions(reportMapper);
	}

	@Test
	@DisplayName("게시판 읽기 권한이 없으면 신고할 수 없다")
	void rejectReportWithoutBoardReadPermission() {
		prepareBoard("ADMIN");

		assertThrows(BoardAccessDeniedException.class,
			() -> service.createPostReport("free", 100L, request(), 10L, false, null));

		verifyNoInteractions(reportMapper);
	}

	@Test
	@DisplayName("게시글이 없거나 삭제되었으면 신고할 수 없다")
	void rejectReportWhenPostIsUnavailable() {
		prepareBoard("PUBLIC");
		when(reportMapper.selectActivePostForShare(1L, 100L)).thenReturn(Optional.empty());

		assertThrows(PostNotFoundException.class,
			() -> service.createPostReport("free", 100L, request(), 10L, false, null));

		verify(reportMapper, never()).insertReport(any(Report.class));
	}

	@Test
	@DisplayName("댓글이 없거나 삭제되었거나 소속이 다르면 신고할 수 없다")
	void rejectReportWhenCommentIsUnavailable() {
		prepareBoard("PUBLIC");
		preparePost(20L, false, null);
		when(reportMapper.selectActiveCommentAuthorForShare(100L, 50L)).thenReturn(Optional.empty());

		assertThrows(CommentNotFoundException.class,
			() -> service.createCommentReport("free", 100L, 50L, request(), 10L, false, null));

		verify(reportMapper, never()).insertReport(any(Report.class));
	}

	@Test
	@DisplayName("비밀글 비밀번호가 틀리면 댓글 조회와 신고 저장 전에 거절한다")
	void rejectCommentReportWithWrongSecretPassword() {
		prepareBoard("PUBLIC");
		preparePost(20L, true, "hash");
		when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

		assertThrows(SecretPostAccessDeniedException.class,
			() -> service.createCommentReport("free", 100L, 50L, request(), 10L, false, "wrong"));

		verify(reportMapper, never()).selectActiveCommentAuthorForShare(anyLong(), anyLong());
		verify(reportMapper, never()).insertReport(any(Report.class));
	}

	@Test
	@DisplayName("DB 중복 신고 예외는 중복 신고 오류로 변환한다")
	void translateDuplicateReportFailure() {
		prepareBoard("PUBLIC");
		preparePost(20L, false, null);
		when(reportMapper.insertReport(any(Report.class))).thenThrow(new DuplicateKeyException("duplicate"));

		assertThrows(DuplicateReportException.class,
			() -> service.createPostReport("free", 100L, request(), 10L, false, null));
	}

	@Test
	@DisplayName("DB 장애를 중복 신고 오류로 바꾸지 않는다")
	void propagateDatabaseFailure() {
		prepareBoard("PUBLIC");
		preparePost(20L, false, null);

		var failure = new DataAccessResourceFailureException("database unavailable");
		when(reportMapper.insertReport(any(Report.class))).thenThrow(failure);

		assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
			() -> service.createPostReport("free", 100L, request(), 10L, false, null)));
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1})
	@DisplayName("저장 건수가 잘못되거나 생성 ID가 없으면 실패한다")
	void rejectInvalidInsertionResult(int insertedCount) {
		prepareBoard("PUBLIC");
		preparePost(20L, false, null);
		when(reportMapper.insertReport(any(Report.class))).thenReturn(insertedCount);

		assertThrows(IllegalStateException.class,
			() -> service.createPostReport("free", 100L, request(), 10L, false, null));
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("블라인드 게시글에는 관리자도 게시글과 댓글 신고를 추가할 수 없다")
	void rejectReportsOnBlindedPost(boolean adminAccess) {
		prepareBoard("PUBLIC");
		when(reportMapper.selectActivePostForShare(1L, 100L)).thenReturn(
			Optional.of(new PostCommentTarget(20L, true, "hash", false, LocalDateTime.now())));

		assertThrows(BlindedContentAccessDeniedException.class,
			() -> service.createPostReport("free", 100L, request(), 10L, adminAccess, null));
		assertThrows(BlindedContentAccessDeniedException.class,
			() -> service.createCommentReport("free", 100L, 50L, request(), 10L, adminAccess, null));

		verify(reportMapper, never()).selectActiveCommentAuthorForShare(anyLong(), anyLong());
		verify(reportMapper, never()).insertReport(any(Report.class));
		verifyNoInteractions(passwordEncoder);
	}

	private void prepareBoard(String readPermission) {
		when(boardMapper.selectActiveBoardBySlugForShare("free")).thenReturn(Optional.of(board()));
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);
		when(boardMapper.selectPolicyByBoardId(1L)).thenReturn(Optional.of(BoardPolicy.builder()
			.boardId(1L)
			.readPermission(readPermission)
			.commentsEnabled(false)
			.commentRepliesEnabled(false)
			.build()));
	}

	private void preparePost(Long authorId, boolean secret, String passwordHash) {
		when(reportMapper.selectActivePostForShare(1L, 100L)).thenReturn(
			Optional.of(new PostCommentTarget(authorId, secret, passwordHash, true, null)));
	}

	private void allowInsert() {
		when(reportMapper.insertReport(any(Report.class))).thenAnswer(invocation -> {
			Report report = invocation.getArgument(0);
			ReflectionTestUtils.setField(report, "id", 300L);
			return 1;
		});
	}

	private Board board() {
		return Board.builder().id(1L).slug("free").status("ACTIVE").build();
	}

	private ReportCreateRequest request() {
		return new ReportCreateRequest(ReportReason.SPAM, "광고가 반복됩니다.");
	}

	private void assertForbidden(org.junit.jupiter.api.function.Executable action) {
		ResponseStatusException exception = assertThrows(ResponseStatusException.class, action);
		assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
	}
}