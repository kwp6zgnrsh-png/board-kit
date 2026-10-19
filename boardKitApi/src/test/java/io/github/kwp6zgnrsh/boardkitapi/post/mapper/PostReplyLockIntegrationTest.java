package io.github.kwp6zgnrsh.boardkitapi.post.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.kwp6zgnrsh.boardkitapi.board.mapper.BoardMapper;
import io.github.kwp6zgnrsh.boardkitapi.common.mybatis.JsonNodeTypeHandler;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostReply;
import tools.jackson.databind.node.JsonNodeFactory;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PostReplyLockIntegrationTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostReplyLockIntegrationTest {

	@Autowired
	private BoardMapper boardMapper;
	@Autowired
	private PostMapper postMapper;
	@Autowired
	private PostReplyMapper postReplyMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private PlatformTransactionManager transactionManager;

	private TransactionTemplate transaction;
	private Long memberId;
	private Long boardId;
	private Long firstPostId;
	private Long secondPostId;
	private String boardSlug;

	@Configuration(proxyBeanMethods = false)
	@MapperScan(basePackageClasses = {BoardMapper.class, PostMapper.class})
	@Import(JsonNodeTypeHandler.class)
	static class Config {
	}

	@BeforeEach
	void prepareFixtures() {
		assertEquals("boardkit_test", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));

		transaction = new TransactionTemplate(transactionManager);
		transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		String key = UUID.randomUUID().toString().replace("-", "");
		boardSlug = "reply-lock-" + key;

		jdbcTemplate.update("""
			INSERT INTO member (login_id, password_hash, display_name, role)
			VALUES (?, ?, ?, 'ADMIN')
			""", "lock-" + key, "unused-in-lock-tests", "잠금" + key.substring(0, 6));

		memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update("""
			INSERT INTO board (name, slug, display_type, status, created_by)
			VALUES (?, ?, 'LIST', 'ACTIVE', ?)
			""", "공식 답변 잠금 테스트", boardSlug, memberId);

		boardId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		firstPostId = insertPost();
		secondPostId = insertPost();
	}

	@AfterEach
	void cleanUpFixtures() {
		if (boardId != null) {
			jdbcTemplate.update("""
				DELETE r
				FROM post_reply r
				    INNER JOIN post p ON p.id = r.post_id
				WHERE p.board_id = ?
				""", boardId);

			jdbcTemplate.update("DELETE FROM post WHERE board_id = ?", boardId);
			jdbcTemplate.update("DELETE FROM board WHERE id = ?", boardId);
		}

		if (memberId != null) {
			jdbcTemplate.update("DELETE FROM member WHERE id = ?", memberId);
		}
	}

	@Test
	@DisplayName("서로 다른 게시글의 답변 등록 트랜잭션은 게시판 공유 잠금을 함께 보유할 수 있다")
	void allowReplyCreationForDifferentPostsWhileFirstTransactionIsOpen() {
		transaction.executeWithoutResult(first -> {
			first.setRollbackOnly();

			assertEquals(boardId, boardMapper.selectActiveBoardBySlugForShare(boardSlug).orElseThrow().getId());

			assertTrue(postMapper.selectActiveCommentTargetForUpdate(boardId, firstPostId).isPresent());

			assertEquals(1, postReplyMapper.insertPostReply(reply(firstPostId)));

			transaction.executeWithoutResult(second -> {
				second.setRollbackOnly();

				withShortLockWait(() -> {
					assertEquals(boardId, boardMapper.selectActiveBoardBySlugForShare(boardSlug).orElseThrow().getId());

					assertTrue(postMapper.selectActiveCommentTargetForUpdate(boardId, secondPostId).isPresent());

					assertEquals(1, postReplyMapper.insertPostReply(reply(secondPostId)));
				});
			});
		});

		assertEquals(0L, jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM post_reply
			WHERE post_id IN (?, ?)
			""", Long.class, firstPostId, secondPostId));
	}

	@Test
	@DisplayName("공식 답변의 게시판 공유 잠금이 유지되는 동안 설정 변경용 배타 잠금을 획득할 수 없다")
	void blockExclusiveBoardLockWhileSharedLockIsHeld() {
		transaction.executeWithoutResult(first -> {
			first.setRollbackOnly();

			assertTrue(boardMapper.selectActiveBoardBySlugForShare(boardSlug).isPresent());

			assertThrows(CannotAcquireLockException.class, () -> transaction.executeWithoutResult(second -> {
				second.setRollbackOnly();

				withShortLockWait(() -> boardMapper.selectActiveBoardBySlugForUpdate(boardSlug));
			}));
		});

		transaction.executeWithoutResult(afterRelease -> {
			afterRelease.setRollbackOnly();

			assertTrue(boardMapper.selectActiveBoardBySlugForUpdate(boardSlug).isPresent());
		});
	}

	private void withShortLockWait(Runnable action) {
		int previous = jdbcTemplate.queryForObject("SELECT @@SESSION.innodb_lock_wait_timeout", Integer.class);

		jdbcTemplate.update("SET SESSION innodb_lock_wait_timeout = ?", 1);

		try {
			action.run();
		} finally {
			jdbcTemplate.update("SET SESSION innodb_lock_wait_timeout = ?", previous);
		}
	}

	private Long insertPost() {
		jdbcTemplate.update("""
			INSERT INTO post (board_id, author_id, title, content)
			VALUES (?, ?, ?, ?)
			""", boardId, memberId, "잠금 테스트 게시글", "본문");

		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private PostReply reply(Long targetPostId) {
		var document = JsonNodeFactory.instance.objectNode().put("type", "doc");
		var paragraph = document.putArray("content").addObject().put("type", "paragraph");

		paragraph.putArray("content").addObject().put("type", "text").put("text", "공식 답변");

		return PostReply.builder()
			.postId(targetPostId)
			.authorId(memberId)
			.content("공식 답변")
			.contentDocument(document)
			.build();
	}
}