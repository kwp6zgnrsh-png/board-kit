package io.github.kwp6zgnrsh.boardkitapi.config;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import io.github.kwp6zgnrsh.boardkitapi.auth.mapper.RevokedTokenMapper;
import io.github.kwp6zgnrsh.boardkitapi.auth.security.JwtAuthenticationFilter;
import io.github.kwp6zgnrsh.boardkitapi.auth.token.JwtTokenProvider;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;

@Configuration
public class SecurityConfig {

	private final JwtTokenProvider jwtTokenProvider;
	private final RevokedTokenMapper revokedTokenMapper;
	private final MemberMapper memberMapper;
	private final boolean cookieSecure;

	public SecurityConfig(JwtTokenProvider jwtTokenProvider, RevokedTokenMapper revokedTokenMapper,
		MemberMapper memberMapper, @Value("${boardkit.auth.cookie-secure:true}") boolean cookieSecure) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.revokedTokenMapper = revokedTokenMapper;
		this.memberMapper = memberMapper;
		this.cookieSecure = cookieSecure;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

		JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(
			jwtTokenProvider, revokedTokenMapper, memberMapper
		);
		
		CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();

		csrfRepository.setCookieCustomizer(cookie -> cookie.path("/").secure(cookieSecure).sameSite("Lax"));

		return http.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfRepository))
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

			.exceptionHandling(exception -> exception.authenticationEntryPoint((request, response, cause) -> {
				response.setStatus(HttpStatus.UNAUTHORIZED.value());
				response.setCharacterEncoding(StandardCharsets.UTF_8.name());
				response.setContentType(MediaType.APPLICATION_JSON_VALUE);
				response.getWriter().write("{\"message\":\"로그인이 필요합니다.\"}");
			}).accessDeniedHandler((request, response, cause) -> {
				response.setStatus(HttpStatus.FORBIDDEN.value());
				response.setCharacterEncoding(StandardCharsets.UTF_8.name());
				response.setContentType(MediaType.APPLICATION_JSON_VALUE);
				response.getWriter().write("{\"message\":\"접근 권한이 없습니다.\"}");
			}))

			.authorizeHttpRequests(authorize -> authorize.requestMatchers(HttpMethod.GET, "/api/auth/csrf")
				.permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/members")
				.permitAll()
				.requestMatchers(HttpMethod.GET, "/api/boards/**")
				.permitAll()
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error")
				.permitAll()
				.anyRequest()
				.authenticated())

			.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

			.build();
	}
}