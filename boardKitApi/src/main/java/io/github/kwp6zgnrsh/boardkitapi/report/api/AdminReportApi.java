package io.github.kwp6zgnrsh.boardkitapi.report.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.auth.model.LoginMember;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportReviewRequest;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportStatusFilter;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.request.ReportTargetActionRequest;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminReportDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminReportPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.service.AdminReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportApi {

	private final AdminReportService adminReportService;

	@GetMapping
	public ResponseEntity<AdminReportPageResponse> getReports(
		@RequestParam(defaultValue = "PENDING") ReportStatusFilter filter,
		@RequestParam(defaultValue = "1") @Min(1) int page,
		@RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
		@AuthenticationPrincipal LoginMember loginMember) {
		AdminReportPageResponse response = adminReportService.getReports(filter, page, size,
			requireMemberId(loginMember), true);

		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
	}

	@GetMapping("/{reportId}")
	public ResponseEntity<AdminReportDetailResponse> getReport(@PathVariable @Positive Long reportId,
		@AuthenticationPrincipal LoginMember loginMember) {
		AdminReportDetailResponse response = adminReportService.getReport(reportId, requireMemberId(loginMember), true);

		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
	}

	@PatchMapping(value = "/{reportId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> reviewReport(@PathVariable @Positive Long reportId,
		@Valid @RequestBody ReportReviewRequest request, @AuthenticationPrincipal LoginMember loginMember) {
		adminReportService.reviewReport(reportId, request, requireMemberId(loginMember), true);

		return ResponseEntity.noContent().build();
	}

	@PatchMapping(value = "/{reportId}/target", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> moderateReportTarget(@PathVariable @Positive Long reportId,
		@Valid @RequestBody ReportTargetActionRequest request, @AuthenticationPrincipal LoginMember loginMember) {
		adminReportService.moderateReportTarget(reportId, request, requireMemberId(loginMember), true);

		return ResponseEntity.noContent().build();
	}

	private Long requireMemberId(LoginMember loginMember) {
		if (loginMember == null || loginMember.id() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
		}

		return loginMember.id();
	}
}