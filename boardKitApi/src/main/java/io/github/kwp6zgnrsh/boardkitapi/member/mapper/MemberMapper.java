package io.github.kwp6zgnrsh.boardkitapi.member.mapper;

import io.github.kwp6zgnrsh.boardkitapi.member.model.Member;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface MemberMapper {

	Optional<Member> selectByLoginId(@Param("loginId") String loginId);
}