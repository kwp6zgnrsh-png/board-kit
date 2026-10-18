package io.github.kwp6zgnrsh.boardkitapi.member.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MyCommentSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MyPostSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.model.Member;

@Mapper
public interface MemberMapper {

	Optional<Member> selectByLoginId(@Param("loginId") String loginId);

	Optional<Member> selectActiveMemberById(@Param("memberId") Long memberId);

	boolean existsActiveMemberById(@Param("memberId") Long memberId);

	boolean existsActiveSanctionByMemberId(@Param("memberId") Long memberId);

	boolean existsByLoginId(@Param("loginId") String loginId);

	boolean existsByDisplayName(@Param("displayName") String displayName);

	int insertMember(Member member);

	boolean existsByDisplayNameExcludingMemberId(@Param("displayName") String displayName,
		@Param("memberId") Long memberId);

	int updateDisplayName(@Param("memberId") Long memberId, @Param("displayName") String displayName);

	boolean existsActiveMemberByIdAndAuthVersion(@Param("memberId") Long memberId,
		@Param("authVersion") long authVersion);

	int updatePassword(@Param("memberId") Long memberId, @Param("passwordHash") String passwordHash,
		@Param("expectedAuthVersion") long expectedAuthVersion);

	long countMyPosts(@Param("memberId") Long memberId);

	List<MyPostSummaryResponse> selectMyPosts(@Param("memberId") Long memberId, @Param("limit") int limit,
		@Param("offset") long offset);

	long countMyComments(@Param("memberId") Long memberId);

	List<MyCommentSummaryResponse> selectMyComments(@Param("memberId") Long memberId, @Param("limit") int limit,
		@Param("offset") long offset);
}