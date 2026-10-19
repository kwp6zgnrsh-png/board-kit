package io.github.kwp6zgnrsh.boardkitapi.member.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberSanctionCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberSanctionRevokeRequest;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.request.MemberSanctionStatusFilter;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.AdminMemberSanctionDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.AdminMemberSanctionPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.dto.response.MemberSanctionCreateResponse;
import io.github.kwp6zgnrsh.boardkitapi.member.service.AdminMemberSanctionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/member-sanctions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMemberSanctionApi {

	private final AdminMemberSanctionService sanctionService;

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<MemberSanctionCreateResponse> createSanction(
		@Valid @RequestBody MemberSanctionCreateRequest request, @AuthenticationPrincipal LoginMember loginMember) {
		Long id = sanctionService.createSanction(request, requireMemberId(loginMember), true);

		return ResponseEntity.status(HttpStatus.CREATED)
			.cacheControl(CacheControl.noStore())
			.body(new MemberSanctionCreateResponse(id));
	}

	@GetMapping
	public ResponseEntity<AdminMemberSanctionPageResponse> getSanctions(
		@RequestParam(defaultValue = "ACTIVE") MemberSanctionStatusFilter filter,
		@RequestParam(required = false) @Positive Long memberId, @RequestParam(defaultValue = "1") @Min(1) int page,
		@RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
		@AuthenticationPrincipal LoginMember loginMember) {

		AdminMemberSanctionPageResponse response = sanctionService.getSanctions(filter, memberId, page, size,
			requireMemberId(loginMember), true);

		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
	}

	@GetMapping("/{sanctionId}")
	public ResponseEntity<AdminMemberSanctionDetailResponse> getSanction(@PathVariable @Positive Long sanctionId,
		@AuthenticationPrincipal LoginMember loginMember) {
		AdminMemberSanctionDetailResponse response = sanctionService.getSanction(sanctionId,
			requireMemberId(loginMember), true);

		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
	}

	@PatchMapping(value = "/{sanctionId}/revoke", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> revokeSanction(@PathVariable @Positive Long sanctionId,
		@Valid @RequestBody MemberSanctionRevokeRequest request, @AuthenticationPrincipal LoginMember loginMember) {
		sanctionService.revokeSanction(sanctionId, request, requireMemberId(loginMember), true);

		return ResponseEntity.noContent().build();
	}

	private Long requireMemberId(LoginMember loginMember) {
		if (loginMember == null || loginMember.id() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}

		return loginMember.id();
	}
}