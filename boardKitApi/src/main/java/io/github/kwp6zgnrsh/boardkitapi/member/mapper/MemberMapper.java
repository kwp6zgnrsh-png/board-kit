package io.github.kwp6zgnrsh.boardkitapi.member.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import io.github.kwp6zgnrsh.boardkitapi.member.model.Member;

@Mapper
public interface MemberMapper {

	Optional<Member> selectByLoginId(@Param("loginId") String loginId);

	boolean existsActiveMemberById(@Param("memberId") Long memberId);

	boolean existsActiveSanctionByMemberId(@Param("memberId") Long memberId);

	boolean existsByLoginId(@Param("loginId") String loginId);

	boolean existsByDisplayName(@Param("displayName") String displayName);

	int insertMember(Member member);
}