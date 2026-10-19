package io.github.kwp6zgnrsh.boardkitapi.comment.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
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

import io.github.kwp6zgnrsh.boardkitapi.comment.dto.response.CommentItemResponse;

@MybatisTest(properties = "mybatis.mapper-locations=classpath:mapper/comment/*.xml")
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = CommentLikeMapperIntegrationTest.Config.class)
class CommentLikeMapperIntegrationTest {

	@Autowired
	private CommentLikeMapper commentLikeMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CommentMapper commentMapper;

	private Long memberId;
	private Long otherMemberId;
	private Long postId;
	private Long commentId;
	private Long replyId;

	@Configuration(proxyBeanMethods = false)
	@MapperScan(basePackageClasses = CommentLikeMapper.class)
	static class Config {
	}

	@BeforeEach
	void prepareFixtures() {
		assertEquals("boardkit_test", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));

		jdbcTemplate.execute("SET SESSION time_zone = '+00:00'");

		memberId = insertMember();
		otherMemberId = insertMember();

		jdbcTemplate.update("""
			INSERT INTO board (name, slug, display_type, status, created_by)
			VALUES (?, ?, 'LIST', 'ACTIVE', ?)
			""", "댓글 좋아요 테스트", "comment-like-" + UUID.randomUUID(), memberId);

		Long boardId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update("""
			INSERT INTO post (board_id, author_id, title, content)
			VALUES (?, ?, ?, ?)
			""", boardId, memberId, "테스트 제목", "테스트 본문");

		postId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		commentId = insertComment(null);
		replyId = insertComment(commentId);
	}

	@Test
	@DisplayName("댓글 좋아요를 반복 등록해도 하나의 기록과 최초 등록 시각을 유지한다")
	void preserveSingleLikeOnRepeatedInsertion() {
		assertEquals(0L, countLikes(commentId));

		commentLikeMapper.insertCommentLike(commentId, memberId);

		LocalDateTime createdAt = jdbcTemplate.queryForObject("""
			SELECT created_at
			FROM comment_like
			WHERE comment_id = ? AND member_id = ?
			""", LocalDateTime.class, commentId, memberId);

		commentLikeMapper.insertCommentLike(commentId, memberId);

		assertEquals(1L, countLikes(commentId));
		assertEquals(createdAt, jdbcTemplate.queryForObject("""
			SELECT created_at
			FROM comment_like
			WHERE comment_id = ? AND member_id = ?
			""", LocalDateTime.class, commentId, memberId));
	}

	@Test
	@DisplayName("댓글 좋아요 취소는 본인 기록만 삭제하고 반복 취소도 허용한다")
	void deleteOnlyRequestedMembersLike() {
		commentLikeMapper.insertCommentLike(commentId, memberId);
		commentLikeMapper.insertCommentLike(commentId, otherMemberId);

		assertEquals(2L, countLikes(commentId));

		commentLikeMapper.deleteCommentLike(commentId, memberId);
		commentLikeMapper.deleteCommentLike(commentId, memberId);

		List<Long> remainingMembers = jdbcTemplate.queryForList("""
			SELECT member_id
			FROM comment_like
			WHERE comment_id = ?
			""", Long.class, commentId);

		assertEquals(List.of(otherMemberId), remainingMembers);
	}

	@Test
	@DisplayName("같은 회원이 부모 댓글과 대댓글에 누른 좋아요는 독립적으로 처리한다")
	void manageParentAndReplyLikesIndependently() {
		commentLikeMapper.insertCommentLike(commentId, memberId);
		commentLikeMapper.insertCommentLike(replyId, memberId);

		assertEquals(1L, countLikes(commentId));
		assertEquals(1L, countLikes(replyId));

		commentLikeMapper.deleteCommentLike(commentId, memberId);

		assertEquals(0L, countLikes(commentId));
		assertEquals(1L, countLikes(replyId));
	}

	@Test
	@DisplayName("존재하지 않는 회원이나 댓글의 좋아요는 외래 키 제약으로 거절한다")
	void rejectLikeWhenMemberOrCommentIsMissing() {
		assertThrows(DataIntegrityViolationException.class,
			() -> commentLikeMapper.insertCommentLike(commentId, Long.MAX_VALUE));
		assertThrows(DataIntegrityViolationException.class,
			() -> commentLikeMapper.insertCommentLike(Long.MAX_VALUE, memberId));

		assertEquals(0L, countLikes(commentId));
	}

	@Test
	@DisplayName("댓글과 대댓글의 좋아요 수 및 회원별 선택 상태를 구분한다")
	void readCommentLikesForCurrentMember() {
		List<CommentItemResponse> initial = commentMapper.selectActiveComments(postId, 50, 0L, memberId, false);

		assertTrue(initial.stream().allMatch(comment -> comment.likeCount() == 0 && !comment.likedByMe()));

		commentLikeMapper.insertCommentLike(commentId, memberId);
		commentLikeMapper.insertCommentLike(commentId, otherMemberId);
		commentLikeMapper.insertCommentLike(replyId, otherMemberId);

		List<CommentItemResponse> comments = commentMapper.selectActiveComments(postId, 50, 0L, memberId, false);

		assertEquals(2, comments.size());

		CommentItemResponse parent = comments.getFirst();
		CommentItemResponse reply = comments.get(1);

		assertAll(() -> assertEquals(commentId, parent.id()), () -> assertEquals(2L, parent.likeCount()),
			() -> assertTrue(parent.likedByMe()), () -> assertEquals(replyId, reply.id()),
			() -> assertEquals(1L, reply.likeCount()), () -> assertFalse(reply.likedByMe()));

		List<CommentItemResponse> otherMembersComments = commentMapper.selectActiveComments(postId, 50, 0L,
			otherMemberId, false);

		assertTrue(otherMembersComments.get(1).likedByMe());
	}

	@Test
	@DisplayName("비로그인은 좋아요 수를 조회하지만 내 선택 상태는 항상 false다")
	void readLikeCountsWithoutAuthentication() {
		commentLikeMapper.insertCommentLike(commentId, memberId);
		commentLikeMapper.insertCommentLike(replyId, otherMemberId);

		List<CommentItemResponse> comments = commentMapper.selectActiveComments(postId, 50, 0L, null, false);

		assertEquals(2, comments.size());
		assertTrue(comments.stream().allMatch(comment -> comment.likeCount() == 1 && !comment.likedByMe()));
	}

	@Test
	@DisplayName("삭제된 부모 댓글의 좋아요 정보는 가리고 활성 대댓글의 정보는 유지한다")
	void hideDeletedParentLikesAndKeepReplyLikes() {
		commentLikeMapper.insertCommentLike(commentId, memberId);
		commentLikeMapper.insertCommentLike(replyId, memberId);

		jdbcTemplate.update("UPDATE comment SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", commentId);

		List<CommentItemResponse> comments = commentMapper.selectActiveComments(postId, 50, 0L, memberId, false);

		assertEquals(2, comments.size());

		CommentItemResponse parent = comments.getFirst();
		CommentItemResponse reply = comments.get(1);

		assertAll(() -> assertEquals(commentId, parent.id()), () -> assertTrue(parent.deleted()),
			() -> assertNull(parent.content()), () -> assertEquals(0L, parent.likeCount()),
			() -> assertFalse(parent.likedByMe()), () -> assertEquals(replyId, reply.id()),
			() -> assertFalse(reply.deleted()), () -> assertEquals(1L, reply.likeCount()),
			() -> assertTrue(reply.likedByMe()), () -> assertEquals(1L, countLikes(commentId)));
	}

	@Test
	@DisplayName("페이지가 대댓글부터 시작해도 해당 대댓글의 좋아요 정보를 조회한다")
	void readReplyLikesWhenPageStartsWithReply() {
		commentLikeMapper.insertCommentLike(replyId, memberId);

		List<CommentItemResponse> comments = commentMapper.selectActiveComments(postId, 1, 1L, memberId, false);

		assertEquals(1, comments.size());

		CommentItemResponse reply = comments.getFirst();

		assertAll(() -> assertEquals(replyId, reply.id()), () -> assertEquals(commentId, reply.parentId()),
			() -> assertEquals(1L, reply.likeCount()), () -> assertTrue(reply.likedByMe()));
	}

	@Test
	@DisplayName("블라인드 댓글은 좋아요 대상에서 제외하고 기존 기록과 정상 대댓글은 유지한다")
	void excludeBlindedCommentFromLikeTargets() {
		Long boardId = jdbcTemplate.queryForObject("SELECT board_id FROM post WHERE id = ?", Long.class, postId);
		commentLikeMapper.insertCommentLike(commentId, memberId);

		assertEquals(commentId, commentMapper.selectActiveLikeTargetForShare(boardId, postId, commentId).orElseThrow());

		jdbcTemplate.update("UPDATE comment SET blinded_at = CURRENT_TIMESTAMP(6) WHERE id = ?", commentId);

		assertTrue(commentMapper.selectActiveLikeTargetForShare(boardId, postId, commentId).isEmpty());
		assertEquals(replyId, commentMapper.selectActiveLikeTargetForShare(boardId, postId, replyId).orElseThrow());
		assertEquals(1L, countLikes(commentId));

		jdbcTemplate.update("UPDATE comment SET blinded_at = NULL WHERE id = ?", commentId);

		assertEquals(commentId, commentMapper.selectActiveLikeTargetForShare(boardId, postId, commentId).orElseThrow());
	}

	private Long insertMember() {
		String key = UUID.randomUUID().toString().replace("-", "");

		jdbcTemplate.update("""
			INSERT INTO member (login_id, password_hash, display_name, role)
			VALUES (?, ?, ?, 'MEMBER')
			""", "cl-" + key, "unused-in-mapper-tests", key.substring(0, 10));

		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertComment(Long parentId) {
		jdbcTemplate.update("""
			INSERT INTO comment (post_id, author_id, parent_id, content)
			VALUES (?, ?, ?, ?)
			""", postId, memberId, parentId, "테스트 댓글");

		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private long countLikes(Long targetCommentId) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM comment_like
			WHERE comment_id = ?
			""", Long.class, targetCommentId);
	}
}