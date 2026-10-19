package io.github.kwp6zgnrsh.boardkitapi.member.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

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
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberSanctionMapper;
import io.github.kwp6zgnrsh.boardkitapi.member.model.Member;
import io.github.kwp6zgnrsh.boardkitapi.member.model.MemberSanction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@Service
@Validated
@RequiredArgsConstructor
public class AdminMemberSanctionService {

	private final MemberMapper memberMapper;
	private final MemberSanctionMapper sanctionMapper;

	@Transactional(readOnly = true)
	public AdminMemberPageResponse getMembers(@NotNull MemberSearchField field, @NotBlank String keyword,
		@Min(1) int page, @Min(1) @Max(100) int size, @NotNull @Positive Long adminId, boolean adminAccess) {
		requireAdmin(adminId, adminAccess);

		String escapedKeyword = keyword.strip().replace("=", "==").replace("%", "=%").replace("_", "=_");

		long totalElements = memberMapper.countAdminMembers(field, escapedKeyword);
		long offset = ((long)page - 1) * size;

		List<AdminMemberSummaryResponse> members =
			offset >= totalElements ? List.of() : memberMapper.selectAdminMembers(field, escapedKeyword, size, offset);

		long totalPages = totalElements / size + (totalElements % size == 0 ? 0 : 1);

		return new AdminMemberPageResponse(members, page, size, totalElements, Math.toIntExact(totalPages));
	}

	@Transactional(readOnly = true)
	public AdminMemberSanctionPageResponse getSanctions(@NotNull MemberSanctionStatusFilter filter,
		@Positive Long memberId, @Min(1) int page, @Min(1) @Max(100) int size, @NotNull @Positive Long adminId,
		boolean adminAccess) {
		requireAdmin(adminId, adminAccess);

		LocalDateTime asOf = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);

		long totalElements = sanctionMapper.countAdminSanctions(filter, memberId, asOf);
		long offset = ((long)page - 1) * size;

		List<AdminMemberSanctionSummaryResponse> sanctions = offset >= totalElements ? List.of() :
			sanctionMapper.selectAdminSanctions(filter, memberId, asOf, size, offset);

		long totalPages = totalElements / size + (totalElements % size == 0 ? 0 : 1);

		return new AdminMemberSanctionPageResponse(sanctions, page, size, totalElements, Math.toIntExact(totalPages));
	}

	@Transactional(readOnly = true)
	public AdminMemberSanctionDetailResponse getSanction(@NotNull @Positive Long sanctionId,
		@NotNull @Positive Long adminId, boolean adminAccess) {
		requireAdmin(adminId, adminAccess);

		LocalDateTime asOf = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);

		return sanctionMapper.selectAdminSanction(sanctionId, asOf)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "제재 이력을 찾을 수 없습니다."));
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Long createSanction(@NotNull @Valid MemberSanctionCreateRequest request, @NotNull @Positive Long adminId,
		boolean adminAccess) {
		requireAdmin(adminId, adminAccess);

		Member target = requireActiveTarget(request.memberId());

		if (!"MEMBER".equals(target.getRole())) {
			throw new InvalidMemberRequestException("일반 회원만 제재할 수 있습니다.");
		}
		if (memberMapper.existsActiveSanctionByMemberId(target.getId())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 제재 중인 회원입니다.");
		}

		LocalDateTime startsAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
		LocalDateTime endsAt = request.endsAt() == null ? null :
			request.endsAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime().truncatedTo(ChronoUnit.MICROS);

		if (endsAt != null && !endsAt.isAfter(startsAt)) {
			throw new InvalidMemberRequestException("제재 종료 일시는 현재보다 미래여야 합니다.");
		}

		MemberSanction sanction = MemberSanction.builder()
			.memberId(target.getId())
			.adminId(adminId)
			.reason(request.reason().strip())
			.startsAt(startsAt)
			.endsAt(endsAt)
			.build();

		if (sanctionMapper.insertSanction(sanction) != 1 || sanction.getId() == null) {
			throw new IllegalStateException("제재 등록에 실패했습니다.");
		}

		return sanction.getId();
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void revokeSanction(@NotNull @Positive Long sanctionId, @NotNull @Valid MemberSanctionRevokeRequest request,
		@NotNull @Positive Long adminId, boolean adminAccess) {
		requireAdmin(adminId, adminAccess);

		MemberSanction sanction = sanctionMapper.selectSanctionById(sanctionId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "제재 이력을 찾을 수 없습니다."));

		requireActiveTarget(sanction.getMemberId());

		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

		if (sanction.getRevokedAt() != null || sanction.getStartsAt().isAfter(now) || (sanction.getEndsAt() != null
			&& !sanction.getEndsAt().isAfter(now))) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "진행 중인 제재만 해제할 수 있습니다.");
		}

		int updatedCount = sanctionMapper.revokeSanction(sanctionId, adminId, request.revokeReason().strip());

		if (updatedCount != 1) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "제재 상태가 변경되었습니다. 새로고침 후 다시 시도해 주세요.");
		}
	}

	private void requireAdmin(Long adminId, boolean adminAccess) {
		if (!adminAccess) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
		}
		if (!memberMapper.existsActiveMemberById(adminId)) {
			throw new MemberUnavailableException();
		}
	}

	private Member requireActiveTarget(Long memberId) {
		return memberMapper.selectActiveMemberByIdForUpdate(memberId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
	}
}