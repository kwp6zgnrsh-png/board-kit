package io.github.kwp6zgnrsh.boardkitapi.member.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MemberCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.DuplicateMemberException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.InvalidMemberRequestException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.member.model.Member;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@Service
@Validated
@RequiredArgsConstructor
public class MemberService {

	private static final int MAX_DISPLAY_NAME_LENGTH = 10;
	private static final String MEMBER_ROLE = "MEMBER";

	private final MemberMapper memberMapper;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public MemberCreateResponse createMember(@NotNull @Valid MemberCreateRequest request) {
		validatePasswordConfirmation(request.password(), request.passwordConfirm());

		String displayName = normalizeDisplayName(request.displayName());

		validateDuplicateMember(request.loginId(), displayName);

		String passwordHash = passwordEncoder.encode(request.password());

		Member member = Member.builder()
			.loginId(request.loginId())
			.passwordHash(passwordHash)
			.displayName(displayName)
			.role(MEMBER_ROLE)
			.build();

		try {
			int insertedCount = memberMapper.insertMember(member);

			if (insertedCount != 1 || member.getId() == null) {
				throw new IllegalStateException("회원 저장에 실패했습니다.");
			}
		} catch (DuplicateKeyException exception) {
			throw new DuplicateMemberException("이미 사용 중인 아이디 또는 닉네임입니다.");
		}

		return new MemberCreateResponse(member.getId(), member.getLoginId(), member.getDisplayName());
	}

	private void validatePasswordConfirmation(String password, String passwordConfirm) {
		if (!password.equals(passwordConfirm)) {
			throw new InvalidMemberRequestException("비밀번호와 비밀번호 확인이 일치하지 않습니다.");
		}
	}

	private String normalizeDisplayName(String displayName) {
		String normalizedDisplayName = displayName.strip();

		if (normalizedDisplayName.isEmpty() || normalizedDisplayName.length() > MAX_DISPLAY_NAME_LENGTH) {
			throw new InvalidMemberRequestException("닉네임은 앞뒤 공백을 제외하고 1자 이상 10자 이하이어야 합니다.");
		}

		return normalizedDisplayName;
	}

	private void validateDuplicateMember(String loginId, String displayName) {
		if (memberMapper.existsByLoginId(loginId)) {
			throw new DuplicateMemberException("이미 사용 중인 아이디입니다.");
		}

		if (memberMapper.existsByDisplayName(displayName)) {
			throw new DuplicateMemberException("이미 사용 중인 닉네임입니다.");
		}
	}
}