package io.github.kwp6zgnrsh.boardkitapi.auth.security;

import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtPayload;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider jwtTokenProvider;

	public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
		this.jwtTokenProvider = jwtTokenProvider;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
								   FilterChain filterChain) throws ServletException, IOException {

		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

		if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
			filterChain.doFilter(request, response);
			return;
		}

		String token = authorization.substring(BEARER_PREFIX.length());

		try {
			JwtPayload payload = jwtTokenProvider.parseAccessToken(token);
			validatePayload(payload);

			LoginMember principal = new LoginMember(payload.memberId());

			SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + payload.role());

			UsernamePasswordAuthenticationToken authentication =
				new UsernamePasswordAuthenticationToken(
					principal,
					null,
					List.of(authority)
				);

			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(authentication);
			SecurityContextHolder.setContext(context);
		} catch (JwtException | IllegalArgumentException exception) {
			SecurityContextHolder.clearContext();
		}

		filterChain.doFilter(request, response);
	}

	private void validatePayload(JwtPayload payload) {
		if (payload.memberId() == null || payload.memberId() <= 0) {
			throw new IllegalArgumentException("유효하지 않은 회원 ID입니다.");
		}

		if (!"MEMBER".equals(payload.role()) && !"ADMIN".equals(payload.role())) {
			throw new IllegalArgumentException("유효하지 않은 회원 권한입니다.");
		}
	}
}