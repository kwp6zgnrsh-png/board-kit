package io.github.kwp6zgnrsh.boardkitapi.auth.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RevokedTokenMapper {

	int insertRevokedToken(@Param("tokenId") String tokenId, @Param("expiresAt") LocalDateTime expiresAt);

	boolean existsRevokedToken(@Param("tokenId") String tokenId);

	int deleteExpiredTokens();
}