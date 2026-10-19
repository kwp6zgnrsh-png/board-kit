package io.github.kwp6zgnrsh.boardkitapi.member.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberSanctionStatusFilter;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberSearchField;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.AdminMemberSanctionSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.AdminMemberSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.model.Member;
import io.github.kwp6zgnrsh.boardkitapi.member.model.MemberSanction;

@MybatisTest(properties = "mybatis.mapper-locations=classpath:mapper/member/*.xml")
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = MemberSanctionMapperIntegrationTest.Config.class)
class MemberSanctionMapperIntegrationTest {

	@Autowired
	private MemberSanctionMapper sanctionMapper;
	@Autowired
	private MemberMapper memberMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Long memberId;
	private Long adminId;
	private LocalDateTime now;

	@Configuration(proxyBeanMethods = false)
	@MapperScan(basePackageClasses = MemberMapper.class)
	static class Config {
	}

	@BeforeEach
	void prepareFixtures() {
		assertEquals("boardkit_test", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));

		jdbcTemplate.execute("SET SESSION time_zone = '+00:00'");
		now = jdbcTemplate.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class);
		assertNotNull(now);

		memberId = insertMember("MEMBER");
		adminId = insertMember("ADMIN");
	}

	@ParameterizedTest(name = "permanent={0}")
	@ValueSource(booleans = {false, true})
	@DisplayName("기간 제재와 영구 제재를 등록하고 저장 정보를 조회한다")
	void insertAndReadSanction(boolean permanent) {
		Member member = memberMapper.selectActiveMemberByIdForUpdate(memberId).orElseThrow();

		assertEquals(memberId, member.getId());
		assertEquals("MEMBER", member.getRole());
		assertFalse(memberMapper.existsActiveSanctionByMemberId(memberId));

		LocalDateTime endsAt = permanent ? null : now.plusDays(7);
		MemberSanction sanction = newSanction(now, endsAt);

		assertEquals(1, sanctionMapper.insertSanction(sanction));
		assertNotNull(sanction.getId());

		MemberSanction saved = sanctionMapper.selectSanctionById(sanction.getId()).orElseThrow();

		assertAll(() -> assertEquals(sanction.getId(), saved.getId()),
			() -> assertEquals(memberId, saved.getMemberId()), () -> assertEquals(adminId, saved.getAdminId()),
			() -> assertEquals("운영 규칙 위반", saved.getReason()), () -> assertEquals(now, saved.getStartsAt()),
			() -> assertEquals(endsAt, saved.getEndsAt()), () -> assertNull(saved.getRevokedAt()),
			() -> assertNull(saved.getRevokedBy()), () -> assertNull(saved.getRevokeReason()),
			() -> assertNotNull(saved.getCreatedAt()),
			() -> assertTrue(memberMapper.existsActiveSanctionByMemberId(memberId)));
	}

	@Test
	@DisplayName("만료된 제재는 현재 제재에서 제외하고 기존 이력은 유지한다")
	void ignoreExpiredSanctionWithoutChangingHistory() {
		LocalDateTime endsAt = now.minusDays(1);
		MemberSanction sanction = newSanction(now.minusDays(2), endsAt);

		assertEquals(1, sanctionMapper.insertSanction(sanction));
		assertEquals(0, sanctionMapper.revokeSanction(sanction.getId(), adminId, "만료 후 해제"));
		assertFalse(memberMapper.existsActiveSanctionByMemberId(memberId));

		MemberSanction saved = sanctionMapper.selectSanctionById(sanction.getId()).orElseThrow();

		assertAll(() -> assertEquals(endsAt, saved.getEndsAt()), () -> assertNull(saved.getRevokedAt()),
			() -> assertNull(saved.getRevokedBy()), () -> assertNull(saved.getRevokeReason()));
	}

	@ParameterizedTest
	@ValueSource(strings = {"+09:00", "-09:00"})
	@DisplayName("DB 세션 시간대와 무관하게 기간제 제재와 등록 시각을 UTC로 처리한다")
	void preserveUtcSanctionTimesAcrossSessionTimeZones(String timeZone) {
		jdbcTemplate.execute("SET SESSION time_zone = '" + timeZone + "'");
		try {
			LocalDateTime before = jdbcTemplate.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class);
			assertNotNull(before);
			MemberSanction sanction = newSanction(before, before.plusHours(1));
			assertEquals(1, sanctionMapper.insertSanction(sanction));

			MemberSanction saved = sanctionMapper.selectSanctionById(sanction.getId()).orElseThrow();
			LocalDateTime after = jdbcTemplate.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class);
			assertNotNull(after);
			assertAll(() -> assertTrue(memberMapper.existsActiveSanctionByMemberId(memberId)),
				() -> assertFalse(saved.getCreatedAt().isBefore(before)),
				() -> assertFalse(saved.getCreatedAt().isAfter(after)));
		} finally {
			jdbcTemplate.execute("SET SESSION time_zone = '+00:00'");
		}
	}

	@Test
	@DisplayName("다른 관리자가 제재를 해제하고 반복 요청에도 최초 해제 이력을 유지한다")
	void preserveRevocationAuditOnRepeatedRequest() {
		Long revokerId = insertMember("ADMIN");
		LocalDateTime endsAt = now.plusDays(7);
		MemberSanction sanction = newSanction(now, endsAt);

		assertEquals(1, sanctionMapper.insertSanction(sanction));
		assertTrue(memberMapper.existsActiveSanctionByMemberId(memberId));

		assertEquals(1, sanctionMapper.revokeSanction(sanction.getId(), revokerId, "검토 후 해제"));

		MemberSanction revoked = sanctionMapper.selectSanctionById(sanction.getId()).orElseThrow();

		LocalDateTime afterRevoke = jdbcTemplate.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class);
		assertNotNull(afterRevoke);
		assertNotNull(revoked.getRevokedAt());

		assertAll(() -> assertFalse(memberMapper.existsActiveSanctionByMemberId(memberId)),
			() -> assertEquals(adminId, revoked.getAdminId()), () -> assertEquals("운영 규칙 위반", revoked.getReason()),
			() -> assertEquals(endsAt, revoked.getEndsAt()), () -> assertEquals(revokerId, revoked.getRevokedBy()),
			() -> assertEquals("검토 후 해제", revoked.getRevokeReason()),
			() -> assertFalse(revoked.getRevokedAt().isBefore(now)),
			() -> assertFalse(revoked.getRevokedAt().isAfter(afterRevoke)));

		assertEquals(0, sanctionMapper.revokeSanction(sanction.getId(), adminId, "변경된 해제 사유"));

		MemberSanction unchanged = sanctionMapper.selectSanctionById(sanction.getId()).orElseThrow();

		assertAll(() -> assertEquals(revoked.getRevokedAt(), unchanged.getRevokedAt()),
			() -> assertEquals(revokerId, unchanged.getRevokedBy()),
			() -> assertEquals("검토 후 해제", unchanged.getRevokeReason()));
	}

	@ParameterizedTest
	@EnumSource(MemberSearchField.class)
	@DisplayName("선택한 검색 항목만 사용하고 관리자와 탈퇴 회원은 제외한다")
	void searchMembersBySelectedField(MemberSearchField field) {
		String key = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		Long deletedMemberId = insertMember("MEMBER");

		jdbcTemplate.update("""
			UPDATE member
			SET login_id = ?, display_name = ?
			WHERE id = ?
			""", "id-" + key, "닉" + key, memberId);

		jdbcTemplate.update("""
			UPDATE member
			SET login_id = ?, display_name = ?
			WHERE id = ?
			""", "id-" + key + "a", "닉" + key + "a", adminId);

		jdbcTemplate.update("""
			UPDATE member
			SET login_id = ?, display_name = ?, deleted_at = UTC_TIMESTAMP(6)
			WHERE id = ?
			""", "id-" + key + "d", "닉" + key + "d", deletedMemberId);

		String keyword = field == MemberSearchField.LOGIN_ID ? "id-" + key : "닉" + key;
		String otherFieldKeyword = field == MemberSearchField.LOGIN_ID ? "닉" + key : "id-" + key;

		assertEquals(1L, memberMapper.countAdminMembers(field, keyword));

		List<AdminMemberSummaryResponse> members = memberMapper.selectAdminMembers(field, keyword, 10, 0L);

		assertEquals(1, members.size());

		AdminMemberSummaryResponse found = members.getFirst();
		assertAll(() -> assertEquals(memberId, found.memberId()), () -> assertEquals("id-" + key, found.loginId()),
			() -> assertEquals("닉" + key, found.displayName()), () -> assertFalse(found.sanctioned()));

		assertEquals(0L, memberMapper.countAdminMembers(field, otherFieldKeyword));
		assertTrue(memberMapper.selectAdminMembers(field, otherFieldKeyword, 10, 0L).isEmpty());
	}

	@Test
	@DisplayName("제재 이력이 여러 건이어도 회원은 한 번만 표시하고 현재 제재 여부를 반환한다")
	void searchSanctionedMemberWithoutDuplicatingHistory() {
		String loginId = memberMapper.selectActiveMemberById(memberId).orElseThrow().getLoginId();

		assertEquals(1, sanctionMapper.insertSanction(newSanction(now.minusDays(3), now.minusDays(2))));
		assertEquals(1, sanctionMapper.insertSanction(newSanction(now.minusDays(1), null)));

		assertEquals(1L, memberMapper.countAdminMembers(MemberSearchField.LOGIN_ID, loginId));

		List<AdminMemberSummaryResponse> members = memberMapper.selectAdminMembers(MemberSearchField.LOGIN_ID, loginId,
			1, 0L);

		assertEquals(1, members.size());
		assertEquals(memberId, members.getFirst().memberId());
		assertTrue(members.getFirst().sanctioned());

		assertTrue(memberMapper.selectAdminMembers(MemberSearchField.LOGIN_ID, loginId, 1, 1L).isEmpty());
	}

	@ParameterizedTest
	@EnumSource(MemberSanctionStatusFilter.class)
	@DisplayName("회원별 제재 이력을 상태로 필터링하고 같은 기준 시각으로 판정한다")
	void filterSanctionHistory(MemberSanctionStatusFilter filter) {
		long originalTotal = sanctionMapper.countAdminSanctions(MemberSanctionStatusFilter.ALL, null, now);

		MemberSanction permanent = newSanction(now.minusDays(1), null);
		MemberSanction finite = newSanction(now.minusDays(1), now.plusDays(1));
		MemberSanction expired = newSanction(now.minusDays(2), now);
		MemberSanction revoked = newSanction(now.minusDays(3), now.minusDays(1));

		for (MemberSanction sanction : List.of(permanent, finite, expired, revoked)) {
			assertEquals(1, sanctionMapper.insertSanction(sanction));
		}

		jdbcTemplate.update("""
			UPDATE member_sanction
			SET revoked_at = ?, revoked_by = ?, revoke_reason = ?
			WHERE id = ?
			""", now.minusDays(2), adminId, "검토 후 해제", revoked.getId());

		// 동일한 등록 시각일 때 ID 순서로 정렬되는지도 확인한다.
		jdbcTemplate.update("""
			UPDATE member_sanction
			SET created_at = ?
			WHERE member_id = ?
			""", now, memberId);

		Long otherMemberId = insertMember("MEMBER");
		MemberSanction other = MemberSanction.builder()
			.memberId(otherMemberId)
			.adminId(adminId)
			.reason("다른 회원의 제재")
			.startsAt(now.minusDays(1))
			.build();
		assertEquals(1, sanctionMapper.insertSanction(other));

		Map<MemberSanctionStatusFilter, List<Long>> expectedIds = Map.of(MemberSanctionStatusFilter.ALL,
			List.of(revoked.getId(), expired.getId(), finite.getId(), permanent.getId()),
			MemberSanctionStatusFilter.ACTIVE, List.of(finite.getId(), permanent.getId()),
			MemberSanctionStatusFilter.EXPIRED, List.of(expired.getId()), MemberSanctionStatusFilter.REVOKED,
			List.of(revoked.getId()));

		Map<Long, String> expectedStatuses = Map.of(permanent.getId(), "ACTIVE", finite.getId(), "ACTIVE",
			expired.getId(), "EXPIRED", revoked.getId(), "REVOKED");

		List<Long> expected = expectedIds.get(filter);
		List<AdminMemberSanctionSummaryResponse> rows = sanctionMapper.selectAdminSanctions(filter, memberId, now, 10,
			0L);

		assertEquals((long)expected.size(), sanctionMapper.countAdminSanctions(filter, memberId, now));
		assertEquals(expected, rows.stream().map(AdminMemberSanctionSummaryResponse::sanctionId).toList());

		for (AdminMemberSanctionSummaryResponse row : rows) {
			assertEquals(memberId, row.memberId());
			assertEquals(expectedStatuses.get(row.sanctionId()), row.status());
			assertFalse(row.memberDeleted());
		}

		if (filter == MemberSanctionStatusFilter.ALL) {
			assertEquals(originalTotal + 5,
				sanctionMapper.countAdminSanctions(MemberSanctionStatusFilter.ALL, null, now));

			List<AdminMemberSanctionSummaryResponse> page = sanctionMapper.selectAdminSanctions(filter, memberId, now,
				2, 1L);

			assertEquals(List.of(expired.getId(), finite.getId()),
				page.stream().map(AdminMemberSanctionSummaryResponse::sanctionId).toList());
		}
	}

	@ParameterizedTest(name = "completeAudit={0}")
	@ValueSource(booleans = {false, true})
	@DisplayName("탈퇴 회원의 제재 상세를 유지하고 해제자 정보가 없는 과거 이력도 조회한다")
	void readDeletedMemberSanctionWithOptionalRevocationAudit(boolean completeAudit) {
		Member target = memberMapper.selectActiveMemberById(memberId).orElseThrow();
		Member administrator = memberMapper.selectActiveMemberById(adminId).orElseThrow();

		Long revokerId = completeAudit ? insertMember("ADMIN") : null;
		String revokerName =
			completeAudit ? memberMapper.selectActiveMemberById(revokerId).orElseThrow().getDisplayName() : null;
		String revokeReason = completeAudit ? "검토 후 해제" : null;

		MemberSanction sanction = newSanction(now.minusDays(2), null);
		assertEquals(1, sanctionMapper.insertSanction(sanction));

		jdbcTemplate.update("""
			UPDATE member_sanction
			SET revoked_at = ?, revoked_by = ?, revoke_reason = ?
			WHERE id = ?
			""", now.minusDays(1), revokerId, revokeReason, sanction.getId());

		jdbcTemplate.update("""
			UPDATE member
			SET deleted_at = ?
			WHERE id IN (?, ?)
			""", now, memberId, adminId);

		var detail = sanctionMapper.selectAdminSanction(sanction.getId(), now).orElseThrow();

		assertAll(() -> assertEquals(sanction.getId(), detail.sanctionId()),
			() -> assertEquals(memberId, detail.memberId()), () -> assertEquals(target.getLoginId(), detail.loginId()),
			() -> assertEquals(target.getDisplayName(), detail.displayName()), () -> assertTrue(detail.memberDeleted()),
			() -> assertEquals(adminId, detail.adminId()),
			() -> assertEquals(administrator.getDisplayName(), detail.adminDisplayName()),
			() -> assertEquals("운영 규칙 위반", detail.reason()), () -> assertEquals("REVOKED", detail.status()),
			() -> assertEquals(now.minusDays(2), detail.startsAt()), () -> assertNull(detail.endsAt()),
			() -> assertEquals(now.minusDays(1), detail.revokedAt()), () -> assertEquals(revokerId, detail.revokedBy()),
			() -> assertEquals(revokerName, detail.revokerDisplayName()),
			() -> assertEquals(revokeReason, detail.revokeReason()), () -> assertNotNull(detail.createdAt()));

		var rows = sanctionMapper.selectAdminSanctions(MemberSanctionStatusFilter.REVOKED, memberId, now, 10, 0L);

		assertEquals(1, rows.size());
		assertTrue(rows.getFirst().memberDeleted());
		assertTrue(sanctionMapper.selectAdminSanction(Long.MAX_VALUE, now).isEmpty());
	}

	private MemberSanction newSanction(LocalDateTime startsAt, LocalDateTime endsAt) {
		return MemberSanction.builder()
			.memberId(memberId)
			.adminId(adminId)
			.reason("운영 규칙 위반")
			.startsAt(startsAt)
			.endsAt(endsAt)
			.build();
	}

	private Long insertMember(String role) {
		String key = UUID.randomUUID().toString().replace("-", "");

		jdbcTemplate.update("""
			INSERT INTO member (login_id, password_hash, display_name, role)
			VALUES (?, ?, ?, ?)
			""", "sanction-" + key, "unused-test-hash", "제재" + key.substring(0, 8), role);

		Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		assertNotNull(id);
		return id;
	}
}
