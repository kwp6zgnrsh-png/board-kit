package io.github.kwp6zgnrsh.boardkitapi.post.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import io.github.kwp6zgnrsh.boardkitapi.common.mybatis.JsonNodeTypeHandler;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostReplyFilter;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.AdminPostReplySummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostReplyResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostReply;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

@MybatisTest(properties = "mybatis.mapper-locations=classpath:mapper/post/PostReplyMapper.xml")
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PostReplyMapperIntegrationTest.Config.class)
class PostReplyMapperIntegrationTest {

	@Autowired
	private PostReplyMapper postReplyMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	private Long memberId;
	private Long boardId;
	private Long postId;
	private String displayName;

	@Configuration(proxyBeanMethods = false)
	@MapperScan(basePackageClasses = PostReplyMapper.class)
	@Import(JsonNodeTypeHandler.class)
	static class Config {
	}

	@BeforeEach
	void prepareFixtures() {
		assertEquals("boardkit_test", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));

		jdbcTemplate.execute("SET SESSION time_zone = '+00:00'");

		String key = UUID.randomUUID().toString().replace("-", "");
		displayName = "관리자" + key.substring(0, 6);

		jdbcTemplate.update("""
			INSERT INTO member (login_id, password_hash, display_name, role)
			VALUES (?, ?, ?, 'ADMIN')
			""", "reply-" + key, "unused-in-mapper-tests", displayName);

		memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		boardId = insertBoard();
		postId = insertPost(boardId);
	}

	@Test
	@DisplayName("공식 답변의 생성 ID와 JSON 본문을 저장하고 조회한다")
	void insertAndReadPostReplyDocument() {
		assertTrue(postReplyMapper.selectPostReply(boardId, postId).isEmpty());

		PostReply reply = newReply("최초 답변");

		assertEquals(1, postReplyMapper.insertPostReply(reply));
		assertNotNull(reply.getId());

		PostReplyResponse saved = postReplyMapper.selectPostReply(boardId, postId).orElseThrow();

		assertAll(() -> assertEquals(reply.getId(), saved.id()), () -> assertEquals(postId, saved.postId()),
			() -> assertEquals(memberId, saved.authorId()), () -> assertEquals(displayName, saved.authorDisplayName()),
			() -> assertEquals("최초 답변", saved.content()),
			() -> assertEquals(reply.getContentDocument(), saved.contentDocument()),
			() -> assertEquals(0, saved.version()), () -> assertNotNull(saved.createdAt()),
			() -> assertNotNull(saved.updatedAt()));
	}

	@Test
	@DisplayName("같은 게시글에 공식 답변을 중복 등록할 수 없다")
	void rejectDuplicatePostReply() {
		postReplyMapper.insertPostReply(newReply("첫 답변"));

		assertThrows(DuplicateKeyException.class, () -> postReplyMapper.insertPostReply(newReply("중복 답변")));

		assertEquals("첫 답변", postReplyMapper.selectPostReply(boardId, postId).orElseThrow().content());
	}

	@Test
	@DisplayName("공식 답변 수정은 버전을 증가시키고 이전 버전의 수정과 삭제를 거절한다")
	void rejectUpdateAndDeletionWithStaleVersion() {
		PostReply reply = newReply("최초 답변");
		postReplyMapper.insertPostReply(reply);

		JsonNode updatedDocument = document("수정 답변");

		assertEquals(1, postReplyMapper.updatePostReply(boardId, postId, reply.getId(), "수정 답변", updatedDocument, 0));

		assertEquals(0,
			postReplyMapper.updatePostReply(boardId, postId, reply.getId(), "오래된 수정", document("오래된 수정"), 0));

		assertEquals(0, postReplyMapper.deletePostReply(boardId, postId, reply.getId(), 0));

		PostReplyResponse saved = postReplyMapper.selectPostReply(boardId, postId).orElseThrow();

		assertAll(() -> assertEquals("수정 답변", saved.content()),
			() -> assertEquals(updatedDocument, saved.contentDocument()), () -> assertEquals(1, saved.version()),
			() -> assertEquals(memberId, saved.authorId()));

		assertEquals(1, postReplyMapper.deletePostReply(boardId, postId, reply.getId(), 1));

		assertTrue(postReplyMapper.selectPostReply(boardId, postId).isEmpty());
	}

	@Test
	@DisplayName("다른 게시판이나 게시글의 답변 및 삭제된 게시글의 답변은 접근할 수 없다")
	void rejectMismatchedScopeAndDeletedPost() {
		PostReply reply = newReply("답변");
		postReplyMapper.insertPostReply(reply);

		Long otherBoardId = insertBoard();
		Long otherPostId = insertPost(boardId);

		assertTrue(postReplyMapper.selectPostReply(otherBoardId, postId).isEmpty());
		assertTrue(postReplyMapper.selectPostReply(boardId, otherPostId).isEmpty());

		assertEquals(0, postReplyMapper.updatePostReply(otherBoardId, postId, reply.getId(), "변경", document("변경"), 0));

		assertEquals(0, postReplyMapper.updatePostReply(boardId, otherPostId, reply.getId(), "변경", document("변경"), 0));

		assertEquals(0, postReplyMapper.deletePostReply(otherBoardId, postId, reply.getId(), 0));

		assertEquals(0, postReplyMapper.deletePostReply(boardId, otherPostId, reply.getId(), 0));

		jdbcTemplate.update("""
			UPDATE post SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?
			""", postId);

		assertTrue(postReplyMapper.selectPostReply(boardId, postId).isEmpty());

		assertEquals(0, postReplyMapper.updatePostReply(boardId, postId, reply.getId(), "변경", document("변경"), 0));

		assertEquals(0, postReplyMapper.deletePostReply(boardId, postId, reply.getId(), 0));

		assertEquals(1L,
			jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_reply WHERE id = ?", Long.class, reply.getId()));
	}

	@Test
	@DisplayName("삭제 후 재등록한 공식 답변은 이전 답변의 요청으로 수정하거나 삭제할 수 없다")
	void rejectOldReplyRequestsAfterRecreation() {
		PostReply oldReply = newReply("이전 답변");
		postReplyMapper.insertPostReply(oldReply);

		assertEquals(1, postReplyMapper.deletePostReply(boardId, postId, oldReply.getId(), 0));

		PostReply newReply = newReply("새 답변");
		assertEquals(1, postReplyMapper.insertPostReply(newReply));
		assertNotEquals(oldReply.getId(), newReply.getId());

		assertEquals(0,
			postReplyMapper.updatePostReply(boardId, postId, oldReply.getId(), "이전 요청", document("이전 요청"), 0));

		assertEquals(0, postReplyMapper.deletePostReply(boardId, postId, oldReply.getId(), 0));

		PostReplyResponse saved = postReplyMapper.selectPostReply(boardId, postId).orElseThrow();

		assertAll(() -> assertEquals(newReply.getId(), saved.id()), () -> assertEquals("새 답변", saved.content()),
			() -> assertEquals(0, saved.version()));
	}

	@Test
	@DisplayName("관리자 목록은 기능 ON의 모든 글과 기능 OFF의 답변이 있는 글을 포함한다")
	void includeEnabledPostsAndExistingRepliesFromDisabledBoards() {
		long baseline = postReplyMapper.countAdminPostReplyTargets(PostReplyFilter.ALL);

		insertReplyPolicy(boardId, true);
		jdbcTemplate.update("UPDATE post SET is_secret = TRUE WHERE id = ?", postId);

		Long answeredPostId = insertPost(boardId);
		Long answeredReplyId = insertReplyForAdminList(answeredPostId);

		Long disabledBoardId = insertBoard();
		insertReplyPolicy(disabledBoardId, false);

		Long disabledAnsweredPostId = insertPost(disabledBoardId);
		Long disabledReplyId = insertReplyForAdminList(disabledAnsweredPostId);
		Long disabledUnansweredPostId = insertPost(disabledBoardId);

		Set<Long> fixtureIds = Set.of(postId, answeredPostId, disabledAnsweredPostId, disabledUnansweredPostId);

		List<AdminPostReplySummaryResponse> rows = readAdminReplyTargets(PostReplyFilter.ALL).stream()
			.filter(row -> fixtureIds.contains(row.postId()))
			.toList();

		assertEquals(baseline + 3, postReplyMapper.countAdminPostReplyTargets(PostReplyFilter.ALL));

		assertEquals(Set.of(postId, answeredPostId, disabledAnsweredPostId),
			rows.stream().map(AdminPostReplySummaryResponse::postId).collect(java.util.stream.Collectors.toSet()));

		AdminPostReplySummaryResponse unanswered = rows.stream()
			.filter(row -> row.postId().equals(postId))
			.findFirst()
			.orElseThrow();

		AdminPostReplySummaryResponse answered = rows.stream()
			.filter(row -> row.postId().equals(answeredPostId))
			.findFirst()
			.orElseThrow();

		AdminPostReplySummaryResponse disabled = rows.stream()
			.filter(row -> row.postId().equals(disabledAnsweredPostId))
			.findFirst()
			.orElseThrow();

		String expectedSlug = jdbcTemplate.queryForObject("SELECT slug FROM board WHERE id = ?", String.class, boardId);

		assertAll(() -> assertEquals(expectedSlug, unanswered.boardSlug()),
			() -> assertEquals("공식 답변 테스트", unanswered.boardName()), () -> assertEquals("테스트 게시글", unanswered.title()),
			() -> assertEquals(displayName, unanswered.authorDisplayName()), () -> assertTrue(unanswered.secret()),
			() -> assertNotNull(unanswered.createdAt()), () -> assertNull(unanswered.replyId()),
			() -> assertTrue(unanswered.officialReplyEnabled()),
			() -> assertEquals(answeredReplyId, answered.replyId()),
			() -> assertEquals(disabledReplyId, disabled.replyId()),
			() -> assertFalse(disabled.officialReplyEnabled()));
	}

	@Test
	@DisplayName("관리자 목록의 미답변과 답변 있음 필터를 개수와 목록에 동일하게 적용한다")
	void filterAnsweredAndUnansweredPostsConsistently() {
		long unansweredBaseline = postReplyMapper.countAdminPostReplyTargets(PostReplyFilter.UNANSWERED);
		long answeredBaseline = postReplyMapper.countAdminPostReplyTargets(PostReplyFilter.ANSWERED);

		insertReplyPolicy(boardId, true);

		Long answeredPostId = insertPost(boardId);
		insertReplyForAdminList(answeredPostId);

		Long disabledBoardId = insertBoard();
		insertReplyPolicy(disabledBoardId, false);

		Long disabledAnsweredPostId = insertPost(disabledBoardId);
		insertReplyForAdminList(disabledAnsweredPostId);
		Long disabledUnansweredPostId = insertPost(disabledBoardId);

		Set<Long> fixtureIds = Set.of(postId, answeredPostId, disabledAnsweredPostId, disabledUnansweredPostId);

		List<Long> unanswered = readAdminReplyTargets(PostReplyFilter.UNANSWERED).stream()
			.map(AdminPostReplySummaryResponse::postId)
			.filter(fixtureIds::contains)
			.toList();

		List<Long> answered = readAdminReplyTargets(PostReplyFilter.ANSWERED).stream()
			.map(AdminPostReplySummaryResponse::postId)
			.filter(fixtureIds::contains)
			.toList();

		assertAll(() -> assertEquals(unansweredBaseline + 1,
				postReplyMapper.countAdminPostReplyTargets(PostReplyFilter.UNANSWERED)),
			() -> assertEquals(answeredBaseline + 2,
				postReplyMapper.countAdminPostReplyTargets(PostReplyFilter.ANSWERED)),
			() -> assertEquals(List.of(postId), unanswered),
			() -> assertEquals(Set.of(answeredPostId, disabledAnsweredPostId), Set.copyOf(answered)));
	}

	@Test
	@DisplayName("보관과 DRAFT 게시판 및 삭제된 게시글은 답변이 있어도 관리자 목록에서 제외한다")
	void excludeUnavailableBoardsAndDeletedPosts() {
		long baseline = postReplyMapper.countAdminPostReplyTargets(PostReplyFilter.ALL);

		insertReplyPolicy(boardId, true);
		insertReplyForAdminList(postId);
		jdbcTemplate.update("""
			UPDATE post SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?
			""", postId);

		Long visiblePostId = insertPost(boardId);

		Long archivedBoardId = insertBoard();
		insertReplyPolicy(archivedBoardId, true);
		jdbcTemplate.update("UPDATE board SET status = 'ARCHIVED' WHERE id = ?", archivedBoardId);
		Long archivedPostId = insertPost(archivedBoardId);
		insertReplyForAdminList(archivedPostId);

		Long draftBoardId = insertBoard();
		insertReplyPolicy(draftBoardId, true);
		jdbcTemplate.update("UPDATE board SET status = 'DRAFT' WHERE id = ?", draftBoardId);
		Long draftPostId = insertPost(draftBoardId);
		insertReplyForAdminList(draftPostId);

		Set<Long> fixtureIds = Set.of(postId, visiblePostId, archivedPostId, draftPostId);

		List<Long> actual = readAdminReplyTargets(PostReplyFilter.ALL).stream()
			.map(AdminPostReplySummaryResponse::postId)
			.filter(fixtureIds::contains)
			.toList();

		assertEquals(List.of(visiblePostId), actual);
		assertEquals(baseline + 1, postReplyMapper.countAdminPostReplyTargets(PostReplyFilter.ALL));
	}

	@Test
	@DisplayName("관리자 목록은 작성 시각과 ID 내림차순으로 정렬하고 페이지를 분할한다")
	void orderAndPaginateAdminReplyTargets() {
		insertReplyPolicy(boardId, true);
		jdbcTemplate.update("""
			UPDATE post SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?
			""", postId);

		LocalDateTime olderTime = LocalDateTime.of(2026, 1, 1, 0, 0);
		LocalDateTime newerTime = olderTime.plusDays(1);

		Long newerLowId = insertPost(boardId);
		Long olderHighId = insertPost(boardId);
		Long newerHighId = insertPost(boardId);

		jdbcTemplate.update("UPDATE post SET created_at = ? WHERE id = ?", newerTime, newerLowId);
		jdbcTemplate.update("UPDATE post SET created_at = ? WHERE id = ?", olderTime, olderHighId);
		jdbcTemplate.update("UPDATE post SET created_at = ? WHERE id = ?", newerTime, newerHighId);

		Set<Long> fixtureIds = Set.of(newerLowId, olderHighId, newerHighId);
		List<AdminPostReplySummaryResponse> all = readAdminReplyTargets(PostReplyFilter.ALL);

		List<Long> ownOrder = all.stream()
			.map(AdminPostReplySummaryResponse::postId)
			.filter(fixtureIds::contains)
			.toList();

		assertEquals(List.of(newerHighId, newerLowId, olderHighId), ownOrder);

		assertEquals(all.subList(0, 2), postReplyMapper.selectAdminPostReplyTargets(PostReplyFilter.ALL, 2, 0));

		assertEquals(all.subList(2, Math.min(4, all.size())),
			postReplyMapper.selectAdminPostReplyTargets(PostReplyFilter.ALL, 2, 2));

		assertTrue(postReplyMapper.selectAdminPostReplyTargets(PostReplyFilter.ALL, 2, all.size()).isEmpty());
	}

	private void insertReplyPolicy(Long targetBoardId, boolean enabled) {
		jdbcTemplate.update("""
			INSERT INTO board_policy (board_id, official_reply_enabled)
			VALUES (?, ?)
			""", targetBoardId, enabled);
	}

	private Long insertReplyForAdminList(Long targetPostId) {
		jdbcTemplate.update("""
			INSERT INTO post_reply (post_id, author_id, content)
			VALUES (?, ?, ?)
			""", targetPostId, memberId, "관리자 목록 테스트 답변");

		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private List<AdminPostReplySummaryResponse> readAdminReplyTargets(PostReplyFilter filter) {
		sqlSessionTemplate.clearCache();

		long count = postReplyMapper.countAdminPostReplyTargets(filter);
		int limit = Math.max(1, Math.toIntExact(count));

		return postReplyMapper.selectAdminPostReplyTargets(filter, limit, 0);
	}

	private Long insertBoard() {
		String slug = "reply-" + UUID.randomUUID().toString().replace("-", "");

		jdbcTemplate.update("""
			INSERT INTO board (name, slug, display_type, status, created_by)
			VALUES (?, ?, 'LIST', 'ACTIVE', ?)
			""", "공식 답변 테스트", slug, memberId);

		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertPost(Long targetBoardId) {
		jdbcTemplate.update("""
			INSERT INTO post (board_id, author_id, title, content)
			VALUES (?, ?, ?, ?)
			""", targetBoardId, memberId, "테스트 게시글", "게시글 본문");

		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private PostReply newReply(String content) {
		return PostReply.builder()
			.postId(postId)
			.authorId(memberId)
			.content(content)
			.contentDocument(document(content))
			.build();
	}

	private JsonNode document(String text) {
		var root = JsonNodeFactory.instance.objectNode().put("type", "doc");
		var paragraph = root.putArray("content").addObject().put("type", "paragraph");

		paragraph.putArray("content").addObject().put("type", "text").put("text", text);

		return root;
	}
}