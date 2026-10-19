package io.github.kwp6zgnrsh.boardkitapi.support;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;

public final class TestAuthentication {

	private TestAuthentication() {
	}

	public static Authentication createAuthentication(Long memberId, String authority) {
		return new UsernamePasswordAuthenticationToken(new LoginMember(memberId), null,
			List.of(new SimpleGrantedAuthority(authority)));
	}
}
