package io.github.kwp6zgnrsh.boardkitapi.report.service;

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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.comment.mapper.CommentMapper;
import io.github.kwp6zgnrsh.boardkitapi.comment.model.Comment;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.model.Post;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportReviewRequest;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportStatusFilter;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminReportDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminReportSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.exception.ReportNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.report.exception.ReportReviewConflictException;
import io.github.kwp6zgnrsh.boardkitapi.report.mapper.ReportMapper;
import io.github.kwp6zgnrsh.boardkitapi.report.model.ReportReason;
import io.github.kwp6zgnrsh.boardkitapi.report.model.ReportStatus;

@ExtendWith(MockitoExtension.class)
class AdminReportServiceTest {

	@Mock
	private MemberMapper memberMapper;
	@Mock
	private ReportMapper reportMapper;
	@Mock
	private PostMapper postMapper;
	@Mock
	private CommentMapper commentMapper;
	@Mock
	private AdminContentModerationService adminContentModerationService;

	private AdminReportService service;

	@BeforeEach
	void setUp() {
		service = new AdminReportService(
			memberMapper, reportMapper, postMapper, commentMapper, adminContentModerationService
		);
	}

	@Test
	@DisplayName("관리자는 선택한 필터와 페이지로 신고 목록을 조회한다")
	void getReportsWithFilterAndPagination() {
		allowAdmin();

		var report = new AdminReportSummaryResponse(100L, 200L, null, "free", "자유게시판", "ARCHIVED", "게시글 제목", "신고자",
			ReportReason.SPAM, ReportStatus.PENDING, true, false, LocalDateTime.of(2026, 10, 18, 12, 0));

		when(reportMapper.countAdminReports(ReportStatusFilter.PENDING)).thenReturn(11L);
		when(reportMapper.selectAdminReports(ReportStatusFilter.PENDING, 10, 10L)).thenReturn(List.of(report));

		var result = service.getReports(ReportStatusFilter.PENDING, 2, 10, 10L, true);

		assertAll(() -> assertEquals(List.of(report), result.reports()), () -> assertEquals(2, result.page()),
			() -> assertEquals(10, result.size()), () -> assertEquals(11L, result.totalElements()),
			() -> assertEquals(2, result.totalPages()));
	}

	@ParameterizedTest
	@CsvSource({"0, 1, 0", "10, 2, 1"})
	@DisplayName("결과가 없거나 마지막 페이지를 넘으면 목록 쿼리 없이 빈 페이지를 반환한다")
	void returnEmptyPageWithoutListQuery(long total, int page, int totalPages) {
		allowAdmin();
		when(reportMapper.countAdminReports(ReportStatusFilter.ALL)).thenReturn(total);

		var result = service.getReports(ReportStatusFilter.ALL, page, 10, 10L, true);

		assertAll(() -> assertTrue(result.reports().isEmpty()), () -> assertEquals(page, result.page()),
			() -> assertEquals(total, result.totalElements()), () -> assertEquals(totalPages, result.totalPages()));

		verify(reportMapper, never()).selectAdminReports(any(ReportStatusFilter.class), anyInt(), anyLong());
	}

	@Test
	@DisplayName("관리자는 신고 상세 조회 결과를 받는다")
	void getReportDetailAsAdmin() {
		allowAdmin();
		var detail = mock(AdminReportDetailResponse.class);
		when(reportMapper.selectAdminReport(100L)).thenReturn(Optional.of(detail));

		assertSame(detail, service.getReport(100L, 10L, true));
	}

	@Test
	@DisplayName("없는 신고 상세 조회는 찾을 수 없음으로 처리한다")
	void rejectMissingReportDetail() {
		allowAdmin();
		when(reportMapper.selectAdminReport(100L)).thenReturn(Optional.empty());

		assertThrows(ReportNotFoundException.class, () -> service.getReport(100L, 10L, true));
	}

	@Test
	@DisplayName("관리자가 아니면 신고 목록과 상세 조회 및 처리를 거절한다")
	void rejectAllOperationsByNonAdmin() {
		assertForbidden(() -> service.getReports(ReportStatusFilter.PENDING, 1, 10, 10L, false));
		assertForbidden(() -> service.getReport(100L, 10L, false));
		assertForbidden(() -> service.reviewReport(100L, request(), 10L, false));

		verifyNoInteractions(memberMapper, reportMapper);
	}

	@Test
	@DisplayName("활성 회원이 아니면 관리자 권한이 있어도 신고 관리 기능을 거절한다")
	void rejectAllOperationsForUnavailableAdmin() {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(false);

		assertThrows(MemberUnavailableException.class,
			() -> service.getReports(ReportStatusFilter.PENDING, 1, 10, 10L, true));
		assertThrows(MemberUnavailableException.class, () -> service.getReport(100L, 10L, true));
		assertThrows(MemberUnavailableException.class, () -> service.reviewReport(100L, request(), 10L, true));

		verifyNoInteractions(reportMapper);
	}

	@ParameterizedTest
	@EnumSource(value = ReportStatus.class, names = {"ACCEPTED", "REJECTED"})
	@DisplayName("관리자는 처리 결과와 메모 및 본인 ID로 신고를 처리한다")
	void reviewReportWithAdminIdentity(ReportStatus status) {
		allowAdmin();
		prepareReview(postTarget(0L), null);

		when(reportMapper.reviewReport(100L, status, "확인했습니다.", 10L)).thenReturn(1);

		if (status == ReportStatus.ACCEPTED) {
			when(reportMapper.countAcceptedPostReports(200L)).thenReturn(2L);
		}

		service.reviewReport(100L, new ReportReviewRequest(status, "확인했습니다."), 10L, true);

		verify(reportMapper).reviewReport(100L, status, "확인했습니다.", 10L);
		verify(postMapper, never()).blindPost(anyLong(), anyInt());
		verify(memberMapper, never()).existsActiveSanctionByMemberId(anyLong());

		if (status == ReportStatus.REJECTED) {
			verify(reportMapper, never()).countAcceptedPostReports(anyLong());
		}
	}

	@Test
	@DisplayName("없는 신고는 처리할 수 없다")
	void rejectReviewForMissingReport() {
		allowAdmin();
		when(reportMapper.selectAdminReport(100L)).thenReturn(Optional.empty());

		assertThrows(ReportNotFoundException.class, () -> service.reviewReport(100L, request(), 10L, true));

		verifyNoInteractions(postMapper, commentMapper);
		verify(reportMapper, never()).reviewReport(anyLong(), any(ReportStatus.class), anyString(), anyLong());
	}

	@Test
	@DisplayName("다른 관리자가 먼저 처리한 신고는 충돌로 처리한다")
	void rejectReviewWhenAnotherAdminHasProcessedReport() {
		allowAdmin();
		prepareReview(postTarget(0L), null);
		when(reportMapper.reviewReport(100L, ReportStatus.ACCEPTED, "확인했습니다.", 10L)).thenReturn(0);

		assertThrows(ReportReviewConflictException.class, () -> service.reviewReport(100L, request(), 10L, true));

		verify(reportMapper, never()).countAcceptedPostReports(anyLong());
		verify(postMapper, never()).blindPost(anyLong(), anyInt());
	}

	@Test
	@DisplayName("신고 처리 중 DB 장애를 처리 충돌로 바꾸지 않는다")
	void propagateDatabaseFailureDuringReview() {
		allowAdmin();
		prepareReview(postTarget(0L), null);

		var failure = new DataAccessResourceFailureException("database unavailable");
		when(reportMapper.reviewReport(100L, ReportStatus.ACCEPTED, "확인했습니다.", 10L)).thenThrow(failure);

		assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
			() -> service.reviewReport(100L, request(), 10L, true)));

		verify(reportMapper, never()).countAcceptedPostReports(anyLong());
	}

	@ParameterizedTest
	@CsvSource({"0, 0, false", "0, 2, false", "0, 3, true", "0, 4, true", "5, 7, false", "5, 8, true"})
	@DisplayName("게시글은 해제 기준 이후 인정 신고가 3건 이상이면 블라인드한다")
	void blindPostWhenAcceptedReportsReachThreshold(long baseline, long acceptedCount, boolean expectedBlind) {
		allowAdmin();
		prepareReview(postTarget(baseline), null);
		allowSuccessfulReview();

		when(reportMapper.countAcceptedPostReports(200L)).thenReturn(acceptedCount);

		if (expectedBlind) {
			when(postMapper.blindPost(200L, 4)).thenReturn(1);
		}

		service.reviewReport(100L, request(), 10L, true);

		var order = inOrder(postMapper, reportMapper);
		order.verify(postMapper).selectPostForModerationForUpdate(200L);
		order.verify(reportMapper).reviewReport(100L, ReportStatus.ACCEPTED, "확인했습니다.", 10L);
		order.verify(reportMapper).countAcceptedPostReports(200L);

		verify(postMapper, times(expectedBlind ? 1 : 0)).blindPost(200L, 4);
		verifyNoInteractions(commentMapper);
	}

	@ParameterizedTest
	@CsvSource({"0, 2, false", "0, 3, true", "5, 7, false", "5, 8, true"})
	@DisplayName("댓글은 게시글과 별도로 인정 신고 기준을 계산해 블라인드한다")
	void blindCommentWhenAcceptedReportsReachThreshold(long baseline, long acceptedCount, boolean expectedBlind) {
		allowAdmin();
		prepareReview(postTarget(0L), commentTarget(baseline));
		allowSuccessfulReview();

		when(reportMapper.countAcceptedCommentReports(300L)).thenReturn(acceptedCount);

		if (expectedBlind) {
			when(commentMapper.blindComment(200L, 300L, 7)).thenReturn(1);
		}

		service.reviewReport(100L, request(), 10L, true);

		var order = inOrder(postMapper, commentMapper, reportMapper);
		order.verify(postMapper).selectPostForModerationForUpdate(200L);
		order.verify(commentMapper).selectCommentForModerationForUpdate(200L, 300L);
		order.verify(reportMapper).reviewReport(100L, ReportStatus.ACCEPTED, "확인했습니다.", 10L);
		order.verify(reportMapper).countAcceptedCommentReports(300L);

		verify(commentMapper, times(expectedBlind ? 1 : 0)).blindComment(200L, 300L, 7);
		verify(reportMapper, never()).countAcceptedPostReports(anyLong());
		verify(postMapper, never()).blindPost(anyLong(), anyInt());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("이미 블라인드된 대상은 신고를 인정해도 상태를 다시 변경하지 않는다")
	void preserveAlreadyBlindedTargets(boolean commentReport) {
		allowAdmin();

		var now = LocalDateTime.now();
		var post = Post.builder()
			.id(200L)
			.version(4)
			.blindReportBaseCount(0L)
			.blindedAt(commentReport ? null : now)
			.build();
		var comment = commentReport ?
			Comment.builder().id(300L).postId(200L).version(7).blindReportBaseCount(0L).blindedAt(now).build() : null;

		prepareReview(post, comment);
		allowSuccessfulReview();

		service.reviewReport(100L, request(), 10L, true);

		verify(reportMapper, never()).countAcceptedPostReports(anyLong());
		verify(reportMapper, never()).countAcceptedCommentReports(anyLong());
		verify(postMapper, never()).blindPost(anyLong(), anyInt());
		verify(commentMapper, never()).blindComment(anyLong(), anyLong(), anyInt());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("삭제된 대상의 신고는 인정하되 블라인드는 적용하지 않는다")
	void acceptReportsWithoutBlindingDeletedTargets(boolean commentReport) {
		allowAdmin();

		var now = LocalDateTime.now();
		var post = Post.builder()
			.id(200L)
			.version(4)
			.blindReportBaseCount(0L)
			.deletedAt(commentReport ? null : now)
			.build();
		var comment = commentReport ?
			Comment.builder().id(300L).postId(200L).version(7).blindReportBaseCount(0L).deletedAt(now).build() : null;

		prepareReview(post, comment);
		allowSuccessfulReview();

		service.reviewReport(100L, request(), 10L, true);

		verify(reportMapper).reviewReport(100L, ReportStatus.ACCEPTED, "확인했습니다.", 10L);
		verify(reportMapper, never()).countAcceptedPostReports(anyLong());
		verify(reportMapper, never()).countAcceptedCommentReports(anyLong());
		verify(postMapper, never()).blindPost(anyLong(), anyInt());
		verify(commentMapper, never()).blindComment(anyLong(), anyLong(), anyInt());
	}

	@Test
	@DisplayName("부모 게시글이 삭제됐으면 댓글 신고만 인정하고 블라인드는 적용하지 않는다")
	void skipCommentBlindingWhenParentPostIsDeleted() {
		allowAdmin();

		var post = Post.builder().id(200L).version(4).blindReportBaseCount(0L).deletedAt(LocalDateTime.now()).build();

		prepareReview(post, commentTarget(0L));
		allowSuccessfulReview();

		service.reviewReport(100L, request(), 10L, true);

		verify(reportMapper, never()).countAcceptedCommentReports(anyLong());
		verify(commentMapper, never()).blindComment(anyLong(), anyLong(), anyInt());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("블라인드 UPDATE가 실패하면 예외를 전파한다")
	void propagateFailureWhenAutomaticBlindUpdateFails(boolean commentReport) {
		allowAdmin();
		prepareReview(postTarget(0L), commentReport ? commentTarget(0L) : null);
		allowSuccessfulReview();

		if (commentReport) {
			when(reportMapper.countAcceptedCommentReports(300L)).thenReturn(3L);
		} else {
			when(reportMapper.countAcceptedPostReports(200L)).thenReturn(3L);
		}

		// Mock의 UPDATE 반환값 기본값 0으로 실패 상황을 만든다.
		assertThrows(IllegalStateException.class, () -> service.reviewReport(100L, request(), 10L, true));
	}

	private void allowAdmin() {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);
	}

	private ReportReviewRequest request() {
		return new ReportReviewRequest(ReportStatus.ACCEPTED, "확인했습니다.");
	}

	private void assertForbidden(org.junit.jupiter.api.function.Executable action) {
		ResponseStatusException exception = assertThrows(ResponseStatusException.class, action);
		assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
	}

	private Post postTarget(long baseline) {
		return Post.builder().id(200L).version(4).blindReportBaseCount(baseline).build();
	}

	private Comment commentTarget(long baseline) {
		return Comment.builder().id(300L).postId(200L).version(7).blindReportBaseCount(baseline).build();
	}

	private void prepareReview(Post post, Comment comment) {
		var detail = mock(AdminReportDetailResponse.class);

		when(detail.postId()).thenReturn(post.getId());
		when(detail.commentId()).thenReturn(comment == null ? null : comment.getId());
		when(reportMapper.selectAdminReport(100L)).thenReturn(Optional.of(detail));
		when(postMapper.selectPostForModerationForUpdate(post.getId())).thenReturn(Optional.of(post));

		if (comment != null) {
			when(commentMapper.selectCommentForModerationForUpdate(post.getId(), comment.getId())).thenReturn(
				Optional.of(comment));
		}
	}

	private void allowSuccessfulReview() {
		when(reportMapper.reviewReport(100L, ReportStatus.ACCEPTED, "확인했습니다.", 10L)).thenReturn(1);
	}
}
