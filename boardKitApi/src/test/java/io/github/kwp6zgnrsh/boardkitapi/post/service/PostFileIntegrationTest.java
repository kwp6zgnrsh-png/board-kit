package io.github.kwp6zgnrsh.boardkitapi.post.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mapstruct.factory.Mappers;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.github.kwp6zgnrsh.boardkitapi.board.mapper.BoardMapper;
import io.github.kwp6zgnrsh.boardkitapi.common.mybatis.JsonNodeTypeHandler;
import io.github.kwp6zgnrsh.boardkitapi.file.config.FileStorageProperties;
import io.github.kwp6zgnrsh.boardkitapi.file.mapper.FileCleanupTaskMapper;
import io.github.kwp6zgnrsh.boardkitapi.file.mapper.PostFileMapper;
import io.github.kwp6zgnrsh.boardkitapi.file.model.FileCleanupTask;
import io.github.kwp6zgnrsh.boardkitapi.file.model.PostFile;
import io.github.kwp6zgnrsh.boardkitapi.file.service.FileCleanupService;
import io.github.kwp6zgnrsh.boardkitapi.file.service.FileSignatureDetector;
import io.github.kwp6zgnrsh.boardkitapi.file.service.FileUploadValidator;
import io.github.kwp6zgnrsh.boardkitapi.file.service.LocalFileStorage;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.converter.PostConverter;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostDetail;
import io.github.kwp6zgnrsh.boardkitapi.post.policy.PostSecretAccess;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PostFileIntegrationTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostFileIntegrationTest {

	@MockitoSpyBean
	private FileCleanupTaskMapper fileCleanupTaskMapper;

	@MockitoSpyBean
	private LocalFileStorage localFileStorage;

	@TempDir
	static Path storageRoot;

	@Autowired
	private PostService postService;

	@Autowired
	private PostFileMapper postFileMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private FileCleanupService fileCleanupService;

	@MockitoSpyBean
	private PostMapper postMapper;

	private Long memberId;
	private Long boardId;
	private String boardSlug;

	@DynamicPropertySource
	static void configureStorage(DynamicPropertyRegistry registry) {
		registry.add("boardkit.file.storage-root", () -> storageRoot.toString());
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(FileStorageProperties.class)
	@MapperScan(basePackageClasses = {BoardMapper.class, MemberMapper.class, PostMapper.class, PostFileMapper.class})
	@Import({PostService.class, PostSecretAccess.class, PostDocumentValidator.class, JsonNodeTypeHandler.class,
		FileSignatureDetector.class, FileUploadValidator.class, LocalFileStorage.class, FileCleanupService.class})
	static class Config {

		@Bean
		PostConverter postConverter() {
			return Mappers.getMapper(PostConverter.class);
		}

		@Bean
		PasswordEncoder passwordEncoder() {
			return new BCryptPasswordEncoder();
		}
	}

	@BeforeEach
	void prepareFixtures() {
		assertEquals("boardkit_test", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));

		String key = UUID.randomUUID().toString().replace("-", "");
		String loginId = "file-it-" + key;
		boardSlug = "file-it-" + key;

		jdbcTemplate.update("""
			INSERT INTO member (login_id, password_hash, display_name, role)
			VALUES (?, ?, ?, 'ADMIN')
			""", loginId, "unused-in-file-tests", "파일" + key.substring(0, 6));

		memberId = jdbcTemplate.queryForObject("SELECT id FROM member WHERE login_id = ?", Long.class, loginId);

		jdbcTemplate.update("""
			INSERT INTO board (name, slug, display_type, status, created_by)
			VALUES (?, ?, 'LIST', 'ACTIVE', ?)
			""", "파일 통합 테스트", boardSlug, memberId);

		boardId = jdbcTemplate.queryForObject("SELECT id FROM board WHERE slug = ?", Long.class, boardSlug);

		jdbcTemplate.update("""
			INSERT INTO board_policy (
				board_id, read_permission, write_permission, secret_mode,
				attachments_enabled, upload_profile,
				max_file_count, max_file_size_bytes, thumbnail_enabled
			)
			VALUES (?, 'PUBLIC', 'MEMBER', 'OFF', 1, 'IMAGE', 5, 1048576, 1)
			""", boardId);
	}

	@AfterEach
	void cleanFixtures() throws IOException {
		if (boardId != null) {
			jdbcTemplate.update("""
				DELETE task FROM file_cleanup_task task
				INNER JOIN post_file pf
					ON task.target_key IN (pf.storage_key, pf.thumbnail_key)
				INNER JOIN post p ON p.id = pf.post_id
				WHERE p.board_id = ?
				""", boardId);

			jdbcTemplate.update("""
				UPDATE post SET representative_file_id = NULL
				WHERE board_id = ?
				""", boardId);

			jdbcTemplate.update("""
				DELETE pf FROM post_file pf
				INNER JOIN post p ON p.id = pf.post_id
				WHERE p.board_id = ?
				""", boardId);

			jdbcTemplate.update("DELETE FROM post WHERE board_id = ?", boardId);
			jdbcTemplate.update("DELETE FROM board_policy WHERE board_id = ?", boardId);
			jdbcTemplate.update("DELETE FROM board WHERE id = ?", boardId);
		}

		if (memberId != null) {
			jdbcTemplate.update("DELETE FROM member WHERE id = ?", memberId);
		}

		try (Stream<Path> paths = Files.walk(storageRoot)) {
			for (Path file : paths.filter(Files::isRegularFile).toList()) {
				Files.deleteIfExists(file);
			}
		}
	}

	@Test
	@DisplayName("이미지 게시글을 등록하면 DB와 원본 및 썸네일이 함께 저장된다")
	void commitPostWithOriginalAndThumbnail() throws IOException {
		assertFalse(TransactionSynchronizationManager.isActualTransactionActive());

		PostCreateResponse response = postService.createPost(boardSlug, request(), List.of(image()), memberId, true);

		assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
		assertEquals(1L, countPosts());
		assertEquals(1L, countPostFiles());

		List<PostFile> files = postFileMapper.selectActivePostFilesForUpdate(response.id());
		assertEquals(1, files.size());

		PostFile file = files.getFirst();
		PostDetail saved = postMapper.selectActivePostDetail(boardId, response.id()).orElseThrow();

		assertEquals("BODY_IMAGE", file.getFileUsage());
		assertEquals(file.getId(), saved.representativeFileId());

		JsonNode attributes = saved.contentDocument().path("content").get(0).path("attrs");

		assertEquals(file.getId().longValue(), attributes.path("fileId").longValue());
		assertFalse(attributes.has("fileIndex"));

		assertTrue(Files.isRegularFile(storageRoot.resolve(file.getStorageKey())));
		assertNotNull(file.getThumbnailKey());

		Path thumbnailPath = storageRoot.resolve(file.getThumbnailKey());
		assertTrue(Files.isRegularFile(thumbnailPath));

		BufferedImage thumbnail = ImageIO.read(thumbnailPath.toFile());
		assertNotNull(thumbnail);
		assertEquals(360, thumbnail.getWidth());
		assertEquals(360, thumbnail.getHeight());

		assertEquals(0L, countFiles("temp"));
	}

	@Test
	@DisplayName("파일 저장 후 대표 이미지 처리에 실패하면 DB와 새 파일을 정리한다")
	void rollbackPostAndDeleteFilesWhenRepresentativeImageFails() throws IOException {
		IllegalStateException failure = new IllegalStateException("테스트용 처리 실패");

		doAnswer(invocation -> {
			assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
			assertEquals(1L, countPosts());
			assertEquals(1L, countPostFiles());
			assertEquals(1L, countFiles("posts"));
			assertEquals(1L, countFiles("thumbnails"));
			throw failure;
		}).when(postMapper).refreshRepresentativeImage(eq(boardId), anyLong());

		MockMultipartFile image = image();

		IllegalStateException actual = assertThrows(IllegalStateException.class,
			() -> postService.createPost(boardSlug, request(), List.of(image), memberId, true));

		assertSame(failure, actual);
		assertFalse(TransactionSynchronizationManager.isActualTransactionActive());

		assertEquals(0L, countPosts());
		assertEquals(0L, countPostFiles());
		assertEquals(0L, countFiles("temp"));
		assertEquals(0L, countFiles("posts"));
		assertEquals(0L, countFiles("thumbnails"));
	}

	@Test
	@DisplayName("파일 교체 중 수정에 실패하면 기존 글과 파일을 유지하고 새 파일만 삭제한다")
	void preserveExistingFilesWhenPostUpdateFails() throws IOException {
		PostCreateResponse response = postService.createPost(boardSlug, request(), List.of(image()), memberId, true);

		Long postId = response.id();
		PostDetail before = postMapper.selectActivePostDetail(boardId, postId).orElseThrow();

		PostFile existingFile = postFileMapper.selectActivePostFilesForUpdate(postId).getFirst();

		Path originalPath = storageRoot.resolve(existingFile.getStorageKey());
		Path thumbnailPath = storageRoot.resolve(existingFile.getThumbnailKey());

		byte[] originalBytes = Files.readAllBytes(originalPath);
		byte[] thumbnailBytes = Files.readAllBytes(thumbnailPath);

		PostUpdateRequest update = new PostUpdateRequest("수정된 제목", request().contentDocument(), null, false, null,
			false, false, before.version(), List.of());

		IllegalStateException failure = new IllegalStateException("테스트용 수정 실패");

		doAnswer(invocation -> {
			assertTrue(TransactionSynchronizationManager.isActualTransactionActive());

			// 기존 파일 제거와 새 파일 저장이 이루어진 이후에 실패시킨다.
			assertTrue(postFileMapper.selectActivePostFile(postId, existingFile.getId()).isEmpty());
			assertEquals(2L, countPostFiles());
			assertEquals(2L, countFiles("posts"));
			assertEquals(2L, countFiles("thumbnails"));
			assertEquals(2L, countCleanupTasks(existingFile.getStorageKey(), existingFile.getThumbnailKey()));

			throw failure;
		}).when(postMapper).refreshRepresentativeImage(boardId, postId);

		MockMultipartFile newImage = image();

		IllegalStateException actual = assertThrows(IllegalStateException.class,
			() -> postService.updatePost(boardSlug, postId, update, List.of(newImage), memberId, true));

		assertSame(failure, actual);
		assertFalse(TransactionSynchronizationManager.isActualTransactionActive());

		PostDetail after = postMapper.selectActivePostDetail(boardId, postId).orElseThrow();

		assertAll(() -> assertEquals(before.title(), after.title()),
			() -> assertEquals(before.content(), after.content()),
			() -> assertEquals(before.contentDocument(), after.contentDocument()),
			() -> assertEquals(before.version(), after.version()),
			() -> assertEquals(before.representativeFileId(), after.representativeFileId()));

		assertTrue(postFileMapper.selectActivePostFile(postId, existingFile.getId()).isPresent());
		assertEquals(1L, countPosts());
		assertEquals(1L, countPostFiles());

		assertArrayEquals(originalBytes, Files.readAllBytes(originalPath));
		assertArrayEquals(thumbnailBytes, Files.readAllBytes(thumbnailPath));

		assertEquals(0L, countFiles("temp"));
		assertEquals(1L, countFiles("posts"));
		assertEquals(1L, countFiles("thumbnails"));

		assertEquals(0L, countCleanupTasks(existingFile.getStorageKey(), existingFile.getThumbnailKey()));
	}

	@Test
	@DisplayName("게시글 삭제 작업을 배치로 처리하면 원본과 썸네일 및 작업 행을 제거한다")
	void deletePostFilesThroughCleanupBatch() throws IOException {
		PostCreateResponse response = postService.createPost(boardSlug, request(), List.of(image()), memberId, true);
		Long postId = response.id();
		PostDetail before = postMapper.selectActivePostDetail(boardId, postId).orElseThrow();
		PostFile file = postFileMapper.selectActivePostFilesForUpdate(postId).getFirst();

		postService.deletePost(boardSlug, postId, before.version(), memberId, true);

		assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
		assertTrue(postMapper.selectActivePostDetail(boardId, postId).isEmpty());
		assertTrue(postFileMapper.selectActivePostFile(postId, file.getId()).isEmpty());

		assertEquals(1L, jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM post
			WHERE id = ? AND deleted_at IS NOT NULL
			""", Long.class, postId));
		assertEquals(1L, jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM post_file
			WHERE id = ? AND deleted_at IS NOT NULL
			""", Long.class, file.getId()));

		assertEquals(2L, countCleanupTasks(file.getStorageKey(), file.getThumbnailKey()));
		verify(fileCleanupTaskMapper).enqueueDeletes(List.of(file.getStorageKey(), file.getThumbnailKey()));

		// 배치 실행 전에는 실제 파일이 남아 있어야 한다.
		assertTrue(Files.isRegularFile(storageRoot.resolve(file.getStorageKey())));
		assertTrue(Files.isRegularFile(storageRoot.resolve(file.getThumbnailKey())));

		// 이번 테스트가 등록한 작업만 실제 DB에서 읽는다.
		List<FileCleanupTask> tasks = readCleanupTasks(file.getStorageKey(), file.getThumbnailKey());
		assertEquals(2, tasks.size());

		// 다른 테스트의 작업을 처리하지 않도록 조회 대상만 제한한다.
		doReturn(tasks).when(fileCleanupTaskMapper).selectDueTasks(anyInt());

		fileCleanupService.processDueTasks();

		assertFalse(Files.exists(storageRoot.resolve(file.getStorageKey())));
		assertFalse(Files.exists(storageRoot.resolve(file.getThumbnailKey())));
		assertEquals(0L, countCleanupTasks(file.getStorageKey(), file.getThumbnailKey()));

		// 물리 파일을 삭제해도 soft delete한 DB 기록은 남는다.
		assertEquals(1L, countPosts());
		assertEquals(1L, countPostFiles());

		for (FileCleanupTask task : tasks) {
			verify(fileCleanupTaskMapper).deleteTask(task.getId());
		}
	}

	@Test
	@DisplayName("파일 정리 작업 등록에 실패하면 게시글과 파일 삭제 표시를 롤백한다")
	void rollbackPostDeletionWhenCleanupEnqueueFails() throws IOException {
		PostCreateResponse response = postService.createPost(boardSlug, request(), List.of(image()), memberId, true);
		Long postId = response.id();
		PostDetail before = postMapper.selectActivePostDetail(boardId, postId).orElseThrow();
		PostFile file = postFileMapper.selectActivePostFilesForUpdate(postId).getFirst();
		IllegalStateException failure = new IllegalStateException("테스트용 작업 등록 실패");

		doAnswer(invocation -> {
			assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
			assertTrue(postMapper.selectActivePostDetail(boardId, postId).isEmpty());
			assertTrue(postFileMapper.selectActivePostFile(postId, file.getId()).isEmpty());
			throw failure;
		}).when(fileCleanupTaskMapper).enqueueDeletes(anyList());

		IllegalStateException actual = assertThrows(IllegalStateException.class,
			() -> postService.deletePost(boardSlug, postId, before.version(), memberId, true));

		assertSame(failure, actual);
		assertFalse(TransactionSynchronizationManager.isActualTransactionActive());

		PostDetail after = postMapper.selectActivePostDetail(boardId, postId).orElseThrow();
		assertEquals(before.version(), after.version());
		assertEquals(before.representativeFileId(), after.representativeFileId());
		assertTrue(postFileMapper.selectActivePostFile(postId, file.getId()).isPresent());
		assertEquals(0L, countCleanupTasks(file.getStorageKey(), file.getThumbnailKey()));
		assertTrue(Files.isRegularFile(storageRoot.resolve(file.getStorageKey())));
		assertTrue(Files.isRegularFile(storageRoot.resolve(file.getThumbnailKey())));
	}

	@Test
	@DisplayName("파일 삭제 실패를 기록하고 재시도에 성공하면 파일과 작업을 제거한다")
	void retryFailedFileDeletionAndCompleteTask() throws IOException {
		PostCreateResponse response = postService.createPost(boardSlug, request(), List.of(image()), memberId, true);
		Long postId = response.id();
		PostDetail post = postMapper.selectActivePostDetail(boardId, postId).orElseThrow();
		PostFile file = postFileMapper.selectActivePostFilesForUpdate(postId).getFirst();

		postService.deletePost(boardSlug, postId, post.version(), memberId, true);

		List<FileCleanupTask> tasks = readCleanupTasks(file.getStorageKey(), file.getThumbnailKey());
		assertEquals(2, tasks.size());

		FileCleanupTask originalTask = tasks.stream()
			.filter(task -> task.getTargetKey().equals(file.getStorageKey()))
			.findFirst()
			.orElseThrow();

		doReturn(tasks).when(fileCleanupTaskMapper).selectDueTasks(anyInt());

		// 원본의 첫 삭제만 실패시키고, 다음 호출은 실제 파일을 삭제한다.
		doThrow(new IllegalStateException("테스트용 파일 삭제 실패")).doCallRealMethod()
			.when(localFileStorage)
			.delete(file.getStorageKey());

		assertDoesNotThrow(() -> fileCleanupService.processDueTasks());

		List<FileCleanupTask> remaining = readCleanupTasks(file.getStorageKey(), file.getThumbnailKey());
		assertEquals(1, remaining.size());

		FileCleanupTask failedTask = remaining.getFirst();
		assertEquals(originalTask.getId(), failedTask.getId());
		assertEquals(1, failedTask.getAttempts());
		assertNotNull(failedTask.getLastError());
		assertTrue(failedTask.getLastError().contains("테스트용 파일 삭제 실패"));

		verify(fileCleanupTaskMapper).recordFailure(eq(originalTask.getId()), eq(60L), anyString());
		verify(fileCleanupTaskMapper, never()).deleteTask(originalTask.getId());

		assertEquals(1L, jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM file_cleanup_task
			WHERE id = ? AND next_attempt_at > CURRENT_TIMESTAMP(6)
			""", Long.class, originalTask.getId()));

		assertTrue(Files.isRegularFile(storageRoot.resolve(file.getStorageKey())));
		assertFalse(Files.exists(storageRoot.resolve(file.getThumbnailKey())));

		// 기다리지 않고 이번 테스트의 작업만 재시도 가능한 시점으로 바꾼다.
		assertEquals(1, jdbcTemplate.update("""
			UPDATE file_cleanup_task
			SET next_attempt_at = CURRENT_TIMESTAMP(6)
			WHERE id = ?
			""", originalTask.getId()));

		List<FileCleanupTask> retryTasks = readCleanupTasks(file.getStorageKey(), file.getThumbnailKey());
		doReturn(retryTasks).when(fileCleanupTaskMapper).selectDueTasks(anyInt());

		fileCleanupService.processDueTasks();

		assertFalse(Files.exists(storageRoot.resolve(file.getStorageKey())));
		assertFalse(Files.exists(storageRoot.resolve(file.getThumbnailKey())));
		assertEquals(0L, countCleanupTasks(file.getStorageKey(), file.getThumbnailKey()));
		verify(fileCleanupTaskMapper).deleteTask(originalTask.getId());
	}

	private PostCreateRequest request() {
		var document = JsonNodeFactory.instance.objectNode();
		document.put("type", "doc");

		var image = document.putArray("content").addObject();
		image.put("type", "image");
		image.putObject("attrs").put("fileIndex", 0);

		return new PostCreateRequest("파일 통합 테스트", document, null, false, null, false);
	}

	private MockMultipartFile image() throws IOException {
		BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);

		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			assertTrue(ImageIO.write(image, "png", output));

			return new MockMultipartFile("files", "test.png", "image/png", output.toByteArray());
		}
	}

	private long countPosts() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post WHERE board_id = ?", Long.class, boardId);
	}

	private long countPostFiles() {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM post_file pf
			INNER JOIN post p ON p.id = pf.post_id
			WHERE p.board_id = ?
			""", Long.class, boardId);
	}

	private long countFiles(String directory) throws IOException {
		try (Stream<Path> paths = Files.walk(storageRoot.resolve(directory))) {
			return paths.filter(Files::isRegularFile).count();
		}
	}

	private long countCleanupTasks(String storageKey, String thumbnailKey) {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM file_cleanup_task
			WHERE target_key IN (?, ?)
			""", Long.class, storageKey, thumbnailKey);
	}

	private List<FileCleanupTask> readCleanupTasks(String storageKey, String thumbnailKey) {
		return jdbcTemplate.query("""
			SELECT id, target_key, attempts, last_error
			FROM file_cleanup_task
			WHERE target_key IN (?, ?)
			ORDER BY id
			""", (resultSet, rowNum) -> FileCleanupTask.builder()
			.id(resultSet.getLong("id"))
			.targetKey(resultSet.getString("target_key"))
			.attempts(resultSet.getInt("attempts"))
			.lastError(resultSet.getString("last_error"))
			.build(), storageKey, thumbnailKey);
	}
}