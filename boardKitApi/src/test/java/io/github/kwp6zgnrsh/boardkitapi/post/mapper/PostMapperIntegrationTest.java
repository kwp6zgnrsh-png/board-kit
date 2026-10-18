package io.github.kwp6zgnrsh.boardkitapi.post.mapper;

import static org.junit.jupiter.api.Assertions.*;

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
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import io.github.kwp6zgnrsh.boardkitapi.common.mybatis.JsonNodeTypeHandler;
import io.github.kwp6zgnrsh.boardkitapi.file.mapper.PostFileMapper;
import io.github.kwp6zgnrsh.boardkitapi.file.model.PostFile;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostSort;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.model.Post;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostDetail;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PostMapperIntegrationTest.Config.class)
class PostMapperIntegrationTest {

	@Autowired
	private PostMapper postMapper;

	@Autowired
	private PostFileMapper postFileMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Long memberId;
	private Long boardId;

	@Configuration(proxyBeanMethods = false)
	@MapperScan(basePackageClasses = {PostMapper.class, PostFileMapper.class})
	@Import(JsonNodeTypeHandler.class)
	static class Config {
	}

	@BeforeEach
	void prepareFixtures() {
		assertEquals(
			"boardkit_test",
			jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)
		);
		jdbcTemplate.execute("SET SESSION time_zone = '+00:00'");

		String key = UUID.randomUUID().toString().replace("-", "");

		jdbcTemplate.update("""
			INSERT INTO member (login_id, password_hash, display_name, role)
			VALUES (?, ?, ?, 'ADMIN')
			""", "it-" + key, "unused-in-mapper-tests", "테스트" + key.substring(0, 6));

		memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

		jdbcTemplate.update("""
			INSERT INTO board (name, slug, display_type, status, created_by)
			VALUES (?, ?, 'LIST', 'ACTIVE', ?)
			""", "통합 테스트 게시판", "it-" + key, memberId);

		boardId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	@Test
	@DisplayName("게시글 생성 ID와 JSON 본문을 실제 MySQL에서 저장하고 조회한다")
	void insertPostAndReadJsonDocument() {
		Post post = insertPost();

		PostDetail saved = postMapper.selectActivePostDetail(boardId, post.getId())
			.orElseThrow();

		assertAll(
			() -> assertNotNull(post.getId()),
			() -> assertTrue(post.getId() > 0),
			() -> assertEquals("테스트 제목", saved.title()),
			() -> assertEquals("앞 내용\n다음 내용", saved.content()),
			() -> assertEquals(post.getContentDocument(), saved.contentDocument())
		);
	}

	@Test
	@DisplayName("여러 파일을 한 번에 저장하면 각 객체에 올바른 생성 ID가 반영된다")
	void assignGeneratedIdsToEachInsertedFile() {
		Post post = insertPost();
		PostFile first = image(post.getId(), "a.png", 0, true);
		PostFile second = image(post.getId(), "b.png", 1, false);

		assertEquals(2, postFileMapper.insertPostFiles(List.of(first, second)));
		assertNotNull(first.getId());
		assertNotNull(second.getId());
		assertNotEquals(first.getId(), second.getId());

		for (PostFile file : List.of(first, second)) {
			PostFile saved = postFileMapper.selectActivePostFile(post.getId(), file.getId())
				.orElseThrow();

			assertAll(
				() -> assertEquals(file.getOriginalName(), saved.getOriginalName()),
				() -> assertEquals(file.getStorageKey(), saved.getStorageKey()),
				() -> assertEquals(file.getThumbnailKey(), saved.getThumbnailKey())
			);
		}
	}

	@Test
	@DisplayName("대표 이미지가 없는 글도 목록에 포함하고 첫 본문 이미지의 썸네일을 조회한다")
	void readRepresentativeImageFromPostSummaries() {
		Post post = insertPost();

		PostSummaryResponse withoutImage = summary();
		assertNull(withoutImage.representativeFileId());
		assertFalse(withoutImage.thumbnailAvailable());

		PostFile later = image(post.getId(), "later.png", 1, false);
		PostFile first = image(post.getId(), "first.png", 0, true);

		assertEquals(2, postFileMapper.insertPostFiles(List.of(later, first)));
		assertEquals(1, postMapper.refreshRepresentativeImage(boardId, post.getId()));

		PostSummaryResponse withImage = summary();
		assertEquals(first.getId(), withImage.representativeFileId());
		assertTrue(withImage.thumbnailAvailable());
	}

	@Test
	@DisplayName("대표 이미지가 있어도 썸네일 키가 없으면 썸네일 유무는 false다")
	void readRepresentativeImageWithoutThumbnail() {
		Post post = insertPost();
		PostFile file = image(post.getId(), "original.png", 0, false);

		assertEquals(1, postFileMapper.insertPostFiles(List.of(file)));
		assertEquals(1, postMapper.refreshRepresentativeImage(boardId, post.getId()));

		PostSummaryResponse result = summary();
		assertEquals(file.getId(), result.representativeFileId());
		assertFalse(result.thumbnailAvailable());
	}

	@Test
	@DisplayName("이미 사용한 버전으로 다시 수정하면 UPDATE 결과는 0건이다")
	void rejectUpdateWithStaleVersion() {
		Post post = insertPost();

		Post update = Post.builder()
			.id(post.getId())
			.boardId(boardId)
			.authorId(memberId)
			.title("수정 제목")
			.content("수정 내용")
			.contentDocument(document("수정 내용"))
			.version(0)
			.build();

		assertEquals(1, postMapper.updatePost(update));
		assertEquals(0, postMapper.updatePost(update));

		PostDetail saved = postMapper.selectActivePostDetail(boardId, post.getId())
			.orElseThrow();

		assertEquals(Integer.valueOf(1), saved.version());
		assertEquals("수정 제목", saved.title());
	}

	@Test
	@DisplayName("존재하지 않는 게시글에 파일을 연결하면 FK 제약으로 거절된다")
	void rejectFileForMissingPost() {
		PostFile file = image(Long.MAX_VALUE, "missing.png", 0, false);

		assertThrows(
			DataIntegrityViolationException.class,
			() -> postFileMapper.insertPostFiles(List.of(file))
		);
	}

	@Test
	@DisplayName("좋아요가 없는 게시글의 목록 좋아요 수는 0이다")
	void returnZeroLikeCountWhenNoLikesExist() {
		insertPost();

		assertEquals(0L, summary().likeCount());
	}

	@Test
	@DisplayName("게시글별 좋아요 수를 다른 게시글 및 댓글 수와 구분하여 집계한다")
	void countLikesSeparatelyForEachPost() {
		Post firstPost = insertPost();
		Post secondPost = insertPost();

		String key = UUID.randomUUID().toString().replace("-", "");

		jdbcTemplate.update("""
		INSERT INTO member (login_id, password_hash, display_name, role)
		VALUES (?, ?, ?, 'MEMBER')
		""",
			"like-" + key,
			"unused-in-mapper-tests",
			key.substring(0, 10)
		);

		Long otherMemberId = jdbcTemplate.queryForObject(
			"SELECT LAST_INSERT_ID()", Long.class
		);

		jdbcTemplate.update("""
		INSERT INTO post_like (post_id, member_id)
		VALUES (?, ?), (?, ?), (?, ?)
		""",
			firstPost.getId(), memberId,
			firstPost.getId(), otherMemberId,
			secondPost.getId(), memberId
		);

		jdbcTemplate.update("""
		INSERT INTO comment (post_id, author_id, content)
		VALUES (?, ?, ?), (?, ?, ?)
		""",
			firstPost.getId(), memberId, "첫 댓글",
			firstPost.getId(), otherMemberId, "두 번째 댓글"
		);

		List<PostSummaryResponse> posts = postMapper.selectPostSummaries(
			boardId, null, null, PostSort.LATEST, 10, 0L
		);

		assertEquals(2, posts.size());

		PostSummaryResponse first = posts.stream()
			.filter(post -> post.id().equals(firstPost.getId()))
			.findFirst()
			.orElseThrow();

		PostSummaryResponse second = posts.stream()
			.filter(post -> post.id().equals(secondPost.getId()))
			.findFirst()
			.orElseThrow();

		assertAll(
			() -> assertEquals(2L, first.likeCount()),
			() -> assertEquals(2L, first.commentCount()),
			() -> assertEquals(1L, second.likeCount()),
			() -> assertEquals(0L, second.commentCount())
		);
	}

	private Post insertPost() {
		Post post = Post.builder()
			.boardId(boardId)
			.authorId(memberId)
			.title("테스트 제목")
			.content("앞 내용\n다음 내용")
			.contentDocument(document("앞 내용\n다음 내용"))
			.viewCount(0L)
			.version(0)
			.build();

		assertEquals(1, postMapper.insertPost(post));
		return post;
	}

	private PostFile image(Long postId, String name, int order, boolean thumbnail) {
		String key = UUID.randomUUID().toString();

		return PostFile.builder()
			.postId(postId)
			.originalName(name)
			.storageKey("posts/" + postId + "/" + key + ".png")
			.contentType("image/png")
			.fileUsage("BODY_IMAGE")
			.sizeBytes(100L)
			.sortOrder(order)
			.thumbnailKey(thumbnail ? "thumbnails/" + postId + "/" + key + ".png" : null)
			.build();
	}

	private PostSummaryResponse summary() {
		List<PostSummaryResponse> posts = postMapper.selectPostSummaries(
			boardId, null, null, PostSort.LATEST, 10, 0L
		);

		assertEquals(1, posts.size());
		return posts.getFirst();
	}

	private JsonNode document(String text) {
		var document = JsonNodeFactory.instance.objectNode();
		document.put("type", "doc");

		var paragraph = document.putArray("content").addObject();
		paragraph.put("type", "paragraph");
		paragraph.putArray("content").addObject()
			.put("type", "text")
			.put("text", text);

		return document;
	}
}