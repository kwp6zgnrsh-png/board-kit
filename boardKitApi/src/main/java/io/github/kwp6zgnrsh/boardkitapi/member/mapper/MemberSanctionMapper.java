package io.github.kwp6zgnrsh.boardkitapi.member.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberSanctionStatusFilter;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.AdminMemberSanctionDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.AdminMemberSanctionSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.model.MemberSanction;

@Mapper
public interface MemberSanctionMapper {

	Optional<MemberSanction> selectSanctionById(@Param("sanctionId") Long sanctionId);

	int insertSanction(MemberSanction sanction);

	int revokeSanction(
		@Param("sanctionId") Long sanctionId,
		@Param("revokedBy") Long revokedBy,
		@Param("revokeReason") String revokeReason
	);

	long countAdminSanctions(
		@Param("filter") MemberSanctionStatusFilter filter,
		@Param("memberId") Long memberId,
		@Param("asOf") LocalDateTime asOf
	);

	List<AdminMemberSanctionSummaryResponse> selectAdminSanctions(
		@Param("filter") MemberSanctionStatusFilter filter,
		@Param("memberId") Long memberId,
		@Param("asOf") LocalDateTime asOf,
		@Param("limit") int limit,
		@Param("offset") long offset
	);

	Optional<AdminMemberSanctionDetailResponse> selectAdminSanction(
		@Param("sanctionId") Long sanctionId,
		@Param("asOf") LocalDateTime asOf
	);
}