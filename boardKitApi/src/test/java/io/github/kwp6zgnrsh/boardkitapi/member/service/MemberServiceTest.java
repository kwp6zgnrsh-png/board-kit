package io.github.kwp6zgnrsh.boardkitapi.member.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberDisplayNameUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberPasswordUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MemberCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MemberProfileResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MyCommentPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MyCommentSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MyPostPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MyPostSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.DuplicateMemberException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.InvalidMemberRequestException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberConflictException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.member.model.Member;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

	@Mock
	private MemberMapper memberMapper;

	@Mock
	private PasswordEncoder passwordEncoder;

	private MemberService memberService;

	@BeforeEach
	void setUp() {
		memberService = new MemberService(memberMapper, passwordEncoder);
	}

	@Test
	@DisplayName("유효한 정보로 회원을 생성한다")
	void createMemberWithValidRequest() {
		MemberCreateRequest request = validRequest("  새회원  ");

		when(memberMapper.existsByLoginId("member01")).thenReturn(false);
		when(memberMapper.existsByDisplayName("새회원")).thenReturn(false);
		when(passwordEncoder.encode("Boardkit1234!")).thenReturn("encoded-password");

		doAnswer(invocation -> {
			Member member = invocation.getArgument(0);
			ReflectionTestUtils.setField(member, "id", 2L);
			return 1;
		}).when(memberMapper).insertMember(any(Member.class));

		MemberCreateResponse response = memberService.createMember(request);

		ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);

		verify(memberMapper).insertMember(memberCaptor.capture());

		Member savedMember = memberCaptor.getValue();

		assertAll(() -> assertEquals(2L, response.id()), () -> assertEquals("member01", response.loginId()),
			() -> assertEquals("새회원", response.displayName()), () -> assertEquals("member01", savedMember.getLoginId()),
			() -> assertEquals("encoded-password", savedMember.getPasswordHash()),
			() -> assertEquals("새회원", savedMember.getDisplayName()),
			() -> assertEquals("MEMBER", savedMember.getRole()));
	}

	@Test
	@DisplayName("비밀번호와 확인값이 다르면 회원을 생성할 수 없다")
	void rejectMemberCreationWhenPasswordsDoNotMatch() {
		MemberCreateRequest request = new MemberCreateRequest("member01", "Boardkit1234!", "Different1234!", "새회원");

		assertThrows(InvalidMemberRequestException.class, () -> memberService.createMember(request));

		verifyNoInteractions(memberMapper, passwordEncoder);
	}

	@Test
	@DisplayName("공백을 제거한 닉네임이 10자를 초과하면 회원을 생성할 수 없다")
	void rejectMemberCreationWhenDisplayNameIsTooLong() {
		MemberCreateRequest request = validRequest("열한글자닉네임입니다요");

		assertThrows(InvalidMemberRequestException.class, () -> memberService.createMember(request));

		verifyNoInteractions(memberMapper, passwordEncoder);
	}

	@Test
	@DisplayName("이미 사용 중인 아이디로 회원을 생성할 수 없다")
	void rejectMemberCreationWhenLoginIdIsDuplicated() {
		MemberCreateRequest request = validRequest("새회원");

		when(memberMapper.existsByLoginId("member01")).thenReturn(true);

		assertThrows(DuplicateMemberException.class, () -> memberService.createMember(request));

		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("이미 사용 중인 닉네임으로 회원을 생성할 수 없다")
	void rejectMemberCreationWhenDisplayNameIsDuplicated() {
		MemberCreateRequest request = validRequest("새회원");

		when(memberMapper.existsByLoginId("member01")).thenReturn(false);
		when(memberMapper.existsByDisplayName("새회원")).thenReturn(true);

		assertThrows(DuplicateMemberException.class, () -> memberService.createMember(request));

		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("동시 가입으로 UNIQUE 제약이 충돌하면 회원 생성을 거절한다")
	void rejectMemberCreationWhenUniqueConstraintConflicts() {
		MemberCreateRequest request = validRequest("새회원");

		when(memberMapper.existsByLoginId("member01")).thenReturn(false);
		when(memberMapper.existsByDisplayName("새회원")).thenReturn(false);
		when(passwordEncoder.encode("Boardkit1234!")).thenReturn("encoded-password");
		when(memberMapper.insertMember(any(Member.class))).thenThrow(new DuplicateKeyException("duplicate"));

		assertThrows(DuplicateMemberException.class, () -> memberService.createMember(request));
	}

	@Test
	@DisplayName("활성 회원의 프로필을 조회한다")
	void getActiveMemberProfile() {
		LocalDateTime createdAt = LocalDateTime.of(2026, 10, 18, 12, 0);
		Member member = Member.builder()
			.id(10L)
			.loginId("member01")
			.displayName("회원")
			.role("MEMBER")
			.createdAt(createdAt)
			.build();

		when(memberMapper.selectActiveMemberById(10L)).thenReturn(Optional.of(member));

		MemberProfileResponse response = memberService.getProfile(10L);

		assertAll(() -> assertEquals(10L, response.memberId()), () -> assertEquals("member01", response.loginId()),
			() -> assertEquals("회원", response.displayName()), () -> assertEquals("MEMBER", response.role()),
			() -> assertEquals(createdAt, response.createdAt()));
	}

	@Test
	@DisplayName("존재하지 않거나 탈퇴한 회원의 프로필은 조회할 수 없다")
	void rejectProfileForUnavailableMember() {
		when(memberMapper.selectActiveMemberById(10L)).thenReturn(Optional.empty());

		assertThrows(MemberUnavailableException.class, () -> memberService.getProfile(10L));
	}

	@Test
	@DisplayName("앞뒤 공백을 제거한 닉네임으로 변경한다")
	void updateDisplayNameAfterTrimming() {
		prepareMemberForDisplayNameUpdate();
		when(memberMapper.updateDisplayName(10L, "새닉네임")).thenReturn(1);

		memberService.updateDisplayName(10L, new MemberDisplayNameUpdateRequest("  새닉네임  "));

		verify(memberMapper).existsByDisplayNameExcludingMemberId("새닉네임", 10L);
		verify(memberMapper).updateDisplayName(10L, "새닉네임");
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("현재 닉네임을 다시 제출하면 수정 없이 성공한다")
	void keepCurrentDisplayNameWithoutUpdate() {
		prepareMemberForDisplayNameUpdate();

		memberService.updateDisplayName(10L, new MemberDisplayNameUpdateRequest("  기존닉네임  "));

		verify(memberMapper, never()).existsByDisplayNameExcludingMemberId(anyString(), anyLong());
		verify(memberMapper, never()).updateDisplayName(anyLong(), anyString());
	}

	@ParameterizedTest
	@ValueSource(strings = {"   ", "12345678901"})
	@DisplayName("공백 제거 후 닉네임이 비거나 10자를 초과하면 변경을 거절한다")
	void rejectInvalidDisplayNameUpdate(String displayName) {
		assertThrows(InvalidMemberRequestException.class,
			() -> memberService.updateDisplayName(10L, new MemberDisplayNameUpdateRequest(displayName)));

		verifyNoInteractions(memberMapper, passwordEncoder);
	}

	@Test
	@DisplayName("다른 회원이 사용 중인 닉네임으로 변경할 수 없다")
	void rejectDuplicatedDisplayNameUpdate() {
		prepareMemberForDisplayNameUpdate();
		when(memberMapper.existsByDisplayNameExcludingMemberId("새닉네임", 10L)).thenReturn(true);

		assertThrows(DuplicateMemberException.class,
			() -> memberService.updateDisplayName(10L, new MemberDisplayNameUpdateRequest("새닉네임")));

		verify(memberMapper, never()).updateDisplayName(anyLong(), anyString());
	}

	@Test
	@DisplayName("중복 검사 이후 UNIQUE 제약이 충돌해도 닉네임 변경을 거절한다")
	void rejectDisplayNameUpdateWhenUniqueConstraintConflicts() {
		prepareMemberForDisplayNameUpdate();
		when(memberMapper.updateDisplayName(10L, "새닉네임")).thenThrow(new DuplicateKeyException("duplicate"));

		assertThrows(DuplicateMemberException.class,
			() -> memberService.updateDisplayName(10L, new MemberDisplayNameUpdateRequest("새닉네임")));

		verify(memberMapper).updateDisplayName(10L, "새닉네임");
	}

	@Test
	@DisplayName("존재하지 않거나 탈퇴한 회원은 닉네임을 변경할 수 없다")
	void rejectDisplayNameUpdateForUnavailableMember() {
		when(memberMapper.selectActiveMemberById(10L)).thenReturn(Optional.empty());

		assertThrows(MemberUnavailableException.class,
			() -> memberService.updateDisplayName(10L, new MemberDisplayNameUpdateRequest("새닉네임")));

		verify(memberMapper, never()).updateDisplayName(anyLong(), anyString());
	}

	@Test
	@DisplayName("회원 조회 이후 수정 대상이 사라지면 닉네임 변경을 거절한다")
	void rejectDisplayNameUpdateWhenMemberBecomesUnavailable() {
		prepareMemberForDisplayNameUpdate();
		when(memberMapper.updateDisplayName(10L, "새닉네임")).thenReturn(0);

		assertThrows(MemberUnavailableException.class,
			() -> memberService.updateDisplayName(10L, new MemberDisplayNameUpdateRequest("새닉네임")));
	}

	@ParameterizedTest
	@ValueSource(strings = {"1234", "OldBoardkit1234!"})
	@DisplayName("현재 비밀번호가 일치하면 새 해시와 조회한 인증 버전으로 변경을 요청한다")
	void updatePasswordWithValidCurrentPassword(String currentPassword) {
		prepareMemberForPasswordUpdate();
		MemberPasswordUpdateRequest request = new MemberPasswordUpdateRequest(currentPassword, "NewBoardkit1234!",
			"NewBoardkit1234!");

		when(passwordEncoder.matches(currentPassword, "old-hash")).thenReturn(true);
		when(passwordEncoder.encode("NewBoardkit1234!")).thenReturn("new-hash");
		when(memberMapper.updatePassword(10L, "new-hash", 3L)).thenReturn(1);

		memberService.updatePassword(10L, request);

		verify(passwordEncoder).matches(currentPassword, "old-hash");
		verify(passwordEncoder).matches("NewBoardkit1234!", "old-hash");
		verify(memberMapper).updatePassword(10L, "new-hash", 3L);
	}

	@Test
	@DisplayName("새 비밀번호와 확인값이 다르면 변경을 거절한다")
	void rejectPasswordUpdateWhenConfirmationDoesNotMatch() {
		MemberPasswordUpdateRequest request = new MemberPasswordUpdateRequest("OldBoardkit1234!", "NewBoardkit1234!",
			"DifferentBoardkit1234!");

		assertThrows(InvalidMemberRequestException.class, () -> memberService.updatePassword(10L, request));

		verifyNoInteractions(memberMapper, passwordEncoder);
	}

	@Test
	@DisplayName("현재 비밀번호가 틀리면 변경을 거절한다")
	void rejectPasswordUpdateWhenCurrentPasswordDoesNotMatch() {
		prepareMemberForPasswordUpdate();
		MemberPasswordUpdateRequest request = validPasswordUpdateRequest();

		when(passwordEncoder.matches(request.currentPassword(), "old-hash")).thenReturn(false);

		assertThrows(InvalidMemberRequestException.class, () -> memberService.updatePassword(10L, request));

		verify(passwordEncoder, never()).encode(anyString());
		verify(memberMapper, never()).updatePassword(anyLong(), anyString(), anyLong());
	}

	@Test
	@DisplayName("새 비밀번호가 현재 비밀번호와 같으면 변경을 거절한다")
	void rejectPasswordUpdateWhenNewPasswordMatchesCurrentPassword() {
		prepareMemberForPasswordUpdate();
		MemberPasswordUpdateRequest request = new MemberPasswordUpdateRequest("OldBoardkit1234!", "OldBoardkit1234!",
			"OldBoardkit1234!");

		when(passwordEncoder.matches("OldBoardkit1234!", "old-hash")).thenReturn(true);

		assertThrows(InvalidMemberRequestException.class, () -> memberService.updatePassword(10L, request));

		verify(passwordEncoder, never()).encode(anyString());
		verify(memberMapper, never()).updatePassword(anyLong(), anyString(), anyLong());
	}

	@Test
	@DisplayName("현재 비밀번호가 BCrypt 처리 한도를 넘으면 비교 전에 거절한다")
	void rejectOversizedCurrentPasswordBeforeComparison() {
		MemberPasswordUpdateRequest request = new MemberPasswordUpdateRequest("a".repeat(73), "NewBoardkit1234!",
			"NewBoardkit1234!");

		assertThrows(InvalidMemberRequestException.class, () -> memberService.updatePassword(10L, request));

		verifyNoInteractions(memberMapper, passwordEncoder);
	}

	@Test
	@DisplayName("존재하지 않거나 탈퇴한 회원은 비밀번호를 변경할 수 없다")
	void rejectPasswordUpdateForUnavailableMember() {
		when(memberMapper.selectActiveMemberById(10L)).thenReturn(Optional.empty());

		assertThrows(MemberUnavailableException.class,
			() -> memberService.updatePassword(10L, validPasswordUpdateRequest()));

		verifyNoInteractions(passwordEncoder);
		verify(memberMapper, never()).updatePassword(anyLong(), anyString(), anyLong());
	}

	@Test
	@DisplayName("인증 버전이 변경되어 UPDATE가 적용되지 않으면 충돌로 처리한다")
	void rejectPasswordUpdateWhenAuthVersionConflicts() {
		prepareMemberForPasswordUpdate();
		MemberPasswordUpdateRequest request = validPasswordUpdateRequest();

		when(passwordEncoder.matches(request.currentPassword(), "old-hash")).thenReturn(true);
		when(passwordEncoder.encode(request.newPassword())).thenReturn("new-hash");
		when(memberMapper.updatePassword(10L, "new-hash", 3L)).thenReturn(0);

		assertThrows(MemberConflictException.class, () -> memberService.updatePassword(10L, request));
	}

	@Test
	@DisplayName("비밀번호 저장 중 DB 오류가 발생하면 실패를 전달한다")
	void propagatePasswordUpdateStorageFailure() {
		prepareMemberForPasswordUpdate();
		MemberPasswordUpdateRequest request = validPasswordUpdateRequest();
		DataAccessResourceFailureException failure = new DataAccessResourceFailureException("DB 연결 실패");

		when(passwordEncoder.matches(request.currentPassword(), "old-hash")).thenReturn(true);
		when(passwordEncoder.encode(request.newPassword())).thenReturn("new-hash");
		when(memberMapper.updatePassword(10L, "new-hash", 3L)).thenThrow(failure);

		assertSame(failure,
			assertThrows(DataAccessResourceFailureException.class, () -> memberService.updatePassword(10L, request)));
	}

	@ParameterizedTest
	@CsvSource({"21, 3", "20, 2"})
	@DisplayName("내 게시글 두 번째 페이지와 전체 페이지 수를 반환한다")
	void getMyPostsWithPagination(long totalElements, int totalPages) {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);
		when(memberMapper.countMyPosts(10L)).thenReturn(totalElements);

		List<MyPostSummaryResponse> posts = List.of(
			new MyPostSummaryResponse(11L, "free", "자유게시판", "ARCHIVED", "내 게시글", true, 5L,
				LocalDateTime.of(2026, 10, 18, 12, 0)));
		when(memberMapper.selectMyPosts(10L, 10, 10L)).thenReturn(posts);

		MyPostPageResponse response = memberService.getMyPosts(10L, 2, 10);

		assertAll(() -> assertEquals(posts, response.posts()), () -> assertEquals(2, response.page()),
			() -> assertEquals(10, response.size()), () -> assertEquals(totalElements, response.totalElements()),
			() -> assertEquals(totalPages, response.totalPages()));

		verify(memberMapper).selectMyPosts(10L, 10, 10L);
		verifyNoInteractions(passwordEncoder);
	}

	@ParameterizedTest
	@CsvSource({"21, 3", "20, 2"})
	@DisplayName("내 댓글과 대댓글을 페이지 정보와 함께 반환한다")
	void getMyCommentsWithPagination(long totalElements, int totalPages) {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);
		when(memberMapper.countMyComments(10L)).thenReturn(totalElements);

		LocalDateTime createdAt = LocalDateTime.of(2026, 10, 18, 12, 0);
		List<MyCommentSummaryResponse> comments = List.of(
			new MyCommentSummaryResponse(31L, 100L, null, "free", "자유게시판", "ACTIVE", false, "내 댓글", createdAt),
			new MyCommentSummaryResponse(30L, 99L, 20L, "archived", "보관 게시판", "ARCHIVED", true, "삭제된 글에 남긴 내 대댓글",
				createdAt.minusMinutes(1)));
		when(memberMapper.selectMyComments(10L, 10, 10L)).thenReturn(comments);

		MyCommentPageResponse response = memberService.getMyComments(10L, 2, 10);

		assertAll(() -> assertEquals(comments, response.comments()), () -> assertEquals(2, response.page()),
			() -> assertEquals(10, response.size()), () -> assertEquals(totalElements, response.totalElements()),
			() -> assertEquals(totalPages, response.totalPages()));

		verify(memberMapper).selectMyComments(10L, 10, 10L);
		verifyNoInteractions(passwordEncoder);
	}

	@ParameterizedTest(name = "total={0}, page={1}, totalPages={2}")
	@CsvSource({"0, 1, 0", "11, 3, 2"})
	@DisplayName("내 댓글이 없거나 마지막 페이지를 넘으면 목록 쿼리 없이 빈 페이지를 반환한다")
	void returnEmptyMyCommentsWithoutListQuery(long total, int page, int totalPages) {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);
		when(memberMapper.countMyComments(10L)).thenReturn(total);

		MyCommentPageResponse response = memberService.getMyComments(10L, page, 10);

		assertAll(() -> assertTrue(response.comments().isEmpty()), () -> assertEquals(page, response.page()),
			() -> assertEquals(10, response.size()), () -> assertEquals(total, response.totalElements()),
			() -> assertEquals(totalPages, response.totalPages()));

		verify(memberMapper, never()).selectMyComments(anyLong(), anyInt(), anyLong());
	}

	@Test
	@DisplayName("존재하지 않거나 탈퇴한 회원의 댓글 내역은 조회할 수 없다")
	void rejectMyCommentsForUnavailableMember() {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(false);

		assertThrows(MemberUnavailableException.class, () -> memberService.getMyComments(10L, 1, 10));

		verify(memberMapper).existsActiveMemberById(10L);
		verifyNoMoreInteractions(memberMapper);
		verifyNoInteractions(passwordEncoder);
	}

	@ParameterizedTest(name = "total={0}, page={1}, totalPages={2}")
	@CsvSource({"0, 1, 0", "11, 3, 2"})
	@DisplayName("내 게시글이 없거나 마지막 페이지를 넘으면 목록 쿼리 없이 빈 페이지를 반환한다")
	void returnEmptyMyPostsWithoutListQuery(long total, int page, int totalPages) {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);
		when(memberMapper.countMyPosts(10L)).thenReturn(total);

		MyPostPageResponse response = memberService.getMyPosts(10L, page, 10);

		assertAll(() -> assertTrue(response.posts().isEmpty()), () -> assertEquals(page, response.page()),
			() -> assertEquals(10, response.size()), () -> assertEquals(total, response.totalElements()),
			() -> assertEquals(totalPages, response.totalPages()));

		verify(memberMapper, never()).selectMyPosts(anyLong(), anyInt(), anyLong());
	}

	@Test
	@DisplayName("존재하지 않거나 탈퇴한 회원의 작성 내역은 조회할 수 없다")
	void rejectMyPostsForUnavailableMember() {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(false);

		assertThrows(MemberUnavailableException.class, () -> memberService.getMyPosts(10L, 1, 10));

		verify(memberMapper).existsActiveMemberById(10L);
		verifyNoMoreInteractions(memberMapper);
		verifyNoInteractions(passwordEncoder);
	}

	private void prepareMemberForPasswordUpdate() {
		Member member = Member.builder().id(10L).passwordHash("old-hash").authVersion(3L).build();

		when(memberMapper.selectActiveMemberById(10L)).thenReturn(Optional.of(member));
	}

	private MemberPasswordUpdateRequest validPasswordUpdateRequest() {
		return new MemberPasswordUpdateRequest("OldBoardkit1234!", "NewBoardkit1234!", "NewBoardkit1234!");
	}

	private void prepareMemberForDisplayNameUpdate() {
		Member member = Member.builder().id(10L).displayName("기존닉네임").build();

		when(memberMapper.selectActiveMemberById(10L)).thenReturn(Optional.of(member));
	}

	private MemberCreateRequest validRequest(String displayName) {
		return new MemberCreateRequest("member01", "Boardkit1234!", "Boardkit1234!", displayName);
	}
}