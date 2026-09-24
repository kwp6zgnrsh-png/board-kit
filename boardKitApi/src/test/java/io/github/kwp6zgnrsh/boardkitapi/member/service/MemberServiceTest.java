package io.github.kwp6zgnrsh.boardkitapi.member.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MemberCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.DuplicateMemberException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.InvalidMemberRequestException;
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

		when(memberMapper.existsByLoginId("member01"))
			.thenReturn(false);
		when(memberMapper.existsByDisplayName("새회원"))
			.thenReturn(false);
		when(passwordEncoder.encode("Boardkit1234!"))
			.thenReturn("encoded-password");

		doAnswer(invocation -> {
			Member member = invocation.getArgument(0);
			ReflectionTestUtils.setField(member, "id", 2L);
			return 1;
		}).when(memberMapper).insertMember(any(Member.class));

		MemberCreateResponse response = memberService.createMember(request);

		ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);

		verify(memberMapper).insertMember(memberCaptor.capture());

		Member savedMember = memberCaptor.getValue();

		assertAll(
			() -> assertEquals(2L, response.id()),
			() -> assertEquals("member01", response.loginId()),
			() -> assertEquals("새회원", response.displayName()),
			() -> assertEquals("member01", savedMember.getLoginId()),
			() -> assertEquals("encoded-password", savedMember.getPasswordHash()),
			() -> assertEquals("새회원", savedMember.getDisplayName()),
			() -> assertEquals("MEMBER", savedMember.getRole())
		);
	}

	@Test
	@DisplayName("비밀번호와 확인값이 다르면 회원을 생성할 수 없다")
	void rejectMemberCreationWhenPasswordsDoNotMatch() {
		MemberCreateRequest request = new MemberCreateRequest(
			"member01",
			"Boardkit1234!",
			"Different1234!",
			"새회원"
		);

		assertThrows(
			InvalidMemberRequestException.class,
			() -> memberService.createMember(request)
		);

		verifyNoInteractions(memberMapper, passwordEncoder);
	}

	@Test
	@DisplayName("공백을 제거한 닉네임이 10자를 초과하면 회원을 생성할 수 없다")
	void rejectMemberCreationWhenDisplayNameIsTooLong() {
		MemberCreateRequest request = validRequest("열한글자닉네임입니다요");

		assertThrows(
			InvalidMemberRequestException.class,
			() -> memberService.createMember(request)
		);

		verifyNoInteractions(memberMapper, passwordEncoder);
	}

	@Test
	@DisplayName("이미 사용 중인 아이디로 회원을 생성할 수 없다")
	void rejectMemberCreationWhenLoginIdIsDuplicated() {
		MemberCreateRequest request = validRequest("새회원");

		when(memberMapper.existsByLoginId("member01"))
			.thenReturn(true);

		assertThrows(
			DuplicateMemberException.class,
			() -> memberService.createMember(request)
		);

		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("이미 사용 중인 닉네임으로 회원을 생성할 수 없다")
	void rejectMemberCreationWhenDisplayNameIsDuplicated() {
		MemberCreateRequest request = validRequest("새회원");

		when(memberMapper.existsByLoginId("member01"))
			.thenReturn(false);
		when(memberMapper.existsByDisplayName("새회원"))
			.thenReturn(true);

		assertThrows(
			DuplicateMemberException.class,
			() -> memberService.createMember(request)
		);

		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("동시 가입으로 UNIQUE 제약이 충돌하면 회원 생성을 거절한다")
	void rejectMemberCreationWhenUniqueConstraintConflicts() {
		MemberCreateRequest request = validRequest("새회원");

		when(memberMapper.existsByLoginId("member01"))
			.thenReturn(false);
		when(memberMapper.existsByDisplayName("새회원"))
			.thenReturn(false);
		when(passwordEncoder.encode("Boardkit1234!"))
			.thenReturn("encoded-password");
		when(memberMapper.insertMember(any(Member.class)))
			.thenThrow(new DuplicateKeyException("duplicate"));

		assertThrows(
			DuplicateMemberException.class,
			() -> memberService.createMember(request)
		);
	}

	private MemberCreateRequest validRequest(String displayName) {
		return new MemberCreateRequest(
			"member01",
			"Boardkit1234!",
			"Boardkit1234!",
			displayName
		);
	}
}