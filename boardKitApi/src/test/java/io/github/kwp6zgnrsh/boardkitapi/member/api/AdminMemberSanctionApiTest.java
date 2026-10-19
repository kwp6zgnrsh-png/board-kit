package io.github.kwp6zgnrsh.boardkitapi.member.api;

import static io.github.kwp6zgnrsh.boardkitapi.support.TestAuthentication.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.auth.mapper.RevokedTokenMapper;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtTokenProvider;
import io.github.kwp6zgnrsh.boardkitapi.config.MethodSecurityConfig;
import io.github.kwp6zgnrsh.boardkitapi.config.SecurityConfig;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberSanctionCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberSanctionRevokeRequest;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberSanctionStatusFilter;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberSearchField;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.AdminMemberPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.AdminMemberSanctionDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.AdminMemberSanctionPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.AdminMemberSanctionSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.AdminMemberSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.InvalidMemberRequestException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.member.service.AdminMemberSanctionService;

@WebMvcTest({AdminMemberSanctionApi.class, AdminMemberApi.class})
@Import({SecurityConfig.class, MethodSecurityConfig.class})
class AdminMemberSanctionApiTest {

	private static final String URL = "/api/admin/member-sanctions";
	private static final String REVOKE_URL = URL + "/100/revoke";
	private static final String CREATE_BODY = "{\"memberId\":20,\"reason\":\"규칙 위반\",\"endsAt\":null}";
	private static final String REVOKE_BODY = "{\"revokeReason\":\"검토 후 해제\"}";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AdminMemberSanctionService sanctionService;
	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;
	@MockitoBean
	private RevokedTokenMapper revokedTokenMapper;
	@MockitoBean
	private MemberMapper memberMapper;

	@ParameterizedTest(name = "permanent={0}")
	@ValueSource(booleans = {false, true})
	@DisplayName("관리자는 기간 또는 영구 제재를 등록하고 생성 ID를 받는다")
	void createSanctionWithAdminAuthentication(boolean permanent) throws Exception {
		OffsetDateTime endsAt = permanent ? null :
			OffsetDateTime.now(ZoneOffset.UTC).plusDays(7).withOffsetSameInstant(ZoneOffset.ofHours(9));
		String reason = "가".repeat(2000);
		String endJson = endsAt == null ? "null" : "\"" + endsAt + "\"";
		String body = """
			{"memberId":20,"reason":"%s","endsAt":%s}
			""".formatted(reason, endJson);

		when(sanctionService.createSanction(any(MemberSanctionCreateRequest.class), eq(10L), eq(true))).thenReturn(
			300L);

		mockMvc.perform(post(URL).param("adminId", "99")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)
				.with(csrf())
				.with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.id").value(300));

		ArgumentCaptor<MemberSanctionCreateRequest> captor = ArgumentCaptor.forClass(MemberSanctionCreateRequest.class);

		verify(sanctionService).createSanction(captor.capture(), eq(10L), eq(true));

		MemberSanctionCreateRequest actual = captor.getValue();

		assertAll(() -> assertEquals(20L, actual.memberId()), () -> assertEquals(reason, actual.reason()),
			() -> assertEquals(endsAt == null ? null : endsAt.toInstant(),
				actual.endsAt() == null ? null : actual.endsAt().toInstant()));
	}

	@Test
	@DisplayName("관리자의 제재 해제 요청에 제재 ID와 로그인한 관리자 ID를 전달한다")
	void revokeSanctionWithAdminAuthentication() throws Exception {
		mockMvc.perform(patch(REVOKE_URL).param("adminId", "99")
				.contentType(MediaType.APPLICATION_JSON)
				.content(REVOKE_BODY)
				.with(csrf())
				.with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		verify(sanctionService).revokeSanction(100L, new MemberSanctionRevokeRequest("검토 후 해제"), 10L, true);
	}

	@ParameterizedTest
	@ValueSource(strings = {"POST", "PATCH"})
	@DisplayName("비로그인 사용자의 제재 등록과 해제를 거절한다")
	void rejectRequestsWithoutAuthentication(String method) throws Exception {
		mockMvc.perform(changeRequest(method).with(csrf())).andExpect(status().isUnauthorized());

		verifyNoInteractions(sanctionService);
	}

	@ParameterizedTest
	@ValueSource(strings = {"POST", "PATCH"})
	@DisplayName("일반 회원의 제재 등록과 해제를 거절한다")
	void rejectRequestsByMember(String method) throws Exception {
		mockMvc.perform(
				changeRequest(method).with(csrf()).with(authentication(createAuthentication(10L, "ROLE_MEMBER"))))
			.andExpect(status().isForbidden());

		verifyNoInteractions(sanctionService);
	}

	@ParameterizedTest
	@ValueSource(strings = {"POST", "PATCH"})
	@DisplayName("관리자라도 CSRF 토큰이 없으면 제재 변경을 거절한다")
	void rejectRequestsWithoutCsrfToken(String method) throws Exception {
		mockMvc.perform(changeRequest(method).with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isForbidden());

		verifyNoInteractions(sanctionService);
	}

	@ParameterizedTest
	@ValueSource(strings = {"POST", "PATCH"})
	@DisplayName("잘못된 CSRF 토큰으로 제재를 변경할 수 없다")
	void rejectRequestsWithInvalidCsrfToken(String method) throws Exception {
		mockMvc.perform(changeRequest(method).with(csrf().useInvalidToken())
			.with(authentication(createAuthentication(10L, "ROLE_ADMIN")))).andExpect(status().isForbidden());

		verifyNoInteractions(sanctionService);
	}

	@ParameterizedTest
	@MethodSource("invalidCreationBodies")
	@DisplayName("잘못된 제재 등록 입력은 서비스 호출 전에 거절한다")
	void rejectInvalidCreationRequest(String body) throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.with(csrf())
			.with(authentication(createAuthentication(10L, "ROLE_ADMIN")))).andExpect(status().isBadRequest());

		verifyNoInteractions(sanctionService);
	}

	@ParameterizedTest
	@MethodSource("invalidRevocationBodies")
	@DisplayName("공백 또는 길이를 초과한 해제 사유는 서비스 호출 전에 거절한다")
	void rejectInvalidRevocationRequest(String body) throws Exception {
		mockMvc.perform(patch(REVOKE_URL).contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.with(csrf())
			.with(authentication(createAuthentication(10L, "ROLE_ADMIN")))).andExpect(status().isBadRequest());

		verifyNoInteractions(sanctionService);
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "abc"})
	@DisplayName("올바르지 않은 제재 ID는 서비스 호출 전에 거절한다")
	void rejectInvalidSanctionId(String sanctionId) throws Exception {
		mockMvc.perform(patch(URL + "/" + sanctionId + "/revoke").contentType(MediaType.APPLICATION_JSON)
			.content(REVOKE_BODY)
			.with(csrf())
			.with(authentication(createAuthentication(10L, "ROLE_ADMIN")))).andExpect(status().isBadRequest());

		mockMvc.perform(get(URL + "/" + sanctionId).with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(sanctionService);
	}

	@ParameterizedTest
	@MethodSource("serviceFailures")
	@DisplayName("서비스의 업무 오류를 해당 HTTP 상태로 반환한다")
	void returnServiceFailureStatus(String method, RuntimeException failure, int expectedStatus) throws Exception {
		if ("POST".equals(method)) {
			when(sanctionService.createSanction(any(MemberSanctionCreateRequest.class), eq(10L), eq(true))).thenThrow(
				failure);
		} else {
			doThrow(failure).when(sanctionService)
				.revokeSanction(eq(100L), any(MemberSanctionRevokeRequest.class), eq(10L), eq(true));
		}

		mockMvc.perform(
				changeRequest(method).with(csrf()).with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().is(expectedStatus));
	}

	@ParameterizedTest
	@EnumSource(MemberSearchField.class)
	@DisplayName("관리자는 선택한 검색 항목으로 회원을 조회한다")
	void searchMembersWithAdminAuthentication(MemberSearchField field) throws Exception {
		var member = new AdminMemberSummaryResponse(20L, "member20", "회원20", true);

		when(sanctionService.getMembers(field, "회원", 1, 10, 10L, true)).thenReturn(
			new AdminMemberPageResponse(List.of(member), 1, 10, 1L, 1));

		mockMvc.perform(get("/api/admin/members").param("field", field.name())
				.param("keyword", "회원")
				.param("adminId", "99")
				.with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.members[0].memberId").value(20))
			.andExpect(jsonPath("$.members[0].loginId").value("member20"))
			.andExpect(jsonPath("$.members[0].displayName").value("회원20"))
			.andExpect(jsonPath("$.members[0].sanctioned").value(true))
			.andExpect(jsonPath("$.members[0].passwordHash").doesNotExist())
			.andExpect(jsonPath("$.members[0].authVersion").doesNotExist())
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.size").value(10))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.totalPages").value(1));

		verify(sanctionService).getMembers(field, "회원", 1, 10, 10L, true);
	}

	@Test
	@DisplayName("회원 검색에 요청한 페이지와 크기를 전달한다")
	void searchMembersWithRequestedPagination() throws Exception {
		when(sanctionService.getMembers(MemberSearchField.LOGIN_ID, "member", 2, 20, 10L, true)).thenReturn(
			new AdminMemberPageResponse(List.of(), 2, 20, 0L, 0));

		mockMvc.perform(get("/api/admin/members").param("field", "LOGIN_ID")
				.param("keyword", "member")
				.param("page", "2")
				.param("size", "20")
				.with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(2))
			.andExpect(jsonPath("$.size").value(20));

		verify(sanctionService).getMembers(MemberSearchField.LOGIN_ID, "member", 2, 20, 10L, true);
	}

	@Test
	@DisplayName("비로그인 사용자와 일반 회원은 회원 검색을 사용할 수 없다")
	void rejectMemberSearchWithoutAdminAccess() throws Exception {
		mockMvc.perform(get("/api/admin/members").param("field", "LOGIN_ID").param("keyword", "member"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/admin/members").param("field", "LOGIN_ID")
			.param("keyword", "member")
			.with(authentication(createAuthentication(10L, "ROLE_MEMBER")))).andExpect(status().isForbidden());

		verifyNoInteractions(sanctionService);
	}

	@ParameterizedTest
	@CsvSource({"field, UNKNOWN", "keyword, '   '", "page, 0", "page, abc", "size, 0", "size, 101"})
	@DisplayName("잘못된 회원 검색 조건은 서비스 호출 전에 거절한다")
	void rejectInvalidMemberSearchParameter(String parameter, String value) throws Exception {
		var request = get("/api/admin/members").param("field", "field".equals(parameter) ? value : "LOGIN_ID")
			.param("keyword", "keyword".equals(parameter) ? value : "member");

		if (!"field".equals(parameter) && !"keyword".equals(parameter)) {
			request.param(parameter, value);
		}
		mockMvc.perform(request.with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(sanctionService);
	}

	@ParameterizedTest
	@ValueSource(strings = {"field", "keyword"})
	@DisplayName("필수 회원 검색 조건이 누락되면 거절한다")
	void rejectMissingMemberSearchParameter(String missingParameter) throws Exception {
		var request = get("/api/admin/members");

		if (!"field".equals(missingParameter)) {
			request.param("field", "LOGIN_ID");
		}
		if (!"keyword".equals(missingParameter)) {
			request.param("keyword", "member");
		}

		mockMvc.perform(request.with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(sanctionService);
	}

	@Test
	@DisplayName("관리자는 기본 조건으로 진행 중인 전체 회원의 제재를 조회한다")
	void getSanctionsWithDefaultParameters() throws Exception {
		LocalDateTime createdAt = LocalDateTime.of(2026, 10, 1, 0, 0);
		var sanction = new AdminMemberSanctionSummaryResponse(100L, 20L, "member20", "회원20", false, "운영 규칙 위반",
			"ACTIVE", createdAt, null, null, createdAt);

		when(sanctionService.getSanctions(MemberSanctionStatusFilter.ACTIVE, null, 1, 10, 10L, true)).thenReturn(
			new AdminMemberSanctionPageResponse(List.of(sanction), 1, 10, 1L, 1));

		mockMvc.perform(get(URL).param("adminId", "99").with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.sanctions[0].sanctionId").value(100))
			.andExpect(jsonPath("$.sanctions[0].memberId").value(20))
			.andExpect(jsonPath("$.sanctions[0].displayName").value("회원20"))
			.andExpect(jsonPath("$.sanctions[0].memberDeleted").value(false))
			.andExpect(jsonPath("$.sanctions[0].status").value("ACTIVE"))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.size").value(10))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.totalPages").value(1));

		verify(sanctionService).getSanctions(MemberSanctionStatusFilter.ACTIVE, null, 1, 10, 10L, true);
	}

	@Test
	@DisplayName("선택한 회원과 상태 및 페이지 조건을 서비스에 전달한다")
	void getSanctionsWithRequestedParameters() throws Exception {
		when(sanctionService.getSanctions(MemberSanctionStatusFilter.REVOKED, 20L, 2, 20, 10L, true)).thenReturn(
			new AdminMemberSanctionPageResponse(List.of(), 2, 20, 0L, 0));

		mockMvc.perform(get(URL).param("filter", "REVOKED")
				.param("memberId", "20")
				.param("page", "2")
				.param("size", "20")
				.with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sanctions").isEmpty())
			.andExpect(jsonPath("$.page").value(2))
			.andExpect(jsonPath("$.size").value(20));

		verify(sanctionService).getSanctions(MemberSanctionStatusFilter.REVOKED, 20L, 2, 20, 10L, true);
	}

	@Test
	@DisplayName("관리자는 탈퇴 회원의 제재 상세와 등록 및 해제 관리자 정보를 조회한다")
	void getSanctionDetailWithAdminAuthentication() throws Exception {
		LocalDateTime createdAt = LocalDateTime.of(2026, 10, 1, 0, 0);
		var detail = new AdminMemberSanctionDetailResponse(100L, 20L, "member20", "회원20", true, 9L, "등록관리자", "운영 규칙 위반",
			"REVOKED", createdAt, null, createdAt.plusDays(1), 8L, "해제관리자", "검토 후 해제", createdAt);

		when(sanctionService.getSanction(100L, 10L, true)).thenReturn(detail);

		mockMvc.perform(get(URL + "/100").with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.sanctionId").value(100))
			.andExpect(jsonPath("$.memberId").value(20))
			.andExpect(jsonPath("$.memberDeleted").value(true))
			.andExpect(jsonPath("$.status").value("REVOKED"))
			.andExpect(jsonPath("$.adminId").value(9))
			.andExpect(jsonPath("$.adminDisplayName").value("등록관리자"))
			.andExpect(jsonPath("$.revokedBy").value(8))
			.andExpect(jsonPath("$.revokerDisplayName").value("해제관리자"))
			.andExpect(jsonPath("$.revokeReason").value("검토 후 해제"));

		verify(sanctionService).getSanction(100L, 10L, true);
	}

	@Test
	@DisplayName("비로그인 사용자와 일반 회원의 제재 목록 및 상세 조회를 거절한다")
	void rejectSanctionReadsWithoutAdminAccess() throws Exception {
		for (String url : List.of(URL, URL + "/100")) {
			mockMvc.perform(get(url)).andExpect(status().isUnauthorized());

			mockMvc.perform(get(url).with(authentication(createAuthentication(10L, "ROLE_MEMBER"))))
				.andExpect(status().isForbidden());
		}

		verifyNoInteractions(sanctionService);
	}

	@ParameterizedTest
	@CsvSource({"filter, UNKNOWN", "memberId, 0", "memberId, abc", "page, 0", "page, abc", "size, 0", "size, 101"})
	@DisplayName("잘못된 제재 목록 조회 조건은 서비스 호출 전에 거절한다")
	void rejectInvalidSanctionListParameter(String parameter, String value) throws Exception {
		mockMvc.perform(get(URL).param(parameter, value).with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(sanctionService);
	}

	@Test
	@DisplayName("없는 제재 상세에 대한 서비스의 404를 반환한다")
	void returnNotFoundForMissingSanctionDetail() throws Exception {
		when(sanctionService.getSanction(999L, 10L, true)).thenThrow(
			new ResponseStatusException(HttpStatus.NOT_FOUND, "제재 이력을 찾을 수 없습니다."));

		mockMvc.perform(get(URL + "/999").with(authentication(createAuthentication(10L, "ROLE_ADMIN"))))
			.andExpect(status().isNotFound());

		verify(sanctionService).getSanction(999L, 10L, true);
	}

	private MockHttpServletRequestBuilder changeRequest(String method) {
		if ("POST".equals(method)) {
			return post(URL).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY);
		}
		return patch(REVOKE_URL).contentType(MediaType.APPLICATION_JSON).content(REVOKE_BODY);
	}

	private static Stream<String> invalidCreationBodies() {
		String pastEnd = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).toString();

		return Stream.of("{\"reason\":\"규칙 위반\",\"endsAt\":null}",
			CREATE_BODY.replace("\"memberId\":20", "\"memberId\":0"), CREATE_BODY.replace("규칙 위반", "   "),
			CREATE_BODY.replace("규칙 위반", "가".repeat(2001)),
			CREATE_BODY.replace("\"endsAt\":null", "\"endsAt\":\"" + pastEnd + "\""),
			CREATE_BODY.replace("\"endsAt\":null", "\"endsAt\":\"invalid\""));
	}

	private static Stream<String> invalidRevocationBodies() {
		return Stream.of("{\"revokeReason\":\"   \"}", "{\"revokeReason\":\"" + "가".repeat(2001) + "\"}");
	}

	private static Stream<Arguments> serviceFailures() {
		return Stream.of(Arguments.of("POST", new InvalidMemberRequestException("잘못된 제재 요청"), 400),
			Arguments.of("POST", new MemberUnavailableException(), 401),
			Arguments.of("POST", new ResponseStatusException(HttpStatus.CONFLICT, "이미 제재 중"), 409),
			Arguments.of("PATCH", new ResponseStatusException(HttpStatus.NOT_FOUND, "제재 없음"), 404),
			Arguments.of("PATCH", new ResponseStatusException(HttpStatus.CONFLICT, "제재 상태 변경"), 409));
	}
}