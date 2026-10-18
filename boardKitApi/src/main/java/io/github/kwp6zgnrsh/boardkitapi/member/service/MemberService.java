package io.github.kwp6zgnrsh.boardkitapi.member.service;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import io.github.kwp6zgnrsh.boardkitapi.common.security.PasswordLimits;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

	public MemberProfileResponse getProfile(@NotNull Long memberId) {
		Member member = memberMapper.selectActiveMemberById(memberId).orElseThrow(MemberUnavailableException::new);

		return new MemberProfileResponse(member.getId(), member.getLoginId(), member.getDisplayName(), member.getRole(),
			member.getCreatedAt());
	}

	@Transactional(readOnly = true)
	public MyPostPageResponse getMyPosts(@NotNull Long memberId, @Min(1) int page, @Min(1) @Max(100) int size) {
		if (!memberMapper.existsActiveMemberById(memberId)) {
			throw new MemberUnavailableException();
		}

		long totalElements = memberMapper.countMyPosts(memberId);
		long offset = ((long)page - 1) * size;

		List<MyPostSummaryResponse> posts;
		if (totalElements == 0 || offset >= totalElements) {
			posts = List.of();
		} else {
			posts = memberMapper.selectMyPosts(memberId, size, offset);
		}

		long totalPages = totalElements / size + (totalElements % size == 0 ? 0 : 1);

		return new MyPostPageResponse(posts, page, size, totalElements, Math.toIntExact(totalPages));
	}

	@Transactional
	public void updateDisplayName(@NotNull Long memberId, @NotNull @Valid MemberDisplayNameUpdateRequest request) {
		String displayName = normalizeDisplayName(request.displayName());

		Member member = memberMapper.selectActiveMemberById(memberId).orElseThrow(MemberUnavailableException::new);

		if (displayName.equals(member.getDisplayName())) {
			return;
		}

		if (memberMapper.existsByDisplayNameExcludingMemberId(displayName, memberId)) {
			throw new DuplicateMemberException("이미 사용 중인 닉네임입니다.");
		}

		try {
			if (memberMapper.updateDisplayName(memberId, displayName) != 1) {
				throw new MemberUnavailableException();
			}
		} catch (DuplicateKeyException exception) {
			throw new DuplicateMemberException("이미 사용 중인 닉네임입니다.");
		}
	}

	@Transactional
	public void updatePassword(@NotNull Long memberId, @NotNull @Valid MemberPasswordUpdateRequest request) {
		validatePasswordConfirmation(request.newPassword(), request.newPasswordConfirm());

		if (request.currentPassword().getBytes(StandardCharsets.UTF_8).length
			> PasswordLimits.BCRYPT_MAX_PASSWORD_BYTES) {
			throw new InvalidMemberRequestException("현재 비밀번호가 올바르지 않습니다.");
		}

		Member member = memberMapper.selectActiveMemberById(memberId).orElseThrow(MemberUnavailableException::new);

		if (!passwordEncoder.matches(request.currentPassword(), member.getPasswordHash())) {
			throw new InvalidMemberRequestException("현재 비밀번호가 올바르지 않습니다.");
		}

		if (passwordEncoder.matches(request.newPassword(), member.getPasswordHash())) {
			throw new InvalidMemberRequestException("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
		}

		String passwordHash = passwordEncoder.encode(request.newPassword());

		if (memberMapper.updatePassword(memberId, passwordHash, member.getAuthVersion()) != 1) {
			throw new MemberConflictException();
		}
	}

	@Transactional(readOnly = true)
	public MyCommentPageResponse getMyComments(@NotNull Long memberId, @Min(1) int page, @Min(1) @Max(100) int size) {
		if (!memberMapper.existsActiveMemberById(memberId)) {
			throw new MemberUnavailableException();
		}

		long totalElements = memberMapper.countMyComments(memberId);
		long offset = ((long)page - 1) * size;

		List<MyCommentSummaryResponse> comments;
		if (totalElements == 0 || offset >= totalElements) {
			comments = List.of();
		} else {
			comments = memberMapper.selectMyComments(memberId, size, offset);
		}

		long totalPages = totalElements / size + (totalElements % size == 0 ? 0 : 1);

		return new MyCommentPageResponse(comments, page, size, totalElements, Math.toIntExact(totalPages));
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