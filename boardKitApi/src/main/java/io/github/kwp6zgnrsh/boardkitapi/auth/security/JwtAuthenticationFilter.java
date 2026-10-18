package io.github.kwp6zgnrsh.boardkitapi.auth.security;

import java.io.IOException;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.kwp6zgnrsh.boardkitapi.auth.cookie.AuthCookieFactory;
import io.github.kwp6zgnrsh.boardkitapi.auth.mapper.RevokedTokenMapper;
import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtPayload;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtTokenProvider;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtTokenProvider jwtTokenProvider;
	private final RevokedTokenMapper revokedTokenMapper;
	private final MemberMapper memberMapper;

	public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, RevokedTokenMapper revokedTokenMapper,
		MemberMapper memberMapper) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.revokedTokenMapper = revokedTokenMapper;
		this.memberMapper = memberMapper;
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
		@NonNull FilterChain filterChain) throws ServletException, IOException {
		String token = resolveToken(request);

		if (token == null || token.isBlank()) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			JwtPayload payload = jwtTokenProvider.parseAccessToken(token);
			validatePayload(payload);

			if (revokedTokenMapper.existsRevokedToken(payload.tokenId())) {
				throw new JwtException("무효화된 인증 토큰입니다.");
			}

			if (!memberMapper.existsActiveMemberByIdAndAuthVersion(payload.memberId(), payload.authVersion())) {
				throw new JwtException("사용할 수 없는 인증 정보입니다.");
			}

			LoginMember principal = new LoginMember(payload.memberId());
			SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + payload.role());

			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal,
				null, List.of(authority));

			authentication.setDetails(payload);

			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(authentication);
			SecurityContextHolder.setContext(context);
		} catch (JwtException | IllegalArgumentException exception) {
			SecurityContextHolder.clearContext();
		}

		filterChain.doFilter(request, response);
	}

	private @Nullable String resolveToken(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();

		if (cookies == null) {
			return null;
		}

		for (Cookie cookie : cookies) {
			if (AuthCookieFactory.ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}

		return null;
	}

	private void validatePayload(JwtPayload payload) {
		if (payload.memberId() == null || payload.memberId() <= 0) {
			throw new IllegalArgumentException("유효하지 않은 회원 ID입니다.");
		}

		if (!"MEMBER".equals(payload.role()) && !"ADMIN".equals(payload.role())) {
			throw new IllegalArgumentException("유효하지 않은 회원 권한입니다.");
		}

		if (payload.authVersion() < 0) {
			throw new IllegalArgumentException("유효하지 않은 인증 버전입니다.");
		}
	}
}