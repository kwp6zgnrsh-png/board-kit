package io.github.kwp6zgnrsh.boardkitapi.member.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberSanctionCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberSanctionRevokeRequest;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberSanctionStatusFilter;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberSearchField;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.AdminMemberPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.AdminMemberSanctionDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.AdminMemberSanctionSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.AdminMemberSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.InvalidMemberRequestException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberSanctionMapper;
import io.github.kwp6zgnrsh.boardkitapi.member.model.Member;
import io.github.kwp6zgnrsh.boardkitapi.member.model.MemberSanction;

@ExtendWith(MockitoExtension.class)
class AdminMemberSanctionServiceTest {

	@Mock
	private MemberMapper memberMapper;
	@Mock
	private MemberSanctionMapper sanctionMapper;

	private AdminMemberSanctionService service;

	@BeforeEach
	void setUp() {
		service = new AdminMemberSanctionService(memberMapper, sanctionMapper);
	}

	@ParameterizedTest(name = "permanent={0}")
	@ValueSource(booleans = {false, true})
	@DisplayName("회원 잠금과 중복 확인 후 UTC 시각으로 기간 또는 영구 제재를 등록한다")
	void createSanction(boolean permanent) {
		allowAdmin();
		prepareTarget("MEMBER");

		OffsetDateTime endsAt = permanent ? null :
			OffsetDateTime.now(ZoneOffset.UTC).plusDays(7).withOffsetSameInstant(ZoneOffset.ofHours(9));

		when(sanctionMapper.insertSanction(any())).thenAnswer(invocation -> {
			MemberSanction sanction = invocation.getArgument(0);
			ReflectionTestUtils.setField(sanction, "id", 300L);
			return 1;
		});

		LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);

		assertEquals(300L, service.createSanction(createRequest(endsAt), 10L, true));

		LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);
		ArgumentCaptor<MemberSanction> captor = ArgumentCaptor.forClass(MemberSanction.class);
		InOrder order = inOrder(memberMapper, sanctionMapper);

		order.verify(memberMapper).selectActiveMemberByIdForUpdate(20L);
		order.verify(memberMapper).existsActiveSanctionByMemberId(20L);
		order.verify(sanctionMapper).insertSanction(captor.capture());

		MemberSanction saved = captor.getValue();
		LocalDateTime expectedEnd = endsAt == null ? null :
			endsAt.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime().truncatedTo(ChronoUnit.MICROS);

		assertAll(() -> assertEquals(20L, saved.getMemberId()), () -> assertEquals(10L, saved.getAdminId()),
			() -> assertEquals("운영 규칙 위반", saved.getReason()), () -> assertEquals(expectedEnd, saved.getEndsAt()),
			() -> assertFalse(saved.getStartsAt().isBefore(before)),
			() -> assertFalse(saved.getStartsAt().isAfter(after)));
	}

	@Test
	@DisplayName("관리자가 아니면 회원 검색과 제재 조회·등록·해제를 거절한다")
	void rejectOperationsByNonAdmin() {
		var creation = assertThrows(ResponseStatusException.class,
			() -> service.createSanction(createRequest(null), 10L, false));
		var revocation = assertThrows(ResponseStatusException.class,
			() -> service.revokeSanction(100L, revokeRequest(), 10L, false));

		assertEquals(HttpStatus.FORBIDDEN, creation.getStatusCode());
		assertEquals(HttpStatus.FORBIDDEN, revocation.getStatusCode());
		var search = assertThrows(ResponseStatusException.class,
			() -> service.getMembers(MemberSearchField.LOGIN_ID, "member", 1, 10, 10L, false));

		assertEquals(HttpStatus.FORBIDDEN, search.getStatusCode());

		var list = assertThrows(ResponseStatusException.class,
			() -> service.getSanctions(MemberSanctionStatusFilter.ACTIVE, null, 1, 10, 10L, false));
		var detail = assertThrows(ResponseStatusException.class, () -> service.getSanction(100L, 10L, false));

		assertEquals(HttpStatus.FORBIDDEN, list.getStatusCode());
		assertEquals(HttpStatus.FORBIDDEN, detail.getStatusCode());
		verifyNoInteractions(memberMapper, sanctionMapper);
	}

	@Test
	@DisplayName("탈퇴한 관리자이면 회원 검색과 제재 조회·등록·해제를 거절한다")
	void rejectOperationsForUnavailableAdmin() {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(false);

		assertThrows(MemberUnavailableException.class, () -> service.createSanction(createRequest(null), 10L, true));
		assertThrows(MemberUnavailableException.class, () -> service.revokeSanction(100L, revokeRequest(), 10L, true));
		assertThrows(MemberUnavailableException.class,
			() -> service.getMembers(MemberSearchField.LOGIN_ID, "member", 1, 10, 10L, true));
		assertThrows(MemberUnavailableException.class,
			() -> service.getSanctions(MemberSanctionStatusFilter.ACTIVE, null, 1, 10, 10L, true));
		assertThrows(MemberUnavailableException.class, () -> service.getSanction(100L, 10L, true));
		verifyNoInteractions(sanctionMapper);
		verify(memberMapper, never()).selectActiveMemberByIdForUpdate(anyLong());
	}

	@Test
	@DisplayName("없거나 탈퇴한 회원에게는 제재를 등록할 수 없다")
	void rejectCreationForUnavailableTarget() {
		allowAdmin();
		when(memberMapper.selectActiveMemberByIdForUpdate(20L)).thenReturn(Optional.empty());

		var exception = assertThrows(ResponseStatusException.class,
			() -> service.createSanction(createRequest(null), 10L, true));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
		verifyNoInteractions(sanctionMapper);
	}

	@Test
	@DisplayName("관리자 계정은 제재 대상으로 지정할 수 없다")
	void rejectCreationForAdminTarget() {
		allowAdmin();
		prepareTarget("ADMIN");

		assertThrows(InvalidMemberRequestException.class, () -> service.createSanction(createRequest(null), 10L, true));

		verifyNoInteractions(sanctionMapper);
	}

	@Test
	@DisplayName("진행 중인 제재가 있으면 중복 등록을 거절한다")
	void rejectDuplicateActiveSanction() {
		allowAdmin();
		prepareTarget("MEMBER");
		when(memberMapper.existsActiveSanctionByMemberId(20L)).thenReturn(true);

		var exception = assertThrows(ResponseStatusException.class,
			() -> service.createSanction(createRequest(null), 10L, true));

		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
		verifyNoInteractions(sanctionMapper);
	}

	@Test
	@DisplayName("종료 일시가 지났으면 제재를 저장하지 않는다")
	void rejectCreationWithPastEndTime() {
		allowAdmin();
		prepareTarget("MEMBER");
		OffsetDateTime endsAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);

		assertThrows(InvalidMemberRequestException.class,
			() -> service.createSanction(createRequest(endsAt), 10L, true));

		verifyNoInteractions(sanctionMapper);
	}

	@ParameterizedTest
	@CsvSource({"0, true", "1, false"})
	@DisplayName("등록 건수 또는 생성 ID가 비정상이면 저장 실패로 처리한다")
	void rejectUnexpectedInsertResult(int insertedCount, boolean generatedId) {
		allowAdmin();
		prepareTarget("MEMBER");

		when(sanctionMapper.insertSanction(any())).thenAnswer(invocation -> {
			if (generatedId) {
				MemberSanction sanction = invocation.getArgument(0);
				ReflectionTestUtils.setField(sanction, "id", 300L);
			}
			return insertedCount;
		});

		assertThrows(IllegalStateException.class, () -> service.createSanction(createRequest(null), 10L, true));
	}

	@Test
	@DisplayName("진행 중인 제재는 회원 잠금 후 현재 관리자와 사유로 해제한다")
	void revokeActiveSanction() {
		prepareRevocation(sanction(null, null));
		when(sanctionMapper.revokeSanction(100L, 10L, "검토 후 해제")).thenReturn(1);

		service.revokeSanction(100L, revokeRequest(), 10L, true);

		InOrder order = inOrder(memberMapper, sanctionMapper);
		order.verify(sanctionMapper).selectSanctionById(100L);
		order.verify(memberMapper).selectActiveMemberByIdForUpdate(20L);
		order.verify(sanctionMapper).revokeSanction(100L, 10L, "검토 후 해제");
	}

	@Test
	@DisplayName("없는 제재 이력은 해제할 수 없다")
	void rejectRevocationForMissingSanction() {
		allowAdmin();
		when(sanctionMapper.selectSanctionById(100L)).thenReturn(Optional.empty());

		var exception = assertThrows(ResponseStatusException.class,
			() -> service.revokeSanction(100L, revokeRequest(), 10L, true));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
		verify(memberMapper, never()).selectActiveMemberByIdForUpdate(anyLong());
	}

	@Test
	@DisplayName("회원이 탈퇴했으면 기존 제재도 해제할 수 없다")
	void rejectRevocationForDeletedMember() {
		allowAdmin();
		when(sanctionMapper.selectSanctionById(100L)).thenReturn(Optional.of(sanction(null, null)));
		when(memberMapper.selectActiveMemberByIdForUpdate(20L)).thenReturn(Optional.empty());

		var exception = assertThrows(ResponseStatusException.class,
			() -> service.revokeSanction(100L, revokeRequest(), 10L, true));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
		verify(sanctionMapper, never()).revokeSanction(anyLong(), anyLong(), anyString());
	}

	@ParameterizedTest(name = "alreadyRevoked={0}")
	@ValueSource(booleans = {false, true})
	@DisplayName("만료되었거나 이미 해제된 제재는 다시 해제할 수 없다")
	void rejectRevocationForInactiveSanction(boolean alreadyRevoked) {
		LocalDateTime past = LocalDateTime.now(ZoneOffset.UTC).minusDays(1);
		prepareRevocation(sanction(alreadyRevoked ? null : past, alreadyRevoked ? past : null));

		var exception = assertThrows(ResponseStatusException.class,
			() -> service.revokeSanction(100L, revokeRequest(), 10L, true));

		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
		verify(sanctionMapper, never()).revokeSanction(anyLong(), anyLong(), anyString());
	}

	@Test
	@DisplayName("상태 검사 후 해제 조건이 달라졌으면 충돌로 처리한다")
	void rejectRevocationWhenConditionalUpdateFails() {
		prepareRevocation(sanction(null, null));
		when(sanctionMapper.revokeSanction(100L, 10L, "검토 후 해제")).thenReturn(0);

		var exception = assertThrows(ResponseStatusException.class,
			() -> service.revokeSanction(100L, revokeRequest(), 10L, true));

		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
	}

	@ParameterizedTest
	@EnumSource(MemberSearchField.class)
	@DisplayName("선택한 항목과 이스케이프한 검색어로 회원 페이지를 조회한다")
	void searchMembersWithEscapedKeyword(MemberSearchField field) {
		allowAdmin();

		String escapedKeyword = "ab=%=_c==";
		List<AdminMemberSummaryResponse> members = List.of(
			new AdminMemberSummaryResponse(20L, "member20", "회원20", true));

		when(memberMapper.countAdminMembers(field, escapedKeyword)).thenReturn(21L);
		when(memberMapper.selectAdminMembers(field, escapedKeyword, 10, 20L)).thenReturn(members);

		AdminMemberPageResponse response = service.getMembers(field, "  ab%_c=  ", 3, 10, 10L, true);

		assertAll(() -> assertEquals(members, response.members()), () -> assertEquals(3, response.page()),
			() -> assertEquals(10, response.size()), () -> assertEquals(21L, response.totalElements()),
			() -> assertEquals(3, response.totalPages()));

		verify(memberMapper).countAdminMembers(field, escapedKeyword);
		verify(memberMapper).selectAdminMembers(field, escapedKeyword, 10, 20L);
	}

	@Test
	@DisplayName("마지막 페이지를 넘으면 회원 목록 조회 없이 빈 목록을 반환한다")
	void returnEmptyMembersForPageBeyondLastPage() {
		allowAdmin();
		when(memberMapper.countAdminMembers(MemberSearchField.LOGIN_ID, "member")).thenReturn(21L);

		AdminMemberPageResponse response = service.getMembers(MemberSearchField.LOGIN_ID, "member", 4, 10, 10L, true);

		assertAll(() -> assertTrue(response.members().isEmpty()), () -> assertEquals(4, response.page()),
			() -> assertEquals(21L, response.totalElements()), () -> assertEquals(3, response.totalPages()));

		verify(memberMapper, never()).selectAdminMembers(any(), anyString(), anyInt(), anyLong());
	}

	@ParameterizedTest(name = "memberScoped={0}")
	@ValueSource(booleans = {false, true})
	@DisplayName("전체 또는 회원별 제재 목록을 동일한 기준 시각으로 조회한다")
	void getSanctionsWithSharedReferenceTime(boolean memberScoped) {
		allowAdmin();

		Long targetMemberId = memberScoped ? 20L : null;
		LocalDateTime createdAt = LocalDateTime.of(2026, 10, 1, 0, 0);
		var row = new AdminMemberSanctionSummaryResponse(100L, 20L, "member20", "회원20", true, "운영 규칙 위반", "ACTIVE",
			createdAt, null, null, createdAt);
		List<AdminMemberSanctionSummaryResponse> rows = List.of(row);

		when(sanctionMapper.countAdminSanctions(eq(MemberSanctionStatusFilter.ACTIVE), eq(targetMemberId),
			any(LocalDateTime.class))).thenReturn(21L);

		when(sanctionMapper.selectAdminSanctions(eq(MemberSanctionStatusFilter.ACTIVE), eq(targetMemberId),
			any(LocalDateTime.class), eq(10), eq(20L))).thenReturn(rows);

		var response = service.getSanctions(MemberSanctionStatusFilter.ACTIVE, targetMemberId, 3, 10, 10L, true);

		assertAll(() -> assertEquals(rows, response.sanctions()), () -> assertEquals(3, response.page()),
			() -> assertEquals(10, response.size()), () -> assertEquals(21L, response.totalElements()),
			() -> assertEquals(3, response.totalPages()));

		ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);

		verify(sanctionMapper).countAdminSanctions(eq(MemberSanctionStatusFilter.ACTIVE), eq(targetMemberId),
			captor.capture());
		verify(sanctionMapper).selectAdminSanctions(MemberSanctionStatusFilter.ACTIVE, targetMemberId,
			captor.getValue(), 10, 20L);

		verify(memberMapper, never()).selectActiveMemberByIdForUpdate(anyLong());
	}

	@ParameterizedTest
	@CsvSource({"0, 1, 0", "21, 4, 3"})
	@DisplayName("제재 이력이 없거나 마지막 페이지를 넘으면 빈 목록을 반환한다")
	void returnEmptySanctionsWithoutListQuery(long totalElements, int page, int totalPages) {
		allowAdmin();

		when(sanctionMapper.countAdminSanctions(eq(MemberSanctionStatusFilter.ALL), eq(20L),
			any(LocalDateTime.class))).thenReturn(totalElements);

		var response = service.getSanctions(MemberSanctionStatusFilter.ALL, 20L, page, 10, 10L, true);

		assertAll(() -> assertTrue(response.sanctions().isEmpty()), () -> assertEquals(page, response.page()),
			() -> assertEquals(10, response.size()), () -> assertEquals(totalElements, response.totalElements()),
			() -> assertEquals(totalPages, response.totalPages()));

		verify(sanctionMapper, never()).selectAdminSanctions(any(), any(), any(LocalDateTime.class), anyInt(),
			anyLong());
	}

	@ParameterizedTest(name = "exists={0}")
	@ValueSource(booleans = {false, true})
	@DisplayName("탈퇴 회원의 제재 상세도 반환하고 없는 이력은 404로 거절한다")
	void getSanctionDetailOrRejectMissingHistory(boolean exists) {
		allowAdmin();

		LocalDateTime createdAt = LocalDateTime.of(2026, 10, 1, 0, 0);
		var detail = new AdminMemberSanctionDetailResponse(100L, 20L, "member20", "회원20", true, 10L, "관리자", "운영 규칙 위반",
			"EXPIRED", createdAt, createdAt.plusDays(7), null, null, null, null, createdAt);

		when(sanctionMapper.selectAdminSanction(eq(100L), any(LocalDateTime.class))).thenReturn(
			exists ? Optional.of(detail) : Optional.empty());

		if (exists) {
			assertSame(detail, service.getSanction(100L, 10L, true));
		} else {
			var exception = assertThrows(ResponseStatusException.class, () -> service.getSanction(100L, 10L, true));

			assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
		}

		verify(memberMapper, never()).selectActiveMemberByIdForUpdate(anyLong());
	}

	private void allowAdmin() {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);
	}

	private void prepareTarget(String role) {
		when(memberMapper.selectActiveMemberByIdForUpdate(20L)).thenReturn(
			Optional.of(Member.builder().id(20L).role(role).build()));
	}

	private void prepareRevocation(MemberSanction sanction) {
		allowAdmin();
		prepareTarget("MEMBER");
		when(sanctionMapper.selectSanctionById(100L)).thenReturn(Optional.of(sanction));
	}

	private MemberSanctionCreateRequest createRequest(OffsetDateTime endsAt) {
		return new MemberSanctionCreateRequest(20L, "  운영 규칙 위반  ", endsAt);
	}

	private MemberSanctionRevokeRequest revokeRequest() {
		return new MemberSanctionRevokeRequest("  검토 후 해제  ");
	}

	private MemberSanction sanction(LocalDateTime endsAt, LocalDateTime revokedAt) {
		return MemberSanction.builder()
			.id(100L)
			.memberId(20L)
			.adminId(30L)
			.startsAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(2))
			.endsAt(endsAt)
			.revokedAt(revokedAt)
			.build();
	}
}
