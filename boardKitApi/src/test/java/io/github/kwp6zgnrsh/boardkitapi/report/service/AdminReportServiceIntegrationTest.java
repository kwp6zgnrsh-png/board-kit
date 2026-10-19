package io.github.kwp6zgnrsh.boardkitapi.report.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.kwp6zgnrsh.boardkitapi.comment.mapper.CommentMapper;
import io.github.kwp6zgnrsh.boardkitapi.common.mybatis.JsonNodeTypeHandler;
import io.github.kwp6zgnrsh.boardkitapi.file.mapper.FileCleanupTaskMapper;
import io.github.kwp6zgnrsh.boardkitapi.file.mapper.PostFileMapper;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostMapper;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportReviewRequest;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportTargetAction;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportTargetActionRequest;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminReportDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.exception.ReportReviewConflictException;
import io.github.kwp6zgnrsh.boardkitapi.report.exception.ReportTargetConflictException;
import io.github.kwp6zgnrsh.boardkitapi.report.mapper.ReportMapper;
import io.github.kwp6zgnrsh.boardkitapi.report.model.ReportStatus;

@MybatisTest(properties = {"mybatis.mapper-locations=classpath*:mapper/**/*.xml",
	"spring.datasource.hikari.maximum-pool-size=4", "spring.datasource.hikari.minimum-idle=0",
	"spring.datasource.hikari.connection-init-sql=" + "SET SESSION innodb_lock_wait_timeout = 5, time_zone = '+00:00'"})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AdminReportServiceIntegrationTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminReportServiceIntegrationTest {

	@Autowired
	private AdminReportService service;

	@Autowired
	private AdminContentModerationService adminContentModerationService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	@MockitoSpyBean
	private ReportMapper reportMapper;

	@MockitoSpyBean
	private PostMapper postMapper;

	@MockitoSpyBean
	private CommentMapper commentMapper;

	@MockitoSpyBean
	private FileCleanupTaskMapper fileCleanupTaskMapper;

	private final List<String> cleanupKeys = new ArrayList<>();

	private final List<Long> memberIds = new ArrayList<>();

	private boolean databaseVerified;
	private Long adminId;
	private Long boardId;
	private Long postId;
	private Long commentId;

	@Configuration(proxyBeanMethods = false)
	@EnableTransactionManagement
	@MapperScan(basePackageClasses = {MemberMapper.class, ReportMapper.class, PostMapper.class, CommentMapper.class,
		PostFileMapper.class})
	@Import({AdminReportService.class, AdminContentModerationService.class, JsonNodeTypeHandler.class})
	static class Config {
	}

	@BeforeEach
	void prepareFixtures() {
		assertEquals("boardkit_test", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));
		databaseVerified = true;

		adminId = insertMember("ADMIN");

		String slug = "moderation-it-" + UUID.randomUUID();
		boardId = insertWithId("""
			INSERT INTO board (name, slug, display_type, status, created_by)
			VALUES (?, ?, 'LIST', 'ACTIVE', ?)
			""", "신고 처리 통합 테스트", slug, adminId);

		postId = insertWithId("""
			INSERT INTO post (board_id, author_id, title, content)
			VALUES (?, ?, '게시글 제목', '게시글 본문')
			""", boardId, adminId);

		commentId = insertWithId("""
			INSERT INTO comment (post_id, author_id, content)
			VALUES (?, ?, '댓글 본문')
			""", postId, adminId);
	}

	@AfterEach
	void cleanUpFixtures() {
		if (!databaseVerified) {
			return;
		}

		for (String key : cleanupKeys) {
			jdbcTemplate.update("DELETE FROM file_cleanup_task WHERE target_key = ?", key);
		}

		if (postId != null) {
			jdbcTemplate.update("""
				DELETE FROM report
				WHERE post_id = ?
				    OR comment_id IN (
				        SELECT id FROM comment WHERE post_id = ?
				    )
				""", postId, postId);

			jdbcTemplate.update("UPDATE post SET representative_file_id = NULL WHERE id = ?", postId);
			jdbcTemplate.update("DELETE FROM post_file WHERE post_id = ?", postId);
			jdbcTemplate.update("DELETE FROM comment WHERE post_id = ?", postId);
			jdbcTemplate.update("DELETE FROM post WHERE id = ?", postId);
		}

		if (boardId != null) {
			jdbcTemplate.update("DELETE FROM board WHERE id = ?", boardId);
		}

		for (Long memberId : memberIds) {
			jdbcTemplate.update("DELETE FROM member WHERE id = ?", memberId);
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("같은 대상의 서로 다른 신고를 동시에 인정해도 3건 도달 시 블라인드한다")
	void blindTargetWhenConcurrentReviewsReachThreshold(boolean commentTarget) throws Exception {
		List<Long> pending = prepareReports(commentTarget, 1, 2);

		List<Boolean> results = reviewConcurrently(pending.get(0), pending.get(1));

		assertEquals(List.of(true, true), results);
		assertEquals(3L, countAcceptedReports(commentTarget));

		Map<String, Object> target = readTargetState(commentTarget);

		assertAll(() -> assertNotNull(target.get("blinded_at")), () -> assertNull(target.get("deleted_at")),
			() -> assertEquals(1, ((Number)target.get("version")).intValue()));
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("같은 신고를 동시에 처리하면 하나만 성공하고 인정 건수는 한 번만 증가한다")
	void acceptSameReportOnlyOnceDuringConcurrentReviews(boolean commentTarget) throws Exception {
		Long reportId = prepareReports(commentTarget, 1, 1).getFirst();

		List<Boolean> results = reviewConcurrently(reportId, reportId);

		assertEquals(1L, results.stream().filter(Boolean::booleanValue).count());
		assertEquals(2L, countAcceptedReports(commentTarget));

		Map<String, Object> target = readTargetState(commentTarget);

		assertNull(target.get("blinded_at"));
		assertEquals(0, ((Number)target.get("version")).intValue());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("자동 블라인드가 실패하면 신고 인정과 처리 정보가 실제 DB에서 롤백된다")
	void rollBackReviewWhenAutomaticBlindingFails(boolean commentTarget) {
		Long reportId = prepareReports(commentTarget, 2, 1).getFirst();
		var failure = new DataAccessResourceFailureException("테스트용 블라인드 실패");

		if (commentTarget) {
			doThrow(failure).when(commentMapper).blindComment(postId, commentId, 0);
		} else {
			doThrow(failure).when(postMapper).blindPost(postId, 0);
		}

		assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
			() -> service.reviewReport(reportId, request(), adminId, true)));

		Map<String, Object> report = jdbcTemplate.queryForMap("""
			SELECT status, reviewed_by, review_note, reviewed_at
			FROM report
			WHERE id = ?
			""", reportId);

		assertAll(() -> assertEquals("PENDING", report.get("status")), () -> assertNull(report.get("reviewed_by")),
			() -> assertNull(report.get("review_note")), () -> assertNull(report.get("reviewed_at")));

		assertEquals(2L, countAcceptedReports(commentTarget));

		Map<String, Object> target = readTargetState(commentTarget);
		assertNull(target.get("blinded_at"));
		assertEquals(0, ((Number)target.get("version")).intValue());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("보관 게시판에서도 수동 블라인드와 해제 및 삭제가 가능하고 해제 이후 새 인정 3건으로 재블라인드한다")
	void moderateArchivedTargetAndResetBlindBaseline(boolean commentTarget) {
		List<Long> pending = prepareReports(commentTarget, 2, 3);
		Long reportId = pending.getFirst();

		jdbcTemplate.update("UPDATE board SET status = 'ARCHIVED' WHERE id = ?", boardId);

		service.moderateReportTarget(reportId, new ReportTargetActionRequest(ReportTargetAction.BLIND, 0), adminId,
			true);
		assertNotNull(readTargetState(commentTarget).get("blinded_at"));

		// 수동 조치가 신고 판정을 변경하지 않는다.
		assertEquals("PENDING",
			jdbcTemplate.queryForObject("SELECT status FROM report WHERE id = ?", String.class, reportId));

		assertThrows(ReportTargetConflictException.class,
			() -> service.moderateReportTarget(reportId, new ReportTargetActionRequest(ReportTargetAction.UNBLIND, 0),
				adminId, true));

		service.moderateReportTarget(reportId, new ReportTargetActionRequest(ReportTargetAction.UNBLIND, 1), adminId,
			true);

		var unblinded = readTargetState(commentTarget);
		assertNull(unblinded.get("blinded_at"));
		assertEquals(2L, ((Number)unblinded.get("blind_report_base_count")).longValue());

		service.reviewReport(pending.get(0), request(), adminId, true);
		service.reviewReport(pending.get(1), request(), adminId, true);
		assertNull(readTargetState(commentTarget).get("blinded_at"));

		service.reviewReport(pending.get(2), request(), adminId, true);
		assertNotNull(readTargetState(commentTarget).get("blinded_at"));
		assertEquals(5L, countAcceptedReports(commentTarget));

		service.moderateReportTarget(reportId, new ReportTargetActionRequest(ReportTargetAction.DELETE, 3), adminId,
			true);

		var deleted = readTargetState(commentTarget);
		assertNotNull(deleted.get("deleted_at"));
		assertEquals(4, ((Number)deleted.get("version")).intValue());

		assertThrows(ReportTargetConflictException.class,
			() -> service.moderateReportTarget(reportId, new ReportTargetActionRequest(ReportTargetAction.BLIND, 4),
				adminId, true));
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("관리자 게시글 삭제는 파일 정리 작업까지 저장하고 작업 등록 실패 시 전체 롤백한다")
	void deletePostAtomicallyWithFileCleanup(boolean failCleanup) {
		Long reportId = prepareReports(false, 0, 1).getFirst();
		String key = UUID.randomUUID().toString();
		List<String> keys = List.of(
			"posts/" + postId + "/" + key + ".png",
			"thumbnails/" + postId + "/" + key + ".png"
		);
		cleanupKeys.addAll(keys);

		Long fileId = insertWithId("""
			INSERT INTO post_file
			    (post_id, original_name, storage_key, content_type,
			     file_usage, size_bytes, thumbnail_key, sort_order)
			VALUES (?, 'image.png', ?, 'image/png', 'BODY_IMAGE', 100, ?, 0)
			""", postId, keys.get(0), keys.get(1));

		jdbcTemplate.update(
			"UPDATE board SET status = 'ARCHIVED' WHERE id = ?", boardId
		);

		var action = new ReportTargetActionRequest(ReportTargetAction.DELETE, 0);
		var failure = new DataAccessResourceFailureException("테스트용 작업 등록 실패");

		if (failCleanup) {
			doThrow(failure).when(fileCleanupTaskMapper).enqueueDeletes(keys);

			assertSame(failure,
				assertThrows(DataAccessResourceFailureException.class,
					() -> service.moderateReportTarget(
						reportId, action, adminId, true
					)));
		} else {
			service.moderateReportTarget(reportId, action, adminId, true);
		}

		var post = readTargetState(false);
		var file = jdbcTemplate.queryForMap(
			"SELECT deleted_at FROM post_file WHERE id = ?", fileId
		);

		if (failCleanup) {
			assertNull(post.get("deleted_at"));
			assertNull(file.get("deleted_at"));
			assertEquals(0, ((Number)post.get("version")).intValue());
		} else {
			assertNotNull(post.get("deleted_at"));
			assertNotNull(file.get("deleted_at"));
			assertEquals(1, ((Number)post.get("version")).intValue());
		}

		assertEquals(failCleanup ? 0L : 2L, jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM file_cleanup_task
			WHERE target_key IN (?, ?)
			""", Long.class, keys.get(0), keys.get(1)).longValue());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("신고가 없어도 대상 ID로 게시글과 댓글을 블라인드하고 해제할 수 있다")
	void moderateTargetWithoutReport(boolean commentTarget) {
		Long targetCommentId = commentTarget ? commentId : null;

		adminContentModerationService.moderateTarget(
			postId, targetCommentId,
			new ReportTargetActionRequest(ReportTargetAction.BLIND, 0),
			adminId, true
		);

		var blinded = readTargetState(commentTarget);
		assertNotNull(blinded.get("blinded_at"));
		assertEquals(1, ((Number) blinded.get("version")).intValue());

		adminContentModerationService.moderateTarget(
			postId, targetCommentId,
			new ReportTargetActionRequest(ReportTargetAction.UNBLIND, 1),
			adminId, true
		);

		var unblinded = readTargetState(commentTarget);
		assertNull(unblinded.get("blinded_at"));
		assertEquals(2, ((Number) unblinded.get("version")).intValue());
		assertEquals(
			0L, ((Number) unblinded.get("blind_report_base_count")).longValue()
		);

		long reportCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM report WHERE post_id = ? OR comment_id = ?",
			Long.class, postId, commentId
		).longValue();

		assertEquals(0L, reportCount);
	}

	private List<Boolean> reviewConcurrently(Long firstReportId, Long secondReportId) throws Exception {
		CyclicBarrier barrier = new CyclicBarrier(2);

		doAnswer(invocation -> {
			Long reportId = invocation.getArgument(0);

			// 실제 Mapper SQL을 실행한 뒤 두 요청을 같은 지점에서 기다리게 한다.
			AdminReportDetailResponse detail = sqlSessionTemplate.selectOne(
				ReportMapper.class.getName() + ".selectAdminReport", Map.of("reportId", reportId));

			barrier.await(10, TimeUnit.SECONDS);
			return Optional.ofNullable(detail);
		}).when(reportMapper).selectAdminReport(anyLong());

		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> tryReview(firstReportId));
			var second = executor.submit(() -> tryReview(secondReportId));

			return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
		}
	}

	private boolean tryReview(Long reportId) {
		try {
			service.reviewReport(reportId, request(), adminId, true);
			return true;
		} catch (ReportReviewConflictException exception) {
			return false;
		}
	}

	private List<Long> prepareReports(boolean commentTarget, int acceptedCount, int pendingCount) {
		List<Long> pending = new ArrayList<>();

		for (int index = 0; index < acceptedCount + pendingCount; index++) {
			Long reporterId = insertMember("MEMBER");

			Long reportId = insertWithId("""
				INSERT INTO report (reporter_id, post_id, comment_id, reason)
				VALUES (?, ?, ?, 'SPAM')
				""", reporterId, commentTarget ? null : postId, commentTarget ? commentId : null);

			if (index < acceptedCount) {
				jdbcTemplate.update("""
					UPDATE report
					SET status = 'ACCEPTED',
					    reviewed_by = ?,
					    review_note = '초기 인정',
					    reviewed_at = CURRENT_TIMESTAMP(6)
					WHERE id = ?
					""", adminId, reportId);
			} else {
				pending.add(reportId);
			}
		}

		return pending;
	}

	private long countAcceptedReports(boolean commentTarget) {
		String sql = commentTarget ? """
			SELECT COUNT(*) FROM report
			WHERE comment_id = ? AND status = 'ACCEPTED'
			""" : """
			SELECT COUNT(*) FROM report
			WHERE post_id = ? AND status = 'ACCEPTED'
			""";

		return jdbcTemplate.queryForObject(sql, Long.class, commentTarget ? commentId : postId);
	}

	private Map<String, Object> readTargetState(boolean commentTarget) {
		String sql = commentTarget ? """
			SELECT blinded_at, deleted_at, version, blind_report_base_count
			FROM comment WHERE id = ?
			""" : """
			SELECT blinded_at, deleted_at, version, blind_report_base_count
			FROM post WHERE id = ?
			""";

		return jdbcTemplate.queryForMap(sql, commentTarget ? commentId : postId);
	}

	private Long insertMember(String role) {
		String key = UUID.randomUUID().toString().replace("-", "");

		Long id = insertWithId("""
			INSERT INTO member (login_id, password_hash, display_name, role)
			VALUES (?, ?, ?, ?)
			""", "moderation-it-" + key, "unused-in-integration-tests", "검증" + key.substring(0, 6), role);

		memberIds.add(id);
		return id;
	}

	private Long insertWithId(String sql, Object... values) {
		var keyHolder = new GeneratedKeyHolder();

		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

			for (int index = 0; index < values.length; index++) {
				statement.setObject(index + 1, values[index]);
			}

			return statement;
		}, keyHolder);

		Number key = keyHolder.getKey();
		assertNotNull(key);
		return key.longValue();
	}

	private ReportReviewRequest request() {
		return new ReportReviewRequest(ReportStatus.ACCEPTED, "통합 테스트 처리");
	}
}
