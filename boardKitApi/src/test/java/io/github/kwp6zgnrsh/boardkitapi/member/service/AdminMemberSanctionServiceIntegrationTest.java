package io.github.kwp6zgnrsh.boardkitapi.member.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberSanctionCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberSanctionMapper;
import io.github.kwp6zgnrsh.boardkitapi.member.model.MemberSanction;

@MybatisTest(properties = {"mybatis.mapper-locations=classpath:mapper/member/*.xml",
	"spring.datasource.hikari.maximum-pool-size=4", "spring.datasource.hikari.minimum-idle=0",
	"spring.datasource.hikari.connection-init-sql=" + "SET SESSION innodb_lock_wait_timeout = 5, time_zone = '+09:00'"})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AdminMemberSanctionServiceIntegrationTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminMemberSanctionServiceIntegrationTest {

	@Autowired
	private AdminMemberSanctionService service;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	@MockitoSpyBean
	private MemberMapper memberMapper;
	@MockitoSpyBean
	private MemberSanctionMapper sanctionMapper;

	private final List<Long> memberIds = new ArrayList<>();
	private boolean databaseVerified;
	private Long adminId;
	private Long memberId;

	@Configuration(proxyBeanMethods = false)
	@EnableTransactionManagement
	@MapperScan(basePackageClasses = MemberMapper.class)
	@Import(AdminMemberSanctionService.class)
	static class Config {
	}

	@BeforeEach
	void prepareFixtures() {
		assertEquals("boardkit_test", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));
		databaseVerified = true;

		adminId = insertMember("ADMIN");
		memberId = insertMember("MEMBER");
	}

	@AfterEach
	void cleanUpFixtures() {
		if (!databaseVerified) {
			return;
		}

		for (Long id : memberIds) {
			jdbcTemplate.update("DELETE FROM member_sanction WHERE member_id = ?", id);
		}
		for (Long id : memberIds) {
			jdbcTemplate.update("DELETE FROM member WHERE id = ?", id);
		}
	}

	@Test
	@DisplayName("같은 회원에게 동시에 제재를 등록하면 하나만 성공하고 나머지는 충돌한다")
	void rejectDuplicateSanctionDuringConcurrentCreation() throws Exception {
		CyclicBarrier barrier = new CyclicBarrier(2);

		doAnswer(invocation -> {
			Long id = invocation.getArgument(0);
			Boolean active = sqlSessionTemplate.selectOne(MemberMapper.class.getName() + ".existsActiveMemberById",
				Map.of("memberId", id));

			// 두 트랜잭션 모두 최초 조회를 마친 뒤 회원 잠금으로 진행한다.
			barrier.await(10, TimeUnit.SECONDS);
			return Boolean.TRUE.equals(active);
		}).when(memberMapper).existsActiveMemberById(adminId);

		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> tryCreate(memberId));
			var second = executor.submit(() -> tryCreate(memberId));

			List<Boolean> results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

			assertEquals(1L, results.stream().filter(Boolean::booleanValue).count());
		}

		assertEquals(1L, countSanctions(memberId));
	}

	@Test
	@DisplayName("첫 회원의 제재가 커밋되기 전에도 다른 회원의 제재는 완료할 수 있다")
	void createSanctionsForDifferentMembersIndependently() throws Exception {
		Long otherMemberId = insertMember("MEMBER");
		CountDownLatch firstInserted = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);

		doAnswer(invocation -> {
			MemberSanction sanction = invocation.getArgument(0);
			int insertedCount = insertWithRealSql(sanction);

			if (memberId.equals(sanction.getMemberId())) {
				firstInserted.countDown();
				assertTrue(releaseFirst.await(10, TimeUnit.SECONDS), "첫 번째 트랜잭션의 대기가 해제되지 않았습니다.");
			}

			return insertedCount;
		}).when(sanctionMapper).insertSanction(any(MemberSanction.class));

		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> service.createSanction(request(memberId), adminId, true));

			try {
				assertTrue(firstInserted.await(10, TimeUnit.SECONDS), "첫 번째 INSERT까지 도달하지 못했습니다.");

				var second = executor.submit(() -> service.createSanction(request(otherMemberId), adminId, true));

				assertNotNull(second.get(10, TimeUnit.SECONDS));
				assertFalse(first.isDone());

				// 첫 요청은 아직 미커밋이고, 두 번째 요청은 커밋되었다.
				assertEquals(0L, countSanctions(memberId));
				assertEquals(1L, countSanctions(otherMemberId));
			} finally {
				releaseFirst.countDown();
			}

			assertNotNull(first.get(10, TimeUnit.SECONDS));
		}

		assertEquals(1L, countSanctions(memberId));
	}

	@Test
	@DisplayName("실제 INSERT 이후 서비스가 실패하면 제재 기록을 롤백한다")
	void rollBackSanctionWhenServiceFailsAfterInsert() {
		doAnswer(invocation -> {
			MemberSanction sanction = invocation.getArgument(0);

			assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
			assertEquals(Integer.valueOf(Connection.TRANSACTION_READ_COMMITTED),
				TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());

			assertEquals(1, insertWithRealSql(sanction));

			// 실제 저장 후 Service의 저장 실패 검사를 발생시킨다.
			return 0;
		}).when(sanctionMapper).insertSanction(any(MemberSanction.class));

		assertThrows(IllegalStateException.class, () -> service.createSanction(request(memberId), adminId, true));

		assertEquals(0L, countSanctions(memberId));
	}

	private boolean tryCreate(Long targetId) {
		try {
			service.createSanction(request(targetId), adminId, true);
			return true;
		} catch (ResponseStatusException exception) {
			if (!exception.getStatusCode().equals(HttpStatus.CONFLICT)) {
				throw exception;
			}
			return false;
		}
	}

	private int insertWithRealSql(MemberSanction sanction) {
		return sqlSessionTemplate.insert(MemberSanctionMapper.class.getName() + ".insertSanction", sanction);
	}

	private MemberSanctionCreateRequest request(Long targetId) {
		return new MemberSanctionCreateRequest(targetId, "통합 테스트 제재", OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
	}

	private long countSanctions(Long targetId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member_sanction WHERE member_id = ?", Long.class,
			targetId);
	}

	private Long insertMember(String role) {
		String key = UUID.randomUUID().toString().replace("-", "");
		var keyHolder = new GeneratedKeyHolder();

		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement("""
				INSERT INTO member (login_id, password_hash, display_name, role)
				VALUES (?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS);

			statement.setString(1, "sanction-it-" + key);
			statement.setString(2, "unused-test-hash");
			statement.setString(3, "통합" + key.substring(0, 8));
			statement.setString(4, role);
			return statement;
		}, keyHolder);

		Number keyValue = keyHolder.getKey();
		assertNotNull(keyValue);

		Long id = keyValue.longValue();
		memberIds.add(id);
		return id;
	}
}
