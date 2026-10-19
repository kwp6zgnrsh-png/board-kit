package io.github.kwp6zgnrsh.boardkitapi.report.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.comment.exception.CommentNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminBlindedCommentPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminBlindedCommentSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminBlindedContentDetailResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminBlindedPostPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminBlindedPostSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.mapper.AdminBlindMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBlindService {

	private final MemberMapper memberMapper;
	private final AdminBlindMapper adminBlindMapper;

	public AdminBlindedPostPageResponse getBlindedPosts(@Min(1) int page, @Min(1) @Max(100) int size,
		@NotNull @Positive Long adminId, boolean adminAccess) {
		requireAdmin(adminId, adminAccess);

		long totalElements = adminBlindMapper.countBlindedPosts();
		long offset = ((long)page - 1) * size;

		List<AdminBlindedPostSummaryResponse> posts =
			offset >= totalElements ? List.of() : adminBlindMapper.selectBlindedPosts(size, offset);

		long totalPages = totalElements / size + (totalElements % size == 0 ? 0 : 1);

		return new AdminBlindedPostPageResponse(posts, page, size, totalElements, Math.toIntExact(totalPages));
	}

	public AdminBlindedCommentPageResponse getBlindedComments(@Min(1) int page, @Min(1) @Max(100) int size,
		@NotNull @Positive Long adminId, boolean adminAccess) {
		requireAdmin(adminId, adminAccess);

		long totalElements = adminBlindMapper.countBlindedComments();
		long offset = ((long)page - 1) * size;

		List<AdminBlindedCommentSummaryResponse> comments =
			offset >= totalElements ? List.of() : adminBlindMapper.selectBlindedComments(size, offset);

		long totalPages = totalElements / size + (totalElements % size == 0 ? 0 : 1);

		return new AdminBlindedCommentPageResponse(comments, page, size, totalElements, Math.toIntExact(totalPages));
	}

	public AdminBlindedContentDetailResponse getBlindedPost(@NotNull @Positive Long postId,
		@NotNull @Positive Long adminId, boolean adminAccess) {
		requireAdmin(adminId, adminAccess);

		return adminBlindMapper.selectBlindedPost(postId).orElseThrow(PostNotFoundException::new);
	}

	public AdminBlindedContentDetailResponse getBlindedComment(@NotNull @Positive Long commentId,
		@NotNull @Positive Long adminId, boolean adminAccess) {
		requireAdmin(adminId, adminAccess);

		return adminBlindMapper.selectBlindedComment(commentId).orElseThrow(CommentNotFoundException::new);
	}

	private void requireAdmin(Long adminId, boolean adminAccess) {
		if (!adminAccess) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
		}
		if (!memberMapper.existsActiveMemberById(adminId)) {
			throw new MemberUnavailableException();
		}
	}
}