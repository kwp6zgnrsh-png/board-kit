package io.github.kwp6zgnrsh.boardkitapi.member.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.kwp6zgnrsh.boardkitapi.auth.cookie.AuthCookieFactory;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MemberCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.DuplicateMemberException;
import io.github.kwp6zgnrsh.boardkitapi.member.service.MemberService;

@WebMvcTest(MemberApi.class)
@Import({
	MemberApiTest.SecurityTestConfig.class,
	AuthCookieFactory.class
})
class MemberApiTest {

	private static final String VALID_REQUEST = """
        {
          "loginId": "member01",
          "password": "Boardkit1234!",
          "passwordConfirm": "Boardkit1234!",
          "displayName": "새회원"
        }
        """;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MemberService memberService;

	@Test
	@DisplayName("비로그인 사용자는 회원가입할 수 있다")
	void createMemberWithoutAuthentication() throws Exception {
		when(memberService.createMember(
			any(MemberCreateRequest.class)
		)).thenReturn(
			new MemberCreateResponse(
				2L,
				"member01",
				"새회원"
			)
		);

		mockMvc.perform(post("/api/members")
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_REQUEST))
			.andExpect(status().isCreated())
			.andExpect(
				header().string(
					"Location",
					"/api/members/2"
				)
			)
			.andExpect(jsonPath("$.id").value(2))
			.andExpect(
				jsonPath("$.loginId").value("member01")
			)
			.andExpect(
				jsonPath("$.displayName").value("새회원")
			);

		verify(memberService).createMember(
			any(MemberCreateRequest.class)
		);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidMemberRequests")
	@DisplayName("잘못된 회원가입 입력은 서비스 호출 전에 거절한다")
	void rejectInvalidMemberCreationRequest(String scenario, String request) throws Exception {
		mockMvc.perform(post("/api/members")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(memberService);
	}

	private static Stream<Arguments> invalidMemberRequests() {
		return Stream.of(
			Arguments.of("아이디 형식", VALID_REQUEST.replace("\"member01\"", "\"Member 01\"")),
			Arguments.of("짧은 비밀번호", VALID_REQUEST.replace("\"Boardkit1234!\"", "\"short\"")),
			Arguments.of("허용하지 않는 비밀번호 문자", VALID_REQUEST.replace("\"Boardkit1234!\"", "\"보드키트비밀번호1234!\"")),
			Arguments.of("공백 닉네임", VALID_REQUEST.replace("\"새회원\"", "\"   \""))
		);
	}

	@Test
	@DisplayName("중복된 아이디나 닉네임이면 409를 반환한다")
	void returnConflictWhenMemberIsDuplicated()
		throws Exception {

		when(memberService.createMember(
			any(MemberCreateRequest.class)
		)).thenThrow(
			new DuplicateMemberException("이미 사용 중인 아이디입니다.")
		);

		mockMvc.perform(post("/api/members")
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_REQUEST))
			.andExpect(status().isConflict());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SecurityTestConfig {

		@Bean
		SecurityFilterChain securityFilterChain(
			HttpSecurity http
		) throws Exception {

			return http
				.csrf(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(authorize -> authorize
					.requestMatchers(
						HttpMethod.POST,
						"/api/members"
					).permitAll()
					.anyRequest().authenticated()
				)
				.build();
		}
	}
}