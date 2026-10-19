package io.github.kwp6zgnrsh.boardkitapi.member.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberSearchField;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.AdminMemberPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.service.AdminMemberSanctionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/members")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMemberApi {

	private final AdminMemberSanctionService sanctionService;

	@GetMapping
	public ResponseEntity<AdminMemberPageResponse> getMembers(@RequestParam MemberSearchField field,
		@RequestParam @NotBlank String keyword, @RequestParam(defaultValue = "1") @Min(1) int page,
		@RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
		@AuthenticationPrincipal LoginMember loginMember) {

		AdminMemberPageResponse response = sanctionService.getMembers(field, keyword, page, size,
			requireMemberId(loginMember), true);

		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
	}

	private Long requireMemberId(LoginMember loginMember) {
		if (loginMember == null || loginMember.id() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}
		return loginMember.id();
	}
}