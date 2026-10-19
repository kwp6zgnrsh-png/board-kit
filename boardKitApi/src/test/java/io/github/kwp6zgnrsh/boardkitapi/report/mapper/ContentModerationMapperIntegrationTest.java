package io.github.kwp6zgnrsh.boardkitapi.report.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import io.github.kwp6zgnrsh.boardkitapi.board.mapper.BoardMapper;
import io.github.kwp6zgnrsh.boardkitapi.comment.mapper.CommentMapper;
import io.github.kwp6zgnrsh.boardkitapi.common.mybatis.JsonNodeTypeHandler;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostMapper;

@MybatisTest(properties = "mybatis.mapper-locations=classpath*:mapper/**/*.xml")
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = ContentModerationMapperIntegrationTest.Config.class)
class ContentModerationMapperIntegrationTest {

	@Autowired
	private BoardMapper boardMapper;

	@Autowired
	private PostMapper postMapper;

	@Autowired
	private CommentMapper commentMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Long memberId;
	private Long postId;
	private Long commentId;
	private Long replyId;
	private Long fileId;
	private String storageKey;

	@Configuration(proxyBeanMethods = false)
	@MapperScan(basePackageClasses = {PostMapper.class, CommentMapper.class, BoardMapper.class})
	@Import(JsonNodeTypeHandler.class)
	static class Config {
	}

	@BeforeEach
	void prepareFixtures() {
		assertEquals("boardkit_test", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));
		jdbcTemplate.execute("SET SESSION time_zone = '+00:00'");

		String key = UUID.randomUUID().toString().replace("-", "");

		jdbcTemplate.update("""
			INSERT INTO member (login_id, password_hash, display_name, role)
			VALUES (?, ?, ?, 'ADMIN')
			""", "blind-" + key, "unused-in-mapper-tests", "검증" + key.substring(0, 6));
		memberId = lastId();

		jdbcTemplate.update("""
			INSERT INTO board (name, slug, display_type, status, created_by)
			VALUES (?, ?, 'LIST', 'ACTIVE', ?)
			""", "블라인드 테스트", "blind-" + key, memberId);
		Long boardId = lastId();

		jdbcTemplate.update("""
			INSERT INTO post (board_id, author_id, title, content)
			VALUES (?, ?, '게시글 제목', '게시글 본문')
			""", boardId, memberId);
		postId = lastId();

		commentId = insertComment(null);
		replyId = insertComment(commentId);

		storageKey = "posts/" + postId + "/" + key + ".png";
		jdbcTemplate.update("""
			INSERT INTO post_file
			    (post_id, original_name, storage_key, content_type, size_bytes)
			VALUES (?, 'image.png', ?, 'image/png', 100)
			""", postId, storageKey);
		fileId = lastId();
	}

	@Test
	@DisplayName("블라인드는 원문과 파일 및 대댓글을 보존하고 본문 수정 시각을 바꾸지 않는다")
	void blindTargetsWithoutDeletingContentOrChangingUpdatedAt() {
		var originalPost = postMapper.selectPostForModerationForUpdate(postId).orElseThrow();
		var originalComment = commentMapper.selectCommentForModerationForUpdate(postId, commentId).orElseThrow();

		assertEquals(1, postMapper.blindPost(postId, 0));
		assertEquals(1, commentMapper.blindComment(postId, commentId, 0));

		var post = postMapper.selectPostForModerationForUpdate(postId).orElseThrow();
		var comment = commentMapper.selectCommentForModerationForUpdate(postId, commentId).orElseThrow();

		assertAll(() -> assertNotNull(post.getBlindedAt()), () -> assertNotNull(comment.getBlindedAt()),
			() -> assertNull(post.getDeletedAt()), () -> assertNull(comment.getDeletedAt()),
			() -> assertEquals(1, post.getVersion().intValue()), () -> assertEquals(1, comment.getVersion().intValue()),
			() -> assertEquals(0L, post.getBlindReportBaseCount().longValue()),
			() -> assertEquals(0L, comment.getBlindReportBaseCount().longValue()),
			() -> assertEquals(originalPost.getUpdatedAt(), post.getUpdatedAt()),
			() -> assertEquals(originalComment.getUpdatedAt(), comment.getUpdatedAt()));

		assertEquals("게시글 본문",
			jdbcTemplate.queryForObject("SELECT content FROM post WHERE id = ?", String.class, postId));
		assertEquals("댓글 본문",
			jdbcTemplate.queryForObject("SELECT content FROM comment WHERE id = ?", String.class, commentId));
		assertEquals(1L, jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM post_file
			WHERE id = ? AND deleted_at IS NULL
			""", Long.class, fileId).longValue());
		assertEquals(1L, jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM comment
			WHERE id = ? AND deleted_at IS NULL AND blinded_at IS NULL
			""", Long.class, replyId).longValue());
		assertEquals(0L, jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM file_cleanup_task WHERE target_key = ?
			""", Long.class, storageKey).longValue());
	}

	@Test
	@DisplayName("해제 시 인정 신고 기준을 저장하고 반복 해제로 기준을 다시 초기화하지 않는다")
	void unblindTargetsAndPreserveBaselineOnRepeatedRequests() {
		assertEquals(1, postMapper.blindPost(postId, 0));
		assertEquals(1, commentMapper.blindComment(postId, commentId, 0));

		assertEquals(1, postMapper.unblindPost(postId, 1, 5L));
		assertEquals(1, commentMapper.unblindComment(postId, commentId, 1, 7L));

		assertEquals(0, postMapper.unblindPost(postId, 2, 20L));
		assertEquals(0, commentMapper.unblindComment(postId, commentId, 2, 20L));

		var post = postMapper.selectPostForModerationForUpdate(postId).orElseThrow();
		var comment = commentMapper.selectCommentForModerationForUpdate(postId, commentId).orElseThrow();

		assertAll(() -> assertNull(post.getBlindedAt()), () -> assertNull(comment.getBlindedAt()),
			() -> assertEquals(5L, post.getBlindReportBaseCount().longValue()),
			() -> assertEquals(7L, comment.getBlindReportBaseCount().longValue()),
			() -> assertEquals(2, post.getVersion().intValue()),
			() -> assertEquals(2, comment.getVersion().intValue()));
	}

	@Test
	@DisplayName("이전 버전이나 반복 블라인드 요청 및 다른 게시글의 댓글 변경은 거절한다")
	void rejectStaleVersionsRepeatedBlindingAndWrongPost() {
		assertEquals(0, postMapper.blindPost(postId, 1));
		assertEquals(0, commentMapper.blindComment(postId, commentId, 1));
		assertEquals(0, commentMapper.blindComment(Long.MAX_VALUE, commentId, 0));

		assertEquals(1, postMapper.blindPost(postId, 0));
		assertEquals(1, commentMapper.blindComment(postId, commentId, 0));

		assertEquals(0, postMapper.blindPost(postId, 1));
		assertEquals(0, commentMapper.blindComment(postId, commentId, 1));
		assertEquals(0, postMapper.unblindPost(postId, 0, 3L));
		assertEquals(0, commentMapper.unblindComment(postId, commentId, 0, 3L));
	}

	@Test
	@DisplayName("삭제된 대상은 관리 잠금 조회가 가능하지만 블라인드와 해제는 불가능하다")
	void rejectModerationChangesForDeletedTargets() {
		jdbcTemplate.update("UPDATE post SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", postId);
		jdbcTemplate.update("UPDATE comment SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", commentId);

		assertNotNull(postMapper.selectPostForModerationForUpdate(postId).orElseThrow().getDeletedAt());
		assertNotNull(
			commentMapper.selectCommentForModerationForUpdate(postId, commentId).orElseThrow().getDeletedAt());

		assertEquals(0, postMapper.blindPost(postId, 0));
		assertEquals(0, postMapper.unblindPost(postId, 0, 3L));
		assertEquals(0, commentMapper.blindComment(postId, commentId, 0));
		assertEquals(0, commentMapper.unblindComment(postId, commentId, 0, 3L));

		assertTrue(postMapper.selectPostForModerationForUpdate(Long.MAX_VALUE).isEmpty());
		assertTrue(commentMapper.selectCommentForModerationForUpdate(postId, Long.MAX_VALUE).isEmpty());
	}

	@Test
	@DisplayName("블라인드된 부모 댓글은 대댓글 작성 대상으로 사용할 수 없고 해제하면 다시 사용할 수 있다")
	void rejectBlindedRootAsReplyTargetUntilUnblinded() {
		assertEquals(1, commentMapper.blindComment(postId, commentId, 0));
		assertTrue(commentMapper.selectActiveRootIdForUpdate(postId, commentId).isEmpty());

		assertEquals(1, commentMapper.unblindComment(postId, commentId, 1, 0L));
		assertEquals(commentId, commentMapper.selectActiveRootIdForUpdate(postId, commentId).orElseThrow());
	}

	@Test
	@DisplayName("블라인드 댓글 원문은 댓글 작성자와 관리자만 조회하고 기존 대댓글은 유지한다")
	void maskBlindedCommentExceptForAuthorAndAdmin() {
		Long commentAuthorId = insertReadTestMember();
		Long postAuthorId = insertReadTestMember();

		jdbcTemplate.update("UPDATE post SET author_id = ? WHERE id = ?", postAuthorId, postId);
		jdbcTemplate.update("UPDATE comment SET author_id = ? WHERE id = ?", commentAuthorId, commentId);
		assertEquals(1, commentMapper.blindComment(postId, commentId, 0));

		// 비로그인과 게시글 작성자 모두 다른 사람의 블라인드 댓글은 읽지 못한다.
		for (Long viewerId : new Long[] {null, postAuthorId}) {
			var comments = commentMapper.selectActiveComments(postId, 50, 0L, viewerId, false);

			assertEquals(List.of(commentId, replyId), comments.stream().map(item -> item.id()).toList());
			assertTrue(comments.getFirst().blinded());
			assertFalse(comments.getFirst().deleted());
			assertNull(comments.getFirst().content());

			assertFalse(comments.get(1).blinded());
			assertEquals("댓글 본문", comments.get(1).content());
		}

		var authorComments = commentMapper.selectActiveComments(postId, 50, 0L, commentAuthorId, false);
		assertEquals("댓글 본문", authorComments.getFirst().content());
		assertTrue(authorComments.getFirst().blinded());

		// 기존 fixture의 memberId는 ADMIN이다.
		var adminComments = commentMapper.selectActiveComments(postId, 50, 0L, memberId, true);
		assertEquals("댓글 본문", adminComments.getFirst().content());
		assertTrue(adminComments.getFirst().blinded());

		assertEquals(2L, commentMapper.countActiveComments(postId));
	}

	@Test
	@DisplayName("블라인드 대댓글도 페이지에 포함하고 삭제된 부모의 원문은 관리자에게도 가린다")
	void preservePaginationAndDeletedCommentMaskingWithBlindedReply() {
		assertEquals(1, commentMapper.blindComment(postId, replyId, 0));

		var secondPage = commentMapper.selectActiveComments(postId, 1, 1L, null, false);

		assertEquals(2L, commentMapper.countActiveComments(postId));
		assertEquals(1, secondPage.size());
		assertEquals(replyId, secondPage.getFirst().id());
		assertEquals(commentId, secondPage.getFirst().parentId());
		assertTrue(secondPage.getFirst().blinded());
		assertNull(secondPage.getFirst().content());

		assertEquals(1, commentMapper.blindComment(postId, commentId, 0));
		jdbcTemplate.update("UPDATE comment SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", commentId);

		var adminComments = commentMapper.selectActiveComments(postId, 50, 0L, memberId, true);

		assertEquals(2, adminComments.size());
		assertTrue(adminComments.getFirst().deleted());
		assertTrue(adminComments.getFirst().blinded());
		assertNull(adminComments.getFirst().content());

		assertEquals(replyId, adminComments.get(1).id());
		assertTrue(adminComments.get(1).blinded());
		assertEquals("댓글 본문", adminComments.get(1).content());
	}

	@ParameterizedTest
	@CsvSource({"ACTIVE, true, true", "ARCHIVED, false, true", "DRAFT, false, false"})
	@DisplayName("게시판 상태에 따라 관리자만 보관된 게시판을 조회할 수 있다")
	void readBoardAccordingToStatusAndAdminRole(String status, boolean normalAllowed, boolean adminAllowed) {
		Long targetBoardId = jdbcTemplate.queryForObject("SELECT board_id FROM post WHERE id = ?", Long.class, postId);
		String slug = jdbcTemplate.queryForObject("SELECT slug FROM board WHERE id = ?", String.class, targetBoardId);

		jdbcTemplate.update("UPDATE board SET status = ? WHERE id = ?", status, targetBoardId);

		var normalResult = boardMapper.selectReadableBoardBySlug(slug, false);
		var adminResult = boardMapper.selectReadableBoardBySlug(slug, true);

		assertEquals(normalAllowed, normalResult.isPresent());
		assertEquals(adminAllowed, adminResult.isPresent());

		if (adminAllowed) {
			assertEquals(targetBoardId, adminResult.orElseThrow().getId());
			assertEquals(status, adminResult.orElseThrow().getStatus());
		}

		// 기존 활성 게시판 조회는 보관 상태를 허용하지 않는다.
		assertEquals("ACTIVE".equals(status), boardMapper.selectActiveBoardBySlug(slug).isPresent());
	}

	private Long insertReadTestMember() {
		String key = UUID.randomUUID().toString().replace("-", "");
		jdbcTemplate.update("""
			INSERT INTO member (login_id, password_hash, display_name, role)
			VALUES (?, ?, ?, 'MEMBER')
			""", "reader-" + key, "unused-in-mapper-tests", key.substring(0, 10));
		return lastId();
	}

	private Long insertComment(Long parentId) {
		jdbcTemplate.update("""
			INSERT INTO comment (post_id, author_id, parent_id, content)
			VALUES (?, ?, ?, '댓글 본문')
			""", postId, memberId, parentId);
		return lastId();
	}

	private Long lastId() {
		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}
}
