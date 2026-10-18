package io.github.kwp6zgnrsh.boardkitapi.member.mapper;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MyCommentSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MyPostSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.model.Member;

@MybatisTest(properties = "mybatis.mapper-locations=classpath:mapper/member/MemberMapper.xml")
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = MemberMapperIntegrationTest.Config.class)
class MemberMapperIntegrationTest {

	@Autowired
	private MemberMapper memberMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Long memberId;
	private String loginId;

	@Configuration(proxyBeanMethods = false)
	@MapperScan(basePackageClasses = MemberMapper.class)
	static class Config {
	}

	@BeforeEach
	void prepareFixtures() {
		assertEquals("boardkit_test", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));
		jdbcTemplate.execute("SET SESSION time_zone = '+00:00'");

		String key = UUID.randomUUID().toString().replace("-", "");
		loginId = "profile-" + key;

		jdbcTemplate.update("""
			INSERT INTO member (
			    login_id, password_hash, display_name, role, auth_version
			)
			VALUES (?, ?, ?, 'MEMBER', 3)
			""", loginId, "old-hash", "테스트" + key.substring(0, 6));

		memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		assertNotNull(memberId);
	}

	@Test
	@DisplayName("회원 ID와 로그인 ID 조회 모두 실제 인증 버전을 반환한다")
	void readAuthVersionFromMemberQueries() {
		Member byId = memberMapper.selectActiveMemberById(memberId).orElseThrow();
		Member byLoginId = memberMapper.selectByLoginId(loginId).orElseThrow();

		assertAll(() -> assertEquals(memberId, byId.getId()), () -> assertEquals(3L, byId.getAuthVersion()),
			() -> assertEquals(memberId, byLoginId.getId()), () -> assertEquals(3L, byLoginId.getAuthVersion()));
	}

	@Test
	@DisplayName("비밀번호 해시와 인증 버전을 함께 변경하고 이전 인증 버전을 거절한다")
	void updatePasswordHashAndAuthVersionTogether() {
		assertTrue(memberMapper.existsActiveMemberByIdAndAuthVersion(memberId, 3L));

		assertEquals(1, memberMapper.updatePassword(memberId, "new-hash", 3L));

		Member updated = memberMapper.selectActiveMemberById(memberId).orElseThrow();

		assertAll(() -> assertEquals("new-hash", updated.getPasswordHash()),
			() -> assertEquals(4L, updated.getAuthVersion()),
			() -> assertFalse(memberMapper.existsActiveMemberByIdAndAuthVersion(memberId, 3L)),
			() -> assertTrue(memberMapper.existsActiveMemberByIdAndAuthVersion(memberId, 4L)));
	}

	@Test
	@DisplayName("이전 인증 버전으로 다시 변경하면 기존 변경 결과를 덮어쓰지 않는다")
	void rejectPasswordUpdateWithStaleAuthVersion() {
		assertEquals(1, memberMapper.updatePassword(memberId, "first-hash", 3L));
		assertEquals(0, memberMapper.updatePassword(memberId, "second-hash", 3L));

		Member saved = memberMapper.selectActiveMemberById(memberId).orElseThrow();

		assertAll(() -> assertEquals("first-hash", saved.getPasswordHash()),
			() -> assertEquals(4L, saved.getAuthVersion()));
	}

	@Test
	@DisplayName("탈퇴한 회원은 버전이 일치해도 인증하거나 비밀번호를 변경할 수 없다")
	void rejectAuthenticationAndPasswordUpdateForDeletedMember() {
		jdbcTemplate.update("""
			UPDATE member
			SET deleted_at = CURRENT_TIMESTAMP(6)
			WHERE id = ?
			""", memberId);

		assertFalse(memberMapper.existsActiveMemberByIdAndAuthVersion(memberId, 3L));
		assertEquals(0, memberMapper.updatePassword(memberId, "new-hash", 3L));
		assertTrue(memberMapper.selectActiveMemberById(memberId).isEmpty());
		assertTrue(memberMapper.selectByLoginId(loginId).isEmpty());

		assertEquals("old-hash",
			jdbcTemplate.queryForObject("SELECT password_hash FROM member WHERE id = ?", String.class, memberId));
		assertEquals(Long.valueOf(3L),
			jdbcTemplate.queryForObject("SELECT auth_version FROM member WHERE id = ?", Long.class, memberId));
	}

	@Test
	@DisplayName("닉네임 변경은 비밀번호 해시와 인증 버전을 변경하지 않는다")
	void preserveAuthenticationWhenDisplayNameChanges() {
		String displayName = "새닉" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);

		assertEquals(1, memberMapper.updateDisplayName(memberId, displayName));

		Member updated = memberMapper.selectActiveMemberById(memberId).orElseThrow();

		assertAll(() -> assertEquals(displayName, updated.getDisplayName()),
			() -> assertEquals("old-hash", updated.getPasswordHash()), () -> assertEquals(3L, updated.getAuthVersion()),
			() -> assertTrue(memberMapper.existsActiveMemberByIdAndAuthVersion(memberId, 3L)));
	}

	@Test
	@DisplayName("내 글 목록과 개수에서 다른 회원의 글과 삭제된 글을 제외한다")
	void excludeOtherMembersPostsAndDeletedPosts() {
		Long boardId = insertActivityBoard("ACTIVE", "PUBLIC");
		Long otherMemberId = insertActivityMember();
		LocalDateTime createdAt = LocalDateTime.of(2026, 10, 18, 12, 0);

		Long visibleId = insertActivityPost(boardId, memberId, "내 글", createdAt, false);
		Long deletedId = insertActivityPost(boardId, memberId, "삭제한 글", createdAt.plusMinutes(1), false);
		insertActivityPost(boardId, otherMemberId, "다른 회원 글", createdAt.plusMinutes(2), false);

		jdbcTemplate.update("UPDATE post SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", deletedId);
		jdbcTemplate.update("UPDATE post SET view_count = 7 WHERE id = ?", visibleId);

		List<MyPostSummaryResponse> posts = memberMapper.selectMyPosts(memberId, 10, 0L);

		assertEquals(1L, memberMapper.countMyPosts(memberId));
		assertEquals(List.of(visibleId), posts.stream().map(MyPostSummaryResponse::id).toList());

		MyPostSummaryResponse post = posts.getFirst();
		assertAll(() -> assertEquals("내 글", post.title()), () -> assertFalse(post.secret()),
			() -> assertEquals(7L, post.viewCount()), () -> assertEquals(createdAt, post.createdAt()));
	}

	@Test
	@DisplayName("보관된 게시판과 관리자 전용 게시판의 내 글도 조회한다")
	void includeOwnPostsFromArchivedAndRestrictedBoards() {
		Long publicBoardId = insertActivityBoard("ACTIVE", "PUBLIC");
		Long archivedBoardId = insertActivityBoard("ARCHIVED", "PUBLIC");
		Long restrictedBoardId = insertActivityBoard("ACTIVE", "ADMIN");
		LocalDateTime createdAt = LocalDateTime.of(2026, 10, 18, 12, 0);

		Long publicId = insertActivityPost(publicBoardId, memberId, "공개 게시판의 내 글", createdAt, false);
		Long archivedId = insertActivityPost(archivedBoardId, memberId, "보관 게시판의 내 글", createdAt.plusMinutes(1), false);
		Long restrictedId = insertActivityPost(restrictedBoardId, memberId, "관리자 전용 게시판의 내 비밀글",
			createdAt.plusMinutes(2), true);

		List<MyPostSummaryResponse> posts = memberMapper.selectMyPosts(memberId, 10, 0L);

		assertEquals(3L, memberMapper.countMyPosts(memberId));
		assertEquals(List.of(restrictedId, archivedId, publicId),
			posts.stream().map(MyPostSummaryResponse::id).toList());

		assertAll(() -> assertEquals("ACTIVE", posts.get(0).boardStatus()), () -> assertTrue(posts.get(0).secret()),
			() -> assertEquals("ARCHIVED", posts.get(1).boardStatus()),
			() -> assertEquals("ACTIVE", posts.get(2).boardStatus()));
	}

	@Test
	@DisplayName("최신순과 동일 시각의 ID 역순을 유지하며 페이지를 나눈다")
	void paginateMyPostsWithStableLatestOrder() {
		Long boardId = insertActivityBoard("ACTIVE", "PUBLIC");
		LocalDateTime createdAt = LocalDateTime.of(2026, 10, 18, 12, 0);

		Long newerFirstId = insertActivityPost(boardId, memberId, "최신 글 A", createdAt.plusMinutes(1), false);
		Long olderId = insertActivityPost(boardId, memberId, "이전 글", createdAt, false);
		Long newerSecondId = insertActivityPost(boardId, memberId, "최신 글 B", createdAt.plusMinutes(1), false);
		Long oldestId = insertActivityPost(boardId, memberId, "가장 오래된 글", createdAt.minusMinutes(1), false);

		List<MyPostSummaryResponse> firstPage = memberMapper.selectMyPosts(memberId, 2, 0L);
		List<MyPostSummaryResponse> secondPage = memberMapper.selectMyPosts(memberId, 2, 2L);

		assertEquals(4L, memberMapper.countMyPosts(memberId));
		assertEquals(List.of(newerSecondId, newerFirstId), firstPage.stream().map(MyPostSummaryResponse::id).toList());
		assertEquals(List.of(olderId, oldestId), secondPage.stream().map(MyPostSummaryResponse::id).toList());
		assertTrue(memberMapper.selectMyPosts(memberId, 2, 4L).isEmpty());
	}

	@Test
	@DisplayName("삭제된 내 댓글과 다른 회원 댓글은 제외하고 남아 있는 내 대댓글은 유지한다")
	void excludeDeletedAndOtherMembersCommentsButKeepOwnReplies() {
		Long boardId = insertActivityBoard("ACTIVE", "PUBLIC");
		Long otherMemberId = insertActivityMember();
		LocalDateTime createdAt = LocalDateTime.of(2026, 10, 18, 12, 0);
		Long postId = insertActivityPost(boardId, otherMemberId, "다른 회원의 글", createdAt, false);

		Long parentId = insertActivityComment(postId, memberId, null, "삭제할 내 부모 댓글", createdAt);
		Long rootId = insertActivityComment(postId, memberId, null, "내 댓글", createdAt.plusMinutes(1));
		Long replyId = insertActivityComment(postId, memberId, parentId, "남아 있는 내 대댓글", createdAt.plusMinutes(2));
		insertActivityComment(postId, otherMemberId, null, "다른 회원 댓글", createdAt.plusMinutes(3));

		jdbcTemplate.update("UPDATE comment SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", parentId);

		List<MyCommentSummaryResponse> comments = memberMapper.selectMyComments(memberId, 10, 0L);

		assertEquals(2L, memberMapper.countMyComments(memberId));
		assertEquals(List.of(replyId, rootId), comments.stream().map(MyCommentSummaryResponse::id).toList());
		assertAll(() -> assertEquals(parentId, comments.get(0).parentId()),
			() -> assertEquals("남아 있는 내 대댓글", comments.get(0).content()), () -> assertNull(comments.get(1).parentId()),
			() -> assertEquals("내 댓글", comments.get(1).content()),
			() -> assertEquals(postId, comments.get(0).postId()));
	}

	@Test
	@DisplayName("삭제된 원글과 보관 및 관리자 전용 게시판의 내 댓글도 조회한다")
	void includeOwnCommentsFromDeletedPostsAndRestrictedBoards() {
		Long publicBoardId = insertActivityBoard("ACTIVE", "PUBLIC");
		Long archivedBoardId = insertActivityBoard("ARCHIVED", "PUBLIC");
		Long restrictedBoardId = insertActivityBoard("ACTIVE", "ADMIN");
		LocalDateTime createdAt = LocalDateTime.of(2026, 10, 18, 12, 0);

		Long deletedPostId = insertActivityPost(publicBoardId, memberId, "삭제할 원글", createdAt, false);
		Long archivedPostId = insertActivityPost(archivedBoardId, memberId, "보관 게시판 글", createdAt, false);
		Long restrictedPostId = insertActivityPost(restrictedBoardId, memberId, "관리자 전용 게시판의 비밀글", createdAt, true);

		Long deletedPostCommentId = insertActivityComment(deletedPostId, memberId, null, "삭제된 원글의 내 댓글", createdAt);
		Long archivedCommentId = insertActivityComment(archivedPostId, memberId, null, "보관 게시판의 내 댓글",
			createdAt.plusMinutes(1));
		Long restrictedCommentId = insertActivityComment(restrictedPostId, memberId, null, "접근 제한 게시판의 내 댓글",
			createdAt.plusMinutes(2));

		jdbcTemplate.update("UPDATE post SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", deletedPostId);

		List<MyCommentSummaryResponse> comments = memberMapper.selectMyComments(memberId, 10, 0L);

		assertEquals(3L, memberMapper.countMyComments(memberId));
		assertEquals(List.of(restrictedCommentId, archivedCommentId, deletedPostCommentId),
			comments.stream().map(MyCommentSummaryResponse::id).toList());
		assertAll(() -> assertFalse(comments.get(0).postDeleted()),
			() -> assertEquals("ARCHIVED", comments.get(1).boardStatus()),
			() -> assertFalse(comments.get(1).postDeleted()), () -> assertTrue(comments.get(2).postDeleted()),
			() -> assertEquals("삭제된 원글의 내 댓글", comments.get(2).content()));
	}

	@Test
	@DisplayName("내 댓글과 대댓글을 작성 시각 및 ID 역순으로 페이지 조회한다")
	void paginateMyCommentsWithStableLatestOrder() {
		Long boardId = insertActivityBoard("ACTIVE", "PUBLIC");
		LocalDateTime createdAt = LocalDateTime.of(2026, 10, 18, 12, 0);
		Long postId = insertActivityPost(boardId, memberId, "테스트 글", createdAt, false);

		Long rootId = insertActivityComment(postId, memberId, null, "부모 댓글", createdAt);
		Long firstReplyId = insertActivityComment(postId, memberId, rootId, "대댓글 A", createdAt.plusMinutes(1));
		Long olderRootId = insertActivityComment(postId, memberId, null, "오래된 댓글", createdAt.minusMinutes(1));
		Long secondReplyId = insertActivityComment(postId, memberId, rootId, "대댓글 B", createdAt.plusMinutes(1));

		List<MyCommentSummaryResponse> firstPage = memberMapper.selectMyComments(memberId, 2, 0L);
		List<MyCommentSummaryResponse> secondPage = memberMapper.selectMyComments(memberId, 2, 2L);

		assertEquals(4L, memberMapper.countMyComments(memberId));
		assertEquals(List.of(secondReplyId, firstReplyId),
			firstPage.stream().map(MyCommentSummaryResponse::id).toList());
		assertEquals(List.of(rootId, olderRootId), secondPage.stream().map(MyCommentSummaryResponse::id).toList());
		assertTrue(memberMapper.selectMyComments(memberId, 2, 4L).isEmpty());
	}

	private Long insertActivityMember() {
		String key = UUID.randomUUID().toString().replace("-", "");

		jdbcTemplate.update("""
			INSERT INTO member (login_id, password_hash, display_name, role)
			VALUES (?, ?, ?, 'MEMBER')
			""", "activity-" + key, "unused-hash", "회원" + key.substring(0, 8));

		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertActivityBoard(String status, String readPermission) {
		String slug = "activity-" + UUID.randomUUID();

		jdbcTemplate.update("""
			INSERT INTO board (name, slug, display_type, status, created_by)
			VALUES (?, ?, 'LIST', ?, ?)
			""", "활동 테스트 게시판", slug, status, memberId);
		Long boardId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update("""
			INSERT INTO board_policy (board_id, read_permission, secret_mode)
			VALUES (?, ?, 'OPTIONAL')
			""", boardId, readPermission);

		return boardId;
	}

	private Long insertActivityPost(Long boardId, Long authorId, String title, LocalDateTime createdAt,
		boolean secret) {
		jdbcTemplate.update("""
			INSERT INTO post (
			    board_id, author_id, title, content,
			    is_secret, secret_password_hash, created_at, updated_at
			)
			VALUES (?, ?, ?, '테스트 본문', ?, ?, ?, ?)
			""", boardId, authorId, title, secret, secret ? "unused-secret-hash" : null, createdAt, createdAt);

		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private Long insertActivityComment(Long postId, Long authorId, Long parentId, String content,
		LocalDateTime createdAt) {
		jdbcTemplate.update("""
			INSERT INTO comment (
			    post_id, author_id, parent_id, content, created_at, updated_at
			)
			VALUES (?, ?, ?, ?, ?, ?)
			""", postId, authorId, parentId, content, createdAt, createdAt);

		return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}
}