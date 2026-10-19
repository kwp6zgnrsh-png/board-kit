package io.github.kwp6zgnrsh.boardkitapi.report.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import io.github.kwp6zgnrsh.boardkitapi.common.mybatis.JsonNodeTypeHandler;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportStatusFilter;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminReportSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.model.Report;
import io.github.kwp6zgnrsh.boardkitapi.report.model.ReportReason;
import io.github.kwp6zgnrsh.boardkitapi.report.model.ReportStatus;

@MybatisTest(properties = {
	"mybatis.mapper-locations=classpath*:mapper/report/*.xml",
	"mybatis.configuration.local-cache-scope=STATEMENT"
})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = ReportMapperIntegrationTest.Config.class)
class ReportMapperIntegrationTest {

	@Autowired
	private ReportMapper reportMapper;

	@Autowired
	private AdminBlindMapper adminBlindMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Long reporterId;
	private Long authorId;
	private Long postId;
	private Long commentId;

	@Configuration(proxyBeanMethods = false)
	@MapperScan(basePackageClasses = ReportMapper.class)
	@Import(JsonNodeTypeHandler.class)
	static class Config {
	}

	@BeforeEach
	void prepareFixtures() {
		assertEquals("boardkit_test", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));

		jdbcTemplate.execute("SET SESSION time_zone = '+00:00'");

		reporterId = insertMember("MEMBER");
		authorId = insertMember("ADMIN");

		String slug = "report-" + UUID.randomUUID().toString().replace("-", "");

		jdbcTemplate.update("""
			INSERT INTO board (name, slug, display_type, status, created_by)
			VALUES (?, ?, 'LIST', 'ACTIVE', ?)
			""", "신고 테스트", slug, authorId);

		Long boardId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update("""
			INSERT INTO post (board_id, author_id, title, content)
			VALUES (?, ?, ?, ?)
			""", boardId, authorId, "신고 대상 게시글", "게시글 본문");

		postId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update("""
			INSERT INTO comment (post_id, author_id, content)
			VALUES (?, ?, ?)
			""", postId, authorId, "신고 대상 댓글");

		commentId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	@Test
	@DisplayName("게시글 신고를 저장하고 생성 ID와 초기 상태를 확인한다")
	void insertPostReportWithGeneratedIdAndPendingStatus() {
		Report report = postReport();

		assertEquals(1, reportMapper.insertReport(report));
		assertNotNull(report.getId());

		var saved = jdbcTemplate.queryForMap("SELECT * FROM report WHERE id = ?", report.getId());

		assertAll(() -> assertEquals(reporterId.longValue(), ((Number)saved.get("reporter_id")).longValue()),
			() -> assertEquals(postId.longValue(), ((Number)saved.get("post_id")).longValue()),
			() -> assertNull(saved.get("comment_id")), () -> assertEquals("SPAM", saved.get("reason")),
			() -> assertEquals("광고가 반복됩니다.", saved.get("detail")), () -> assertEquals("PENDING", saved.get("status")),
			() -> assertNotNull(saved.get("created_at")), () -> assertNull(saved.get("reviewed_by")),
			() -> assertNull(saved.get("review_note")), () -> assertNull(saved.get("reviewed_at")));
	}

	@Test
	@DisplayName("같은 회원이 게시글과 해당 게시글의 댓글을 각각 신고할 수 있다")
	void allowSeparateReportsForPostAndComment() {
		Report postReport = postReport();
		Report commentReport = commentReport();

		assertEquals(1, reportMapper.insertReport(postReport));
		assertEquals(1, reportMapper.insertReport(commentReport));
		assertNotEquals(postReport.getId(), commentReport.getId());

		var saved = jdbcTemplate.queryForMap("SELECT * FROM report WHERE id = ?", commentReport.getId());

		assertAll(() -> assertNull(saved.get("post_id")),
			() -> assertEquals(commentId.longValue(), ((Number)saved.get("comment_id")).longValue()),
			() -> assertEquals("ABUSE", saved.get("reason")), () -> assertNull(saved.get("detail")),
			() -> assertEquals("PENDING", saved.get("status")));
	}

	@Test
	@DisplayName("서로 다른 회원은 같은 게시글을 각각 신고할 수 있다")
	void allowDifferentMembersToReportSamePost() {
		Report first = postReport();
		Report second = Report.builder()
			.reporterId(insertMember("MEMBER"))
			.postId(postId)
			.reason(ReportReason.ABUSE)
			.build();

		assertEquals(1, reportMapper.insertReport(first));
		assertEquals(1, reportMapper.insertReport(second));
		assertNotEquals(first.getId(), second.getId());
	}

	@ParameterizedTest
	@EnumSource(ReportStatus.class)
	@DisplayName("처리 상태와 관계없이 같은 회원의 게시글 중복 신고를 차단한다")
	void rejectDuplicatePostReportRegardlessOfStatus(ReportStatus status) {
		Report original = postReport();
		reportMapper.insertReport(original);
		setReportStatus(original.getId(), status);

		assertThrows(DuplicateKeyException.class, () -> reportMapper.insertReport(postReport()));

		assertEquals(1L, jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM report
			WHERE reporter_id = ? AND post_id = ?
			""", Long.class, reporterId, postId));
	}

	@ParameterizedTest
	@EnumSource(ReportStatus.class)
	@DisplayName("처리 상태와 관계없이 같은 회원의 댓글 중복 신고를 차단한다")
	void rejectDuplicateCommentReportRegardlessOfStatus(ReportStatus status) {
		Report original = commentReport();
		reportMapper.insertReport(original);
		setReportStatus(original.getId(), status);

		assertThrows(DuplicateKeyException.class, () -> reportMapper.insertReport(commentReport()));

		assertEquals(1L, jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM report
			WHERE reporter_id = ? AND comment_id = ?
			""", Long.class, reporterId, commentId));
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("신고 대상이 없거나 두 대상이 모두 있으면 CHECK 제약으로 거절한다")
	void rejectInvalidTargetCombination(boolean bothTargets) {
		Report report = Report.builder()
			.reporterId(reporterId)
			.postId(bothTargets ? postId : null)
			.commentId(bothTargets ? commentId : null)
			.reason(ReportReason.SPAM)
			.build();

		DataAccessException exception = assertThrows(DataAccessException.class,
			() -> reportMapper.insertReport(report));

		SQLException sqlException = assertInstanceOf(SQLException.class, exception.getMostSpecificCause());

		assertEquals(3819, sqlException.getErrorCode());
	}

	@Test
	@DisplayName("신고 대상 게시글과 댓글의 소속 및 작성자를 조회한다")
	void readReportTargetsWithinRequestedScope() {
		Long boardId = jdbcTemplate.queryForObject("SELECT board_id FROM post WHERE id = ?", Long.class, postId);

		var post = reportMapper.selectActivePostForShare(boardId, postId).orElseThrow();

		assertEquals(authorId, post.authorId());
		assertTrue(reportMapper.selectActivePostForShare(Long.MAX_VALUE, postId).isEmpty());

		assertEquals(authorId, reportMapper.selectActiveCommentAuthorForShare(postId, commentId).orElseThrow());
		assertTrue(reportMapper.selectActiveCommentAuthorForShare(Long.MAX_VALUE, commentId).isEmpty());
	}

	@Test
	@DisplayName("삭제 표시된 게시글과 댓글은 신고 대상으로 조회하지 않는다")
	void excludeDeletedReportTargets() {
		Long boardId = jdbcTemplate.queryForObject("SELECT board_id FROM post WHERE id = ?", Long.class, postId);

		jdbcTemplate.update("UPDATE comment SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", commentId);
		jdbcTemplate.update("UPDATE post SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", postId);

		assertTrue(reportMapper.selectActivePostForShare(boardId, postId).isEmpty());
		assertTrue(reportMapper.selectActiveCommentAuthorForShare(postId, commentId).isEmpty());
	}

	@Test
	@DisplayName("게시글 신고 상세는 현재 제목과 본문 및 JSON을 조회한다")
	void readCurrentPostContentInAdminReportDetail() {
		Report report = postReport();
		reportMapper.insertReport(report);

		LocalDateTime reportedAt = LocalDateTime.of(2026, 1, 1, 12, 0);
		LocalDateTime updatedAt = reportedAt.plusHours(1);

		jdbcTemplate.update("UPDATE report SET created_at = ? WHERE id = ?", reportedAt, report.getId());

		String document = """
			{
			  "type":"doc",
			  "content":[{
			    "type":"paragraph",
			    "content":[{"type":"text","text":"현재 본문"}]
			  }]
			}
			""";

		jdbcTemplate.update("""
			UPDATE post
			SET title = ?, content = ?, content_document = ?,
			    created_at = ?, updated_at = ?
			WHERE id = ?
			""", "현재 제목", "현재 본문", document, reportedAt.minusDays(1), updatedAt, postId);

		var result = reportMapper.selectAdminReport(report.getId()).orElseThrow();

		assertAll(() -> assertEquals(report.getId(), result.id()), () -> assertEquals(reporterId, result.reporterId()),
			() -> assertEquals(postId, result.postId()), () -> assertNull(result.commentId()),
			() -> assertEquals(authorId, result.authorId()), () -> assertEquals("현재 제목", result.postTitle()),
			() -> assertEquals("현재 본문", result.content()), () -> assertEquals("현재 본문",
				result.contentDocument().path("content").get(0).path("content").get(0).path("text").asString()),
			() -> assertEquals(updatedAt, result.contentUpdatedAt()),
			() -> assertTrue(result.contentUpdatedAt().isAfter(result.createdAt())),
			() -> assertEquals(ReportReason.SPAM, result.reason()),
			() -> assertEquals(ReportStatus.PENDING, result.status()),
			() -> assertEquals("광고가 반복됩니다.", result.detail()), () -> assertFalse(result.postDeleted()),
			() -> assertFalse(result.commentDeleted()), () -> assertNull(result.reviewedBy()),
			() -> assertNull(result.reviewerDisplayName()), () -> assertNull(result.reviewNote()),
			() -> assertNull(result.reviewedAt()));

		assertTrue(reportMapper.selectAdminReport(Long.MAX_VALUE).isEmpty());
	}

	@Test
	@DisplayName("보관된 게시판의 삭제된 댓글 신고도 현재 내용과 처리 이력을 조회한다")
	void readArchivedAndDeletedCommentReportWithReviewHistory() {
		Report report = commentReport();
		reportMapper.insertReport(report);

		Long commentAuthorId = insertMember("MEMBER");
		Long boardId = jdbcTemplate.queryForObject("SELECT board_id FROM post WHERE id = ?", Long.class, postId);

		jdbcTemplate.update("UPDATE board SET status = 'ARCHIVED' WHERE id = ?", boardId);
		jdbcTemplate.update("UPDATE post SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", postId);
		jdbcTemplate.update("""
			UPDATE comment
			SET author_id = ?, content = ?, deleted_at = CURRENT_TIMESTAMP(6)
			WHERE id = ?
			""", commentAuthorId, "현재 댓글 내용", commentId);

		setReportStatus(report.getId(), ReportStatus.REJECTED);
		jdbcTemplate.update("UPDATE report SET review_note = ? WHERE id = ?", "위반 사항을 확인하지 못했습니다.", report.getId());

		String reviewerName = jdbcTemplate.queryForObject("SELECT display_name FROM member WHERE id = ?", String.class,
			authorId);

		var result = reportMapper.selectAdminReport(report.getId()).orElseThrow();

		assertAll(() -> assertEquals(postId, result.postId()), () -> assertEquals(commentId, result.commentId()),
			() -> assertEquals(commentAuthorId, result.authorId()), () -> assertEquals("현재 댓글 내용", result.content()),
			() -> assertNull(result.contentDocument()), () -> assertEquals("ARCHIVED", result.boardStatus()),
			() -> assertTrue(result.postDeleted()), () -> assertTrue(result.commentDeleted()),
			() -> assertEquals(ReportStatus.REJECTED, result.status()),
			() -> assertEquals(authorId, result.reviewedBy()),
			() -> assertEquals(reviewerName, result.reviewerDisplayName()),
			() -> assertEquals("위반 사항을 확인하지 못했습니다.", result.reviewNote()), () -> assertNotNull(result.reviewedAt()));
	}

	@ParameterizedTest
	@EnumSource(ReportStatusFilter.class)
	@DisplayName("관리자 목록은 상태별 개수와 목록 조건을 일치시키고 보관 및 삭제 이력을 포함한다")
	void filterAdminReportsIncludingArchivedAndDeletedTargets(ReportStatusFilter filter) {
		long baseline;
		if (filter == ReportStatusFilter.ALL) {
			baseline = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report", Long.class);
		} else {
			baseline = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report WHERE status = ?", Long.class,
				filter.name());
		}

		List<Report> reports = insertThreeAdminReports();
		Long pendingId = reports.get(0).getId();
		Long acceptedId = reports.get(1).getId();
		Long rejectedId = reports.get(2).getId();

		setReportStatus(acceptedId, ReportStatus.ACCEPTED);
		setReportStatus(rejectedId, ReportStatus.REJECTED);

		jdbcTemplate.update("""
			UPDATE board SET status = 'ARCHIVED'
			WHERE id = (SELECT board_id FROM post WHERE id = ?)
			""", postId);
		jdbcTemplate.update("UPDATE post SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", postId);
		jdbcTemplate.update("UPDATE comment SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", commentId);

		Set<Long> fixtureIds = Set.of(pendingId, acceptedId, rejectedId);
		Set<Long> expectedIds = switch (filter) {
			case ALL -> fixtureIds;
			case PENDING -> Set.of(pendingId);
			case ACCEPTED -> Set.of(acceptedId);
			case REJECTED -> Set.of(rejectedId);
		};

		assertEquals(baseline + expectedIds.size(), reportMapper.countAdminReports(filter));

		List<AdminReportSummaryResponse> actual = readAdminReports(filter).stream()
			.filter(row -> fixtureIds.contains(row.id()))
			.toList();

		assertEquals(expectedIds, actual.stream().map(AdminReportSummaryResponse::id).collect(Collectors.toSet()));
		assertTrue(actual.stream().allMatch(row -> row.postDeleted() && "ARCHIVED".equals(row.boardStatus())));
	}

	@Test
	@DisplayName("관리자 신고 목록은 신고 시각과 ID 내림차순으로 정렬하고 페이지를 분할한다")
	void orderAndPaginateAdminReports() {
		List<Report> reports = insertThreeAdminReports();
		LocalDateTime older = LocalDateTime.of(2026, 1, 1, 0, 0);
		LocalDateTime newer = older.plusDays(1);

		jdbcTemplate.update("UPDATE report SET created_at = ? WHERE id = ?", newer, reports.get(0).getId());
		jdbcTemplate.update("UPDATE report SET created_at = ? WHERE id = ?", older, reports.get(1).getId());
		jdbcTemplate.update("UPDATE report SET created_at = ? WHERE id = ?", newer, reports.get(2).getId());

		Set<Long> fixtureIds = reports.stream().map(Report::getId).collect(Collectors.toSet());

		List<AdminReportSummaryResponse> all = readAdminReports(ReportStatusFilter.ALL);

		List<Long> ownOrder = all.stream().map(AdminReportSummaryResponse::id).filter(fixtureIds::contains).toList();

		assertEquals(List.of(reports.get(2).getId(), reports.get(0).getId(), reports.get(1).getId()), ownOrder);

		assertEquals(all.subList(0, 2), reportMapper.selectAdminReports(ReportStatusFilter.ALL, 2, 0L));
		assertEquals(all.subList(2, Math.min(4, all.size())),
			reportMapper.selectAdminReports(ReportStatusFilter.ALL, 2, 2L));
		assertTrue(reportMapper.selectAdminReports(ReportStatusFilter.ALL, 2, all.size()).isEmpty());
	}

	@ParameterizedTest
	@EnumSource(value = ReportStatus.class, names = {"ACCEPTED", "REJECTED"})
	@DisplayName("보관 및 삭제된 대상의 신고를 한 번만 처리하고 최초 처리 정보를 유지한다")
	void reviewReportOnceAndPreserveOriginalDecision(ReportStatus status) {
		Report report = commentReport();
		reportMapper.insertReport(report);

		jdbcTemplate.update("""
			UPDATE board SET status = 'ARCHIVED'
			WHERE id = (SELECT board_id FROM post WHERE id = ?)
			""", postId);
		jdbcTemplate.update("UPDATE post SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", postId);
		jdbcTemplate.update("UPDATE comment SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", commentId);

		assertTrue(reportMapper.existsReport(report.getId()));
		assertEquals(1, reportMapper.reviewReport(report.getId(), status, "최초 처리 메모", authorId));

		var original = reportMapper.selectAdminReport(report.getId()).orElseThrow();

		assertAll(() -> assertEquals(status, original.status()), () -> assertEquals(authorId, original.reviewedBy()),
			() -> assertEquals("최초 처리 메모", original.reviewNote()), () -> assertNotNull(original.reviewedAt()),
			() -> assertEquals("ARCHIVED", original.boardStatus()), () -> assertTrue(original.postDeleted()),
			() -> assertTrue(original.commentDeleted()));

		Long anotherAdminId = insertMember("ADMIN");
		ReportStatus opposite = status == ReportStatus.ACCEPTED ? ReportStatus.REJECTED : ReportStatus.ACCEPTED;

		assertEquals(0, reportMapper.reviewReport(report.getId(), opposite, "덮어쓰기 시도", anotherAdminId));

		assertEquals(original, reportMapper.selectAdminReport(report.getId()).orElseThrow());
	}

	@Test
	@DisplayName("대기를 처리 결과로 전달해도 신고 상태와 처리 정보를 변경하지 않는다")
	void rejectPendingDecisionInReviewQuery() {
		Report report = postReport();
		reportMapper.insertReport(report);

		assertEquals(0, reportMapper.reviewReport(report.getId(), ReportStatus.PENDING, "잘못된 처리", authorId));

		var result = reportMapper.selectAdminReport(report.getId()).orElseThrow();

		assertAll(() -> assertEquals(ReportStatus.PENDING, result.status()), () -> assertNull(result.reviewedBy()),
			() -> assertNull(result.reviewNote()), () -> assertNull(result.reviewedAt()));
	}

	@Test
	@DisplayName("없는 신고의 존재 조회는 false이고 처리 결과는 0건이다")
	void returnNoMatchForMissingReport() {
		assertFalse(reportMapper.existsReport(Long.MAX_VALUE));
		assertEquals(0, reportMapper.reviewReport(Long.MAX_VALUE, ReportStatus.ACCEPTED, "처리 메모", authorId));
	}

	@ParameterizedTest
	@EnumSource(ReportStatus.class)
	@DisplayName("대상별 신고 집계에는 인정된 신고만 포함한다")
	void countOnlyAcceptedReportsForEachTarget(ReportStatus status) {
		Report postReport = postReport();
		Report commentReport = commentReport();

		reportMapper.insertReport(postReport);
		reportMapper.insertReport(commentReport);

		setReportStatus(postReport.getId(), status);
		setReportStatus(commentReport.getId(), status);

		long expected = status == ReportStatus.ACCEPTED ? 1L : 0L;

		assertAll(() -> assertEquals(expected, reportMapper.countAcceptedPostReports(postId)),
			() -> assertEquals(expected, reportMapper.countAcceptedCommentReports(commentId)),
			() -> assertEquals(0L, reportMapper.countAcceptedPostReports(Long.MAX_VALUE)),
			() -> assertEquals(0L, reportMapper.countAcceptedCommentReports(Long.MAX_VALUE)));
	}

	@Test
	@DisplayName("여러 회원의 인정 신고를 합산하고 게시글과 댓글 건수는 구분한다")
	void countAcceptedReportsFromMultipleMembersSeparately() {
		List<Report> reports = insertThreeAdminReports();

		for (Report report : reports) {
			setReportStatus(report.getId(), ReportStatus.ACCEPTED);
		}

		assertAll(() -> assertEquals(2L, reportMapper.countAcceptedPostReports(postId)),
			() -> assertEquals(1L, reportMapper.countAcceptedCommentReports(commentId)));
	}

	@Test
	@DisplayName("신고 대상 공유 잠금 조회에서 게시글 블라인드 시각을 받는다")
	void readBlindStateFromReportTarget() {
		Long boardId = jdbcTemplate.queryForObject("SELECT board_id FROM post WHERE id = ?", Long.class, postId);
		jdbcTemplate.update("UPDATE post SET blinded_at = CURRENT_TIMESTAMP(6) WHERE id = ?", postId);

		assertNotNull(reportMapper.selectActivePostForShare(boardId, postId).orElseThrow().blindedAt());
	}

	@Test
	@DisplayName("블라인드 댓글은 신고 대상에서 제외하고 해제하면 다시 조회한다")
	void excludeBlindedCommentFromReportTargets() {
		assertEquals(authorId, reportMapper.selectActiveCommentAuthorForShare(postId, commentId).orElseThrow());

		jdbcTemplate.update("UPDATE comment SET blinded_at = CURRENT_TIMESTAMP(6) WHERE id = ?", commentId);

		assertTrue(reportMapper.selectActiveCommentAuthorForShare(postId, commentId).isEmpty());

		jdbcTemplate.update("UPDATE comment SET blinded_at = NULL WHERE id = ?", commentId);

		assertEquals(authorId, reportMapper.selectActiveCommentAuthorForShare(postId, commentId).orElseThrow());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("보관된 게시판의 신고 상세에서 대상별 블라인드 상태와 버전을 구분한다")
	void readModerationStateAndVersionForReportTarget(boolean commentReport) {
		Report report = commentReport ? commentReport() : postReport();
		assertEquals(1, reportMapper.insertReport(report));

		Long boardId = jdbcTemplate.queryForObject("SELECT board_id FROM post WHERE id = ?", Long.class, postId);
		LocalDateTime blindedAt = LocalDateTime.of(2026, 10, 19, 12, 0);

		jdbcTemplate.update("UPDATE board SET status = 'ARCHIVED' WHERE id = ?", boardId);
		jdbcTemplate.update("UPDATE post SET version = 4, blinded_at = ? WHERE id = ?",
			commentReport ? null : blindedAt, postId);
		jdbcTemplate.update("UPDATE comment SET version = 7, blinded_at = ? WHERE id = ?",
			commentReport ? blindedAt : null, commentId);

		var result = reportMapper.selectAdminReport(report.getId()).orElseThrow();

		assertAll(() -> assertEquals("ARCHIVED", result.boardStatus()),
			() -> assertEquals(!commentReport, result.postBlinded()),
			() -> assertEquals(commentReport, result.commentBlinded()),
			() -> assertEquals(commentReport ? 7 : 4, result.targetVersion().intValue()));
	}

	@Test
	@DisplayName("블라인드 게시글은 신고 수와 관계없이 한 행이며 보관 게시판을 포함한다")
	void readBlindedPostsWithoutDuplicateReports() {
		long baseline = adminBlindMapper.countBlindedPosts();

		jdbcTemplate.update("""
			UPDATE board SET status = 'ARCHIVED'
			WHERE id = (SELECT board_id FROM post WHERE id = ?)
			""", postId);
		jdbcTemplate.update("UPDATE post SET blinded_at = CURRENT_TIMESTAMP(6) WHERE id = ?", postId);

		// 신고가 없어도 블라인드 대상에 포함된다.
		assertEquals(baseline + 1, adminBlindMapper.countBlindedPosts());

		for (int i = 0; i < 3; i++) {
			reportMapper.insertReport(
				Report.builder().reporterId(insertMember("MEMBER")).postId(postId).reason(ReportReason.SPAM).build());
		}

		var rows = adminBlindMapper.selectBlindedPosts(Math.toIntExact(baseline + 1), 0L);
		var ownRows = rows.stream().filter(row -> row.postId().equals(postId)).toList();

		assertEquals(baseline + 1, adminBlindMapper.countBlindedPosts());
		assertEquals(1, ownRows.size());

		var row = ownRows.getFirst();
		assertEquals("ARCHIVED", row.boardStatus());
		assertEquals("신고 대상 게시글", row.title());
		assertEquals(authorId, row.authorId());
		assertNotNull(row.blindedAt());
		assertEquals(Integer.valueOf(0), row.version());

		String document = """
			{
			  "type":"doc",
			  "content":[{
			    "type":"paragraph",
			    "content":[{"type":"text","text":"게시글 본문"}]
			  }]
			}
			""";

		jdbcTemplate.update(
			"UPDATE post SET content_document = ? WHERE id = ?",
			document, postId
		);

		var detail = adminBlindMapper.selectBlindedPost(postId).orElseThrow();

		assertEquals(postId, detail.postId());
		assertNull(detail.commentId());
		assertNull(detail.parentId());
		assertEquals("ARCHIVED", detail.boardStatus());
		assertEquals("신고 대상 게시글", detail.postTitle());
		assertEquals(authorId, detail.authorId());
		assertEquals("게시글 본문", detail.content());
		assertEquals(
			"게시글 본문",
			detail.contentDocument()
				.path("content").get(0)
				.path("content").get(0)
				.path("text").asString()
		);
		assertNotNull(detail.createdAt());
		assertEquals(row.blindedAt(), detail.blindedAt());
		assertEquals(row.version(), detail.version());

		jdbcTemplate.update("UPDATE post SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", postId);
		assertEquals(baseline, adminBlindMapper.countBlindedPosts());
		assertTrue(adminBlindMapper.selectBlindedPost(postId).isEmpty());

		jdbcTemplate.update("""
			UPDATE post SET deleted_at = NULL, blinded_at = NULL
			WHERE id = ?
			""", postId);
		assertEquals(baseline, adminBlindMapper.countBlindedPosts());
		assertTrue(adminBlindMapper.selectBlindedPost(postId).isEmpty());
	}

	@Test
	@DisplayName("블라인드 댓글은 본인과 부모 글의 삭제 상태를 검사하고 댓글 작성자를 반환한다")
	void readBlindedCommentsWithinUndeletedPosts() {
		long baseline = adminBlindMapper.countBlindedComments();

		jdbcTemplate.update("""
			UPDATE board SET status = 'ARCHIVED'
			WHERE id = (SELECT board_id FROM post WHERE id = ?)
			""", postId);

		// 부모 글이 블라인드여도 관리자 목록에서는 댓글을 관리할 수 있다.
		jdbcTemplate.update("UPDATE post SET blinded_at = CURRENT_TIMESTAMP(6) WHERE id = ?", postId);
		jdbcTemplate.update("""
			UPDATE comment
			SET blinded_at = CURRENT_TIMESTAMP(6), author_id = ?, content = ?
			WHERE id = ?
			""", reporterId, "가".repeat(201), commentId);

		assertEquals(baseline + 1, adminBlindMapper.countBlindedComments());

		var row = adminBlindMapper.selectBlindedComments(Math.toIntExact(baseline + 1), 0L)
			.stream()
			.filter(item -> item.commentId().equals(commentId))
			.findFirst()
			.orElseThrow();

		assertEquals(postId, row.postId());
		assertNull(row.parentId());
		assertEquals("ARCHIVED", row.boardStatus());
		assertEquals("신고 대상 게시글", row.postTitle());
		assertEquals(reporterId, row.authorId());
		assertEquals("가".repeat(200), row.contentPreview());
		assertNotNull(row.blindedAt());

		var detail = adminBlindMapper.selectBlindedComment(commentId).orElseThrow();

		assertEquals(postId, detail.postId());
		assertEquals(commentId, detail.commentId());
		assertNull(detail.parentId());
		assertEquals("ARCHIVED", detail.boardStatus());
		assertEquals("신고 대상 게시글", detail.postTitle());
		assertEquals(reporterId, detail.authorId());
		assertEquals("가".repeat(201), detail.content());
		assertNull(detail.contentDocument());
		assertNotNull(detail.createdAt());
		assertEquals(row.blindedAt(), detail.blindedAt());
		assertEquals(row.version(), detail.version());

		jdbcTemplate.update("UPDATE comment SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", commentId);
		assertEquals(baseline, adminBlindMapper.countBlindedComments());
		assertTrue(adminBlindMapper.selectBlindedComment(commentId).isEmpty());

		jdbcTemplate.update("UPDATE comment SET deleted_at = NULL WHERE id = ?", commentId);
		jdbcTemplate.update("UPDATE post SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", postId);
		assertEquals(baseline, adminBlindMapper.countBlindedComments());
		assertTrue(adminBlindMapper.selectBlindedComment(commentId).isEmpty());

		jdbcTemplate.update("UPDATE post SET deleted_at = NULL WHERE id = ?", postId);
		jdbcTemplate.update("UPDATE comment SET blinded_at = NULL WHERE id = ?", commentId);
		assertEquals(baseline, adminBlindMapper.countBlindedComments());
		assertTrue(adminBlindMapper.selectBlindedComment(commentId).isEmpty());
	}

	private List<Report> insertThreeAdminReports() {
		Report third = Report.builder()
			.reporterId(insertMember("MEMBER"))
			.postId(postId)
			.reason(ReportReason.OTHER)
			.detail("기타 신고 내용")
			.build();

		List<Report> reports = List.of(postReport(), commentReport(), third);

		for (Report report : reports) {
			assertEquals(1, reportMapper.insertReport(report));
			assertNotNull(report.getId());
		}

		return reports;
	}

	private List<AdminReportSummaryResponse> readAdminReports(ReportStatusFilter filter) {
		long count = reportMapper.countAdminReports(filter);

		return reportMapper.selectAdminReports(filter, Math.max(1, Math.toIntExact(count)), 0L);
	}

	private Long insertMember(String role) {
		String key = UUID.randomUUID().toString().replace("-", "");

		jdbcTemplate.update("""
			INSERT INTO member (login_id, password_hash, display_name, role)
			VALUES (?, ?, ?, ?)
			""", "report-" + key, "unused-in-mapper-tests", "신고" + key.substring(0, 6), role);

		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Report postReport() {
		return Report.builder()
			.reporterId(reporterId)
			.postId(postId)
			.reason(ReportReason.SPAM)
			.detail("광고가 반복됩니다.")
			.build();
	}

	private Report commentReport() {
		return Report.builder().reporterId(reporterId).commentId(commentId).reason(ReportReason.ABUSE).build();
	}

	private void setReportStatus(Long reportId, ReportStatus status) {
		if (status == ReportStatus.PENDING)
			return;

		jdbcTemplate.update("""
			UPDATE report
			SET status = ?, reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP(6)
			WHERE id = ?
			""", status.name(), authorId, reportId);
	}
}
