package io.github.kwp6zgnrsh.boardkitapi.report.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.comment.exception.CommentNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminBlindedCommentSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.dto.response.AdminBlindedPostSummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.report.mapper.AdminBlindMapper;

@ExtendWith(MockitoExtension.class)
class AdminBlindServiceTest {

	@Mock
	private MemberMapper memberMapper;
	@Mock
	private AdminBlindMapper adminBlindMapper;

	private AdminBlindService service;

	@BeforeEach
	void setUp() {
		service = new AdminBlindService(memberMapper, adminBlindMapper);
	}

	@Test
	@DisplayName("관리자는 블라인드 게시글 목록을 페이지 단위로 조회한다")
	void getBlindedPostsWithPagination() {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);

		var post = new AdminBlindedPostSummaryResponse(100L, "free", "자유게시판", "ARCHIVED", "게시글 제목", 20L, "작성자",
			LocalDateTime.of(2026, 10, 19, 10, 0), 4);
		when(adminBlindMapper.countBlindedPosts()).thenReturn(11L);
		when(adminBlindMapper.selectBlindedPosts(10, 10L)).thenReturn(List.of(post));

		var result = service.getBlindedPosts(2, 10, 10L, true);

		assertAll(() -> assertEquals(List.of(post), result.posts()), () -> assertEquals(2, result.page()),
			() -> assertEquals(10, result.size()), () -> assertEquals(11L, result.totalElements()),
			() -> assertEquals(2, result.totalPages()));
	}

	@Test
	@DisplayName("관리자는 블라인드 댓글 목록을 페이지 단위로 조회한다")
	void getBlindedCommentsWithPagination() {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);

		var comment = new AdminBlindedCommentSummaryResponse(300L, null, 100L, "free", "자유게시판", "ACTIVE", "게시글 제목",
			"댓글 미리보기", 20L, "작성자", LocalDateTime.of(2026, 10, 19, 10, 0), 7);
		when(adminBlindMapper.countBlindedComments()).thenReturn(21L);
		when(adminBlindMapper.selectBlindedComments(10, 20L)).thenReturn(List.of(comment));

		var result = service.getBlindedComments(3, 10, 10L, true);

		assertAll(() -> assertEquals(List.of(comment), result.comments()), () -> assertEquals(3, result.page()),
			() -> assertEquals(10, result.size()), () -> assertEquals(21L, result.totalElements()),
			() -> assertEquals(3, result.totalPages()));
	}

	@ParameterizedTest
	@CsvSource({"0, 1, 0", "10, 2, 1"})
	@DisplayName("결과가 없거나 페이지 범위를 넘으면 목록 쿼리 없이 빈 페이지를 반환한다")
	void returnEmptyPagesWithoutListQueries(long total, int page, int totalPages) {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);
		when(adminBlindMapper.countBlindedPosts()).thenReturn(total);
		when(adminBlindMapper.countBlindedComments()).thenReturn(total);

		var posts = service.getBlindedPosts(page, 10, 10L, true);
		var comments = service.getBlindedComments(page, 10, 10L, true);

		assertAll(() -> assertTrue(posts.posts().isEmpty()), () -> assertTrue(comments.comments().isEmpty()),
			() -> assertEquals(total, posts.totalElements()), () -> assertEquals(total, comments.totalElements()),
			() -> assertEquals(totalPages, posts.totalPages()), () -> assertEquals(totalPages, comments.totalPages()));

		verify(adminBlindMapper, never()).selectBlindedPosts(anyInt(), anyLong());
		verify(adminBlindMapper, never()).selectBlindedComments(anyInt(), anyLong());
	}

	@Test
	@DisplayName("비관리자는 블라인드 목록을 조회할 수 없다")
	void rejectListRequestsByNonAdmin() {
		var postError = assertThrows(ResponseStatusException.class, () -> service.getBlindedPosts(1, 10, 10L, false));
		var commentError = assertThrows(ResponseStatusException.class,
			() -> service.getBlindedComments(1, 10, 10L, false));

		assertEquals(HttpStatus.FORBIDDEN, postError.getStatusCode());
		assertEquals(HttpStatus.FORBIDDEN, commentError.getStatusCode());

		var postDetailError = assertThrows(ResponseStatusException.class,
			() -> service.getBlindedPost(100L, 10L, false));
		var commentDetailError = assertThrows(ResponseStatusException.class,
			() -> service.getBlindedComment(300L, 10L, false));

		assertEquals(HttpStatus.FORBIDDEN, postDetailError.getStatusCode());
		assertEquals(HttpStatus.FORBIDDEN, commentDetailError.getStatusCode());
		verifyNoInteractions(memberMapper, adminBlindMapper);
	}

	@Test
	@DisplayName("비활성 회원은 관리자 권한이 있어도 블라인드 목록을 조회할 수 없다")
	void rejectListRequestsForUnavailableAdmin() {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(false);

		assertThrows(MemberUnavailableException.class, () -> service.getBlindedPosts(1, 10, 10L, true));
		assertThrows(MemberUnavailableException.class, () -> service.getBlindedComments(1, 10, 10L, true));
		assertThrows(MemberUnavailableException.class, () -> service.getBlindedPost(100L, 10L, true));
		assertThrows(MemberUnavailableException.class, () -> service.getBlindedComment(300L, 10L, true));
		verifyNoInteractions(adminBlindMapper);
	}

	@Test
	@DisplayName("더 이상 조회할 수 없는 블라인드 대상은 찾을 수 없음으로 처리한다")
	void rejectUnavailableBlindedDetails() {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);
		when(adminBlindMapper.selectBlindedPost(100L)).thenReturn(Optional.empty());
		when(adminBlindMapper.selectBlindedComment(300L)).thenReturn(Optional.empty());

		assertThrows(PostNotFoundException.class, () -> service.getBlindedPost(100L, 10L, true));
		assertThrows(CommentNotFoundException.class, () -> service.getBlindedComment(300L, 10L, true));
	}
}