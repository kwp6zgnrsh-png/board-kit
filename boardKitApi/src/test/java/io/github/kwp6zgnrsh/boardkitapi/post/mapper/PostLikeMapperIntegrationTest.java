package io.github.kwp6zgnrsh.boardkitapi.post.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@MybatisTest(properties = "mybatis.mapper-locations=classpath:mapper/post/PostLikeMapper.xml")
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PostLikeMapperIntegrationTest.Config.class)
class PostLikeMapperIntegrationTest {

	@Autowired
	private PostLikeMapper postLikeMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Long memberId;
	private Long otherMemberId;
	private Long postId;

	@Configuration(proxyBeanMethods = false)
	@MapperScan(basePackageClasses = PostLikeMapper.class)
	static class Config {
	}

	@BeforeEach
	void prepareFixtures() {
		assertEquals("boardkit_test", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));

		jdbcTemplate.execute("SET SESSION time_zone = '+00:00'");

		memberId = insertMember();
		otherMemberId = insertMember();

		String slug = "like-" + UUID.randomUUID();

		jdbcTemplate.update("""
			INSERT INTO board (name, slug, display_type, status, created_by)
			VALUES (?, ?, 'LIST', 'ACTIVE', ?)
			""", "좋아요 테스트", slug, memberId);

		Long boardId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update("""
			INSERT INTO post (board_id, author_id, title, content)
			VALUES (?, ?, ?, ?)
			""", boardId, memberId, "테스트 제목", "테스트 본문");

		postId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	@Test
	@DisplayName("같은 회원이 반복 등록해도 좋아요 하나와 최초 등록 시각을 유지한다")
	void preserveSingleLikeOnRepeatedInsertion() {
		assertFalse(postLikeMapper.existsPostLike(postId, memberId));
		assertEquals(0L, postLikeMapper.countPostLikes(postId));

		postLikeMapper.insertPostLike(postId, memberId);

		LocalDateTime createdAt = jdbcTemplate.queryForObject("""
			SELECT created_at
			FROM post_like
			WHERE post_id = ? AND member_id = ?
			""", LocalDateTime.class, postId, memberId);

		postLikeMapper.insertPostLike(postId, memberId);

		assertTrue(postLikeMapper.existsPostLike(postId, memberId));
		assertEquals(1L, postLikeMapper.countPostLikes(postId));
		assertEquals(createdAt, jdbcTemplate.queryForObject("""
			SELECT created_at
			FROM post_like
			WHERE post_id = ? AND member_id = ?
			""", LocalDateTime.class, postId, memberId));
	}

	@Test
	@DisplayName("좋아요 취소는 본인 기록만 삭제하고 반복 취소도 허용한다")
	void deleteOnlyRequestedMembersLike() {
		postLikeMapper.insertPostLike(postId, memberId);
		postLikeMapper.insertPostLike(postId, otherMemberId);

		assertEquals(2L, postLikeMapper.countPostLikes(postId));

		postLikeMapper.deletePostLike(postId, memberId);
		postLikeMapper.deletePostLike(postId, memberId);

		assertFalse(postLikeMapper.existsPostLike(postId, memberId));
		assertTrue(postLikeMapper.existsPostLike(postId, otherMemberId));
		assertEquals(1L, postLikeMapper.countPostLikes(postId));
	}

	@Test
	@DisplayName("존재하지 않는 회원의 좋아요는 외래 키 제약으로 거절한다")
	void rejectLikeFromMissingMember() {
		assertThrows(DataIntegrityViolationException.class,
			() -> postLikeMapper.insertPostLike(postId, Long.MAX_VALUE));

		assertEquals(0L, postLikeMapper.countPostLikes(postId));
	}

	private Long insertMember() {
		String key = UUID.randomUUID().toString().replace("-", "");

		jdbcTemplate.update("""
			INSERT INTO member (login_id, password_hash, display_name, role)
			VALUES (?, ?, ?, 'MEMBER')
			""", "like-" + key, "unused-in-mapper-tests", key.substring(0, 10));

		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}
}