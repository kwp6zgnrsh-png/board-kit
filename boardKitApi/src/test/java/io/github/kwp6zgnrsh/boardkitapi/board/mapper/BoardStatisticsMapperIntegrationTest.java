package io.github.kwp6zgnrsh.boardkitapi.board.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardActivityResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardDailyActivityResponse;
import io.github.kwp6zgnrsh.boardkitapi.board.dto.response.BoardStatisticsSummaryResponse;

@MybatisTest(properties = "mybatis.mapper-locations=classpath:mapper/board/BoardStatisticsMapper.xml")
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = BoardStatisticsMapperIntegrationTest.Config.class)
class BoardStatisticsMapperIntegrationTest {

	private static final LocalDateTime START = LocalDate.of(2026, 10, 19)
		.atStartOfDay(ZoneId.of("Asia/Seoul"))
		.withZoneSameInstant(ZoneOffset.UTC)
		.toLocalDateTime();

	private static final LocalDateTime END = START.plusDays(1);

	@Autowired
	private BoardStatisticsMapper statisticsMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	private Long memberId;
	private Long activeBoardId;
	private Long archivedBoardId;
	private Long draftBoardId;
	private Long emptyBoardId;
	private BoardStatisticsSummaryResponse baseline;

	@Configuration(proxyBeanMethods = false)
	@MapperScan(basePackageClasses = BoardStatisticsMapper.class)
	static class Config {
	}

	@BeforeEach
	void prepareFixtures() {
		assertEquals("boardkit_test", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));
		jdbcTemplate.execute("SET SESSION time_zone = '+00:00'");

		baseline = statisticsMapper.selectSummary(null, START, END);

		String key = UUID.randomUUID().toString().replace("-", "");
		jdbcTemplate.update("""
			INSERT INTO member (login_id, password_hash, display_name, role)
			VALUES (?, ?, ?, 'ADMIN')
			""", "statistics-" + key, "unused-test-hash", "통계" + key.substring(0, 8));
		memberId = lastInsertId();

		activeBoardId = insertBoard("ACTIVE");
		archivedBoardId = insertBoard("ARCHIVED");
		draftBoardId = insertBoard("DRAFT");
		emptyBoardId = insertBoard("ACTIVE");

		Long firstPost = insertPost(activeBoardId, START, false);
		Long deletedPost = insertPost(activeBoardId, START.plusHours(1), true);
		insertPost(activeBoardId, START.minusNanos(1_000), false);
		insertPost(activeBoardId, END, false);

		Long rootComment = insertComment(firstPost, null, START, false);
		insertComment(firstPost, rootComment, START.plusHours(1), false);
		insertComment(firstPost, null, START.plusHours(2), true);
		insertComment(deletedPost, null, START.plusHours(3), false);
		insertComment(firstPost, null, END, false);

		Long archivedPost = insertPost(archivedBoardId, START, false);
		insertComment(archivedPost, null, START, false);

		Long draftPost = insertPost(draftBoardId, START, false);
		insertComment(draftPost, null, START, false);

		sqlSessionTemplate.clearCache();
	}

	@Test
	@DisplayName("게시판별 현재 수와 한국 날짜 기준 작성 수를 구분한다")
	void distinguishCurrentCountsFromDailyActivity() {
		assertEquals(new BoardStatisticsSummaryResponse(3, 4, 2, 4),
			statisticsMapper.selectSummary(activeBoardId, START, END));
		assertEquals(new BoardStatisticsSummaryResponse(1, 1, 1, 1),
			statisticsMapper.selectSummary(archivedBoardId, START, END));
		assertEquals(new BoardStatisticsSummaryResponse(0, 0, 0, 0),
			statisticsMapper.selectSummary(emptyBoardId, START, END));
		assertEquals(new BoardStatisticsSummaryResponse(0, 0, 0, 0),
			statisticsMapper.selectSummary(draftBoardId, START, END));
	}

	@Test
	@DisplayName("전체 집계는 활성·보관 게시판을 포함하고 DRAFT는 제외한다")
	void aggregateActiveAndArchivedBoards() {
		BoardStatisticsSummaryResponse result = statisticsMapper.selectSummary(null, START, END);

		assertEquals(
			new BoardStatisticsSummaryResponse(baseline.currentPostCount() + 4, baseline.currentCommentCount() + 5,
				baseline.todayPostCount() + 3, baseline.todayCommentCount() + 5), result);
	}

	@Test
	@DisplayName("날짜별 추이는 한국 날짜로 묶고 시작 경계를 포함하며 종료 경계를 제외한다")
	void aggregateDailyActivitiesByKoreanDate() {
		jdbcTemplate.execute("SET SESSION time_zone = '+09:00'");
		try {
			LocalDate firstDate = LocalDate.of(2026, 10, 19);

			assertEquals(List.of(new BoardDailyActivityResponse(firstDate, 2, 4)),
				statisticsMapper.selectDailyActivities(activeBoardId, START, END));

			assertEquals(List.of(new BoardDailyActivityResponse(firstDate, 2, 4),
					new BoardDailyActivityResponse(firstDate.plusDays(1), 1, 1)),
				statisticsMapper.selectDailyActivities(activeBoardId, START, END.plusDays(2)));

			assertTrue(statisticsMapper.selectDailyActivities(emptyBoardId, START, END).isEmpty());
			assertTrue(statisticsMapper.selectDailyActivities(draftBoardId, START, END).isEmpty());
		} finally {
			jdbcTemplate.execute("SET SESSION time_zone = '+00:00'");
		}
	}

	@Test
	@DisplayName("전체 작성 추이는 삭제된 활동과 보관 게시판을 포함하고 DRAFT는 제외한다")
	void aggregateDailyActivitiesAcrossBoards() {
		List<BoardDailyActivityResponse> before = statisticsMapper.selectDailyActivities(null, START, END);

		Long archivedPost = insertPost(archivedBoardId, START.plusHours(4), true);
		insertComment(archivedPost, null, START.plusHours(5), true);

		Long draftPost = insertPost(draftBoardId, START.plusHours(4), false);
		insertComment(draftPost, null, START.plusHours(5), false);

		sqlSessionTemplate.clearCache();

		List<BoardDailyActivityResponse> after = statisticsMapper.selectDailyActivities(null, START, END);

		assertAll(() -> assertEquals(before.stream().mapToLong(BoardDailyActivityResponse::postCount).sum() + 1,
				after.stream().mapToLong(BoardDailyActivityResponse::postCount).sum()),
			() -> assertEquals(before.stream().mapToLong(BoardDailyActivityResponse::commentCount).sum() + 1,
				after.stream().mapToLong(BoardDailyActivityResponse::commentCount).sum()),
			() -> assertTrue(after.stream().allMatch(row -> row.date().equals(LocalDate.of(2026, 10, 19)))));
	}

	@Test
	@DisplayName("게시판별 기간 집계는 작성이 없는 게시판도 0건으로 반환한다")
	void aggregateBoardActivitiesIncludingEmptyBoards() {
		Map<Long, BoardActivityResponse> rows = statisticsMapper.selectBoardActivities(START, END)
			.stream()
			.collect(Collectors.toMap(BoardActivityResponse::boardId, row -> row));

		BoardActivityResponse active = rows.get(activeBoardId);
		BoardActivityResponse archived = rows.get(archivedBoardId);
		BoardActivityResponse empty = rows.get(emptyBoardId);

		assertNotNull(active);
		assertNotNull(archived);
		assertNotNull(empty);

		assertAll(() -> assertEquals(2L, active.postCount()), () -> assertEquals(4L, active.commentCount()),
			() -> assertEquals(1L, archived.postCount()), () -> assertEquals(1L, archived.commentCount()),
			() -> assertEquals(0L, empty.postCount()), () -> assertEquals(0L, empty.commentCount()),
			() -> assertEquals("통계 테스트 게시판", active.boardName()),
			() -> assertTrue(active.boardSlug().startsWith("statistics-")),
			() -> assertFalse(rows.containsKey(draftBoardId)));
	}

	private Long insertBoard(String status) {
		jdbcTemplate.update("""
			INSERT INTO board (name, slug, status, created_by)
			VALUES (?, ?, ?, ?)
			""", "통계 테스트 게시판", "statistics-" + UUID.randomUUID(), status, memberId);
		return lastInsertId();
	}

	private Long insertPost(Long boardId, LocalDateTime createdAt, boolean deleted) {
		jdbcTemplate.update("""
			INSERT INTO post (
			    board_id, author_id, title, content, created_at, deleted_at
			)
			VALUES (?, ?, ?, ?, ?, ?)
			""", boardId, memberId, "통계 테스트", "본문", createdAt, deleted ? createdAt : null);
		return lastInsertId();
	}

	private Long insertComment(Long postId, Long parentId, LocalDateTime createdAt, boolean deleted) {
		jdbcTemplate.update("""
			INSERT INTO comment (
			    post_id, author_id, parent_id, content, created_at, deleted_at
			)
			VALUES (?, ?, ?, ?, ?, ?)
			""", postId, memberId, parentId, "통계 테스트 댓글", createdAt, deleted ? createdAt : null);
		return lastInsertId();
	}

	private Long lastInsertId() {
		Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		assertNotNull(id);
		return id;
	}
}