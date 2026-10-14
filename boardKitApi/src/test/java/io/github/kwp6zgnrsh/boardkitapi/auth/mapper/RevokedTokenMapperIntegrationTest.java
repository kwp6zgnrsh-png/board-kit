package io.github.kwp6zgnrsh.boardkitapi.auth.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

@MybatisTest(properties = "mybatis.mapper-locations=classpath:mapper/auth/RevokedTokenMapper.xml")
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = RevokedTokenMapperIntegrationTest.Config.class)
class RevokedTokenMapperIntegrationTest {

	@Autowired
	private RevokedTokenMapper revokedTokenMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Configuration(proxyBeanMethods = false)
	@MapperScan(basePackageClasses = RevokedTokenMapper.class)
	static class Config {
	}

	@BeforeEach
	void verifyTestDatabase() {
		assertEquals("boardkit_test", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));
		jdbcTemplate.execute("SET SESSION time_zone = '+00:00'");
	}

	@Test
	@DisplayName("등록한 토큰만 무효화된 토큰으로 조회한다")
	void findOnlyRegisteredRevokedToken() {
		String tokenId = UUID.randomUUID().toString();
		LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusHours(1).withNano(0);

		assertFalse(revokedTokenMapper.existsRevokedToken(tokenId));

		revokedTokenMapper.insertRevokedToken(tokenId, expiresAt);

		assertTrue(revokedTokenMapper.existsRevokedToken(tokenId));
		assertFalse(revokedTokenMapper.existsRevokedToken(UUID.randomUUID().toString()));
	}

	@Test
	@DisplayName("같은 토큰을 반복 등록해도 기록과 원래 만료 시각을 유지한다")
	void preserveExpirationOnDuplicateRevocation() {
		String tokenId = UUID.randomUUID().toString();
		LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusHours(1).withNano(0);

		revokedTokenMapper.insertRevokedToken(tokenId, expiresAt);
		assertDoesNotThrow(() -> revokedTokenMapper.insertRevokedToken(tokenId, expiresAt.plusHours(1)));

		assertEquals(1L, jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM revoked_token
			WHERE token_id = ?
			""", Long.class, tokenId));

		assertEquals(expiresAt, jdbcTemplate.queryForObject("""
			SELECT expires_at FROM revoked_token
			WHERE token_id = ?
			""", LocalDateTime.class, tokenId));
	}

	@Test
	@DisplayName("만료된 무효화 기록만 정리하고 미만료 기록은 유지한다")
	void deleteOnlyExpiredRevocations() {
		String expiredId = UUID.randomUUID().toString();
		String activeId = UUID.randomUUID().toString();
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

		revokedTokenMapper.insertRevokedToken(expiredId, now.minusDays(1));
		revokedTokenMapper.insertRevokedToken(activeId, now.plusDays(1));

		revokedTokenMapper.deleteExpiredTokens();

		assertFalse(revokedTokenMapper.existsRevokedToken(expiredId));
		assertTrue(revokedTokenMapper.existsRevokedToken(activeId));
	}
}