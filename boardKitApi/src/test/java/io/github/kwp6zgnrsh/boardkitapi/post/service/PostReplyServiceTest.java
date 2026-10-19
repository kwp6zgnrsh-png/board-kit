package io.github.kwp6zgnrsh.boardkitapi.post.service;

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
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import io.github.kwp6zgnrsh.boardkitapi.board.exception.BoardAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.board.mapper.BoardMapper;
import io.github.kwp6zgnrsh.boardkitapi.board.model.Board;
import io.github.kwp6zgnrsh.boardkitapi.board.model.BoardPolicy;
import io.github.kwp6zgnrsh.boardkitapi.common.exception.BlindedContentAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.member.exception.MemberUnavailableException;
import io.github.kwp6zgnrsh.boardkitapi.member.mapper.MemberMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.converter.PostConverter;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostReplyCreateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostReplyFilter;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.request.PostReplyUpdateRequest;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.AdminPostReplyPageResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.AdminPostReplySummaryResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.dto.response.PostReplyResponse;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.InvalidPostRequestException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostNotFoundException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.PostReplyConflictException;
import io.github.kwp6zgnrsh.boardkitapi.post.exception.SecretPostAccessDeniedException;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.mapper.PostReplyMapper;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostCommentTarget;
import io.github.kwp6zgnrsh.boardkitapi.post.model.PostReply;
import io.github.kwp6zgnrsh.boardkitapi.post.policy.PostSecretAccess;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

@ExtendWith(MockitoExtension.class)
class PostReplyServiceTest {

	@Mock
	private BoardMapper boardMapper;
	@Mock
	private PostMapper postMapper;
	@Mock
	private PostReplyMapper postReplyMapper;
	@Mock
	private MemberMapper memberMapper;
	@Mock
	private PasswordEncoder passwordEncoder;

	private PostReplyService service;

	@BeforeEach
	void setUp() {
		service = new PostReplyService(boardMapper, postMapper, postReplyMapper, memberMapper,
			Mappers.getMapper(PostConverter.class), new PostDocumentValidator(), new PostSecretAccess(passwordEncoder));
	}

	@Test
	@DisplayName("공식 답변 기능이 꺼져 있어도 접근 가능한 기존 답변은 조회한다")
	void readExistingReplyWhenFeatureIsDisabled() {
		prepareReadBoard("PUBLIC", false, false);
		prepareReadablePost(false, null);

		PostReplyResponse expected = replyResponse();
		when(postReplyMapper.selectPostReply(1L, 100L)).thenReturn(Optional.of(expected));

		assertEquals(Optional.of(expected), service.getPostReply("free", 100L, null, false, null));

		verify(postMapper, never()).increaseViewCount(anyLong(), anyLong());
	}

	@Test
	@DisplayName("공식 답변이 없으면 빈 결과를 반환한다")
	void returnEmptyWhenReplyDoesNotExist() {
		prepareReadBoard("PUBLIC", true, false);
		prepareReadablePost(false, null);

		when(postReplyMapper.selectPostReply(1L, 100L)).thenReturn(Optional.empty());

		assertTrue(service.getPostReply("free", 100L, null, false, null).isEmpty());
	}

	@Test
	@DisplayName("게시판 읽기 권한이 없으면 공식 답변을 조회할 수 없다")
	void rejectReplyReadWithoutBoardPermission() {
		prepareReadBoard("ADMIN", true, false);

		assertThrows(BoardAccessDeniedException.class, () -> service.getPostReply("free", 100L, 10L, false, null));

		verifyNoInteractions(postMapper, postReplyMapper);
	}

	@Test
	@DisplayName("비밀글 비밀번호가 틀리면 공식 답변도 조회할 수 없다")
	void rejectReplyReadWithWrongSecretPassword() {
		prepareReadBoard("PUBLIC", true, false);
		prepareReadablePost(true, "hash");

		when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

		assertThrows(SecretPostAccessDeniedException.class,
			() -> service.getPostReply("free", 100L, null, false, "wrong"));

		verifyNoInteractions(postReplyMapper);
	}

	@Test
	@DisplayName("관리자는 댓글이 잠긴 게시글에도 공식 답변을 등록할 수 있다")
	void createReplyRegardlessOfCommentLock() {
		prepareManagementBoard(true);
		prepareManageablePost();

		PostReplyResponse expected = replyResponse();

		when(postReplyMapper.insertPostReply(any(PostReply.class))).thenAnswer(invocation -> {
			PostReply reply = invocation.getArgument(0);
			ReflectionTestUtils.setField(reply, "id", 200L);
			return 1;
		});

		when(postReplyMapper.selectPostReply(1L, 100L)).thenReturn(Optional.of(expected));

		assertEquals(expected, service.createPostReply("free", 100L, createRequest(), 10L, true));

		ArgumentCaptor<PostReply> captor = ArgumentCaptor.forClass(PostReply.class);
		verify(postReplyMapper).insertPostReply(captor.capture());

		PostReply saved = captor.getValue();

		assertAll(() -> assertEquals(100L, saved.getPostId()), () -> assertEquals(10L, saved.getAuthorId()),
			() -> assertEquals("공식 답변", saved.getContent()),
			() -> assertEquals(document("공식 답변"), saved.getContentDocument()));

		verify(memberMapper, never()).existsActiveSanctionByMemberId(anyLong());
		verifyNoInteractions(passwordEncoder);

		InOrder order = inOrder(boardMapper, memberMapper, postMapper);

		order.verify(boardMapper).selectActiveBoardBySlugForShare("free");
		order.verify(memberMapper).existsActiveMemberById(10L);
		order.verify(boardMapper).selectPolicyByBoardId(1L);
		order.verify(postMapper).selectActiveCommentTargetForUpdate(1L, 100L);
	}

	@Test
	@DisplayName("같은 게시글의 공식 답변 중복 등록은 충돌로 처리한다")
	void rejectDuplicateReplyCreation() {
		prepareManagementBoard(true);
		prepareManageablePost();

		when(postReplyMapper.insertPostReply(any(PostReply.class))).thenThrow(new DuplicateKeyException("duplicate"));

		assertThrows(PostReplyConflictException.class,
			() -> service.createPostReply("free", 100L, createRequest(), 10L, true));

		verify(postReplyMapper, never()).selectPostReply(anyLong(), anyLong());
	}

	@Test
	@DisplayName("관리자가 아니면 공식 답변 등록과 수정 및 삭제를 모두 거절한다")
	void rejectReplyManagementByNonAdmin() {
		assertForbidden(() -> service.createPostReply("free", 100L, createRequest(), 10L, false));

		assertForbidden(() -> service.updatePostReply("free", 100L, 200L, updateRequest(), 10L, false));

		assertForbidden(() -> service.deletePostReply("free", 100L, 200L, 0, 10L, false));

		verifyNoInteractions(boardMapper, postMapper, postReplyMapper, memberMapper);
	}

	@Test
	@DisplayName("공식 답변 기능이 꺼지면 등록과 수정 및 삭제를 모두 거절한다")
	void rejectAllChangesWhenFeatureIsDisabled() {
		prepareManagementBoard(false);

		assertForbidden(() -> service.createPostReply("free", 100L, createRequest(), 10L, true));

		assertForbidden(() -> service.updatePostReply("free", 100L, 200L, updateRequest(), 10L, true));

		assertForbidden(() -> service.deletePostReply("free", 100L, 200L, 0, 10L, true));

		verifyNoInteractions(postMapper, postReplyMapper);
	}

	@Test
	@DisplayName("활성 회원이 아니면 관리자 권한이 있어도 답변을 등록할 수 없다")
	void rejectReplyCreationForUnavailableMember() {
		when(boardMapper.selectActiveBoardBySlugForShare("free")).thenReturn(Optional.of(board()));

		when(memberMapper.existsActiveMemberById(10L)).thenReturn(false);

		assertThrows(MemberUnavailableException.class,
			() -> service.createPostReply("free", 100L, createRequest(), 10L, true));

		verifyNoInteractions(postMapper, postReplyMapper);
		verify(boardMapper, never()).selectPolicyByBoardId(anyLong());
	}

	@Test
	@DisplayName("게시글이 없거나 삭제되었으면 공식 답변을 등록할 수 없다")
	void rejectReplyCreationWhenPostIsUnavailable() {
		prepareManagementBoard(true);

		when(postMapper.selectActiveCommentTargetForUpdate(1L, 100L)).thenReturn(Optional.empty());

		assertThrows(PostNotFoundException.class,
			() -> service.createPostReply("free", 100L, createRequest(), 10L, true));

		verifyNoInteractions(postReplyMapper);
	}

	@Test
	@DisplayName("공식 답변 본문 검증에 실패하면 저장하지 않는다")
	void rejectInvalidReplyBeforeInsertion() {
		prepareManagementBoard(true);
		prepareManageablePost();

		PostReplyCreateRequest request = new PostReplyCreateRequest(document("가".repeat(2001)));

		assertThrows(InvalidPostRequestException.class,
			() -> service.createPostReply("free", 100L, request, 10L, true));

		verifyNoInteractions(postReplyMapper);
	}

	@Test
	@DisplayName("관리자는 검증된 본문과 요청 버전으로 공식 답변을 수정한다")
	void updateReplyWithValidatedDocumentAndVersion() {
		prepareManagementBoard(true);
		prepareManageablePost();

		when(postReplyMapper.updatePostReply(1L, 100L, 200L, "공식 답변", document("공식 답변"), 0)).thenReturn(1);

		service.updatePostReply("free", 100L, 200L, updateRequest(), 10L, true);

		verify(postReplyMapper).updatePostReply(1L, 100L, 200L, "공식 답변", document("공식 답변"), 0);
	}

	@Test
	@DisplayName("공식 답변 수정 결과가 0건이면 충돌로 처리한다")
	void rejectReplyUpdateWhenTargetOrVersionHasChanged() {
		prepareManagementBoard(true);
		prepareManageablePost();

		when(postReplyMapper.updatePostReply(1L, 100L, 200L, "공식 답변", document("공식 답변"), 0)).thenReturn(0);

		assertThrows(PostReplyConflictException.class,
			() -> service.updatePostReply("free", 100L, 200L, updateRequest(), 10L, true));
	}

	@Test
	@DisplayName("관리자는 답변 ID와 버전을 지정해 공식 답변을 삭제한다")
	void deleteReplyWithIdAndVersion() {
		prepareManagementBoard(true);
		prepareManageablePost();

		when(postReplyMapper.deletePostReply(1L, 100L, 200L, 3)).thenReturn(1);

		service.deletePostReply("free", 100L, 200L, 3, 10L, true);

		verify(postReplyMapper).deletePostReply(1L, 100L, 200L, 3);
	}

	@Test
	@DisplayName("공식 답변 삭제 결과가 0건이면 충돌로 처리한다")
	void rejectReplyDeletionWhenTargetOrVersionHasChanged() {
		prepareManagementBoard(true);
		prepareManageablePost();

		when(postReplyMapper.deletePostReply(1L, 100L, 200L, 0)).thenReturn(0);

		assertThrows(PostReplyConflictException.class, () -> service.deletePostReply("free", 100L, 200L, 0, 10L, true));
	}

	@Test
	@DisplayName("DB 장애를 공식 답변 중복 충돌로 바꾸지 않는다")
	void propagateDatabaseFailureDuringReplyCreation() {
		prepareManagementBoard(true);
		prepareManageablePost();

		var failure = new DataAccessResourceFailureException("database unavailable");

		when(postReplyMapper.insertPostReply(any(PostReply.class))).thenThrow(failure);

		assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
			() -> service.createPostReply("free", 100L, createRequest(), 10L, true)));
	}

	@Test
	@DisplayName("관리자는 선택한 필터와 페이지로 공식 답변 관리 목록을 조회한다")
	void getAdminReplyTargetsWithFilterAndPagination() {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);
		when(postReplyMapper.countAdminPostReplyTargets(PostReplyFilter.ANSWERED)).thenReturn(11L);

		var post = new AdminPostReplySummaryResponse(100L, "free", "자유게시판", "게시글 제목", "작성자", false,
			LocalDateTime.of(2026, 10, 18, 12, 0), 200L, false);
		List<AdminPostReplySummaryResponse> posts = List.of(post);

		when(postReplyMapper.selectAdminPostReplyTargets(PostReplyFilter.ANSWERED, 10, 10L)).thenReturn(posts);

		AdminPostReplyPageResponse result = service.getAdminPostReplyTargets(PostReplyFilter.ANSWERED, 2, 10, 10L,
			true);

		assertAll(() -> assertEquals(posts, result.posts()), () -> assertEquals(2, result.page()),
			() -> assertEquals(10, result.size()), () -> assertEquals(11L, result.totalElements()),
			() -> assertEquals(2, result.totalPages()));

		verify(postReplyMapper).selectAdminPostReplyTargets(PostReplyFilter.ANSWERED, 10, 10L);
		verifyNoInteractions(boardMapper, postMapper);
		verify(memberMapper, never()).existsActiveSanctionByMemberId(anyLong());
	}

	@ParameterizedTest(name = "filter={0}, total={1}, page={2}")
	@CsvSource({"UNANSWERED, 0, 1, 0", "ALL, 10, 2, 1"})
	@DisplayName("공식 답변 관리 대상이 없거나 마지막 페이지를 넘으면 목록 쿼리 없이 빈 페이지를 반환한다")
	void returnEmptyAdminReplyPageWithoutListQuery(PostReplyFilter filter, long total, int page, int totalPages) {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);
		when(postReplyMapper.countAdminPostReplyTargets(filter)).thenReturn(total);

		AdminPostReplyPageResponse result = service.getAdminPostReplyTargets(filter, page, 10, 10L, true);

		assertAll(() -> assertTrue(result.posts().isEmpty()), () -> assertEquals(page, result.page()),
			() -> assertEquals(10, result.size()), () -> assertEquals(total, result.totalElements()),
			() -> assertEquals(totalPages, result.totalPages()));

		verify(postReplyMapper, never()).selectAdminPostReplyTargets(any(PostReplyFilter.class), anyInt(), anyLong());
	}

	@Test
	@DisplayName("관리자가 아니면 공식 답변 관리 목록을 조회할 수 없다")
	void rejectAdminReplyListByNonAdmin() {
		assertForbidden(() -> service.getAdminPostReplyTargets(PostReplyFilter.UNANSWERED, 1, 10, 10L, false));

		verifyNoInteractions(memberMapper, postReplyMapper, boardMapper, postMapper);
	}

	@Test
	@DisplayName("비활성 회원은 관리자 권한이 있어도 공식 답변 관리 목록을 조회할 수 없다")
	void rejectAdminReplyListForUnavailableMember() {
		when(memberMapper.existsActiveMemberById(10L)).thenReturn(false);

		assertThrows(MemberUnavailableException.class,
			() -> service.getAdminPostReplyTargets(PostReplyFilter.UNANSWERED, 1, 10, 10L, true));

		verifyNoInteractions(postReplyMapper, boardMapper, postMapper);
	}

	@ParameterizedTest
	@CsvSource(value = {"null", "10"}, nullValues = "null")
	@DisplayName("다른 사용자는 블라인드된 게시글의 공식 답변도 조회할 수 없다")
	void rejectReplyReadOfBlindedPost(Long memberId) {
		prepareReadBoard("PUBLIC", true, false);
		when(postMapper.selectActiveCommentTarget(1L, 100L)).thenReturn(
			Optional.of(new PostCommentTarget(20L, true, "hash", false, LocalDateTime.now())));

		assertThrows(BlindedContentAccessDeniedException.class,
			() -> service.getPostReply("free", 100L, memberId, false, "correct-password"));

		verifyNoInteractions(postReplyMapper, passwordEncoder);
	}

	@ParameterizedTest
	@CsvSource({"20, false", "10, true"})
	@DisplayName("게시글 작성자와 관리자는 블라인드된 게시글의 공식 답변을 조회할 수 있다")
	void allowReplyReadOfBlindedPostForAuthorOrAdmin(Long memberId, boolean adminAccess) {
		prepareReadBoard("PUBLIC", true, adminAccess);
		when(postMapper.selectActiveCommentTarget(1L, 100L)).thenReturn(
			Optional.of(new PostCommentTarget(20L, true, "hash", false, LocalDateTime.now())));

		var expected = replyResponse();
		when(postReplyMapper.selectPostReply(1L, 100L)).thenReturn(Optional.of(expected));

		assertEquals(Optional.of(expected), service.getPostReply("free", 100L, memberId, adminAccess, null));
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	@DisplayName("블라인드된 게시글에서는 관리자의 공식 답변 등록 수정 삭제를 차단한다")
	void rejectReplyChangesOnBlindedPost() {
		prepareManagementBoard(true);
		when(postMapper.selectActiveCommentTargetForUpdate(1L, 100L)).thenReturn(
			Optional.of(new PostCommentTarget(20L, false, null, false, LocalDateTime.now())));

		assertThrows(BlindedContentAccessDeniedException.class,
			() -> service.createPostReply("free", 100L, createRequest(), 10L, true));
		assertThrows(BlindedContentAccessDeniedException.class,
			() -> service.updatePostReply("free", 100L, 200L, updateRequest(), 10L, true));
		assertThrows(BlindedContentAccessDeniedException.class,
			() -> service.deletePostReply("free", 100L, 200L, 0, 10L, true));

		verifyNoInteractions(postReplyMapper);
	}

	private void prepareReadBoard(String readPermission, boolean enabled, boolean adminAccess) {
		when(boardMapper.selectReadableBoardBySlug("free", adminAccess)).thenReturn(Optional.of(board()));

		when(boardMapper.selectPolicyByBoardId(1L)).thenReturn(Optional.of(policy(readPermission, enabled)));
	}

	private void prepareManagementBoard(boolean enabled) {
		when(boardMapper.selectActiveBoardBySlugForShare("free")).thenReturn(Optional.of(board()));

		when(memberMapper.existsActiveMemberById(10L)).thenReturn(true);

		when(boardMapper.selectPolicyByBoardId(1L)).thenReturn(Optional.of(policy("PUBLIC", enabled)));
	}

	private void prepareReadablePost(boolean secret, String passwordHash) {
		when(postMapper.selectActiveCommentTarget(1L, 100L)).thenReturn(
			Optional.of(new PostCommentTarget(20L, secret, passwordHash, false, null)));
	}

	private void prepareManageablePost() {
		when(postMapper.selectActiveCommentTargetForUpdate(1L, 100L)).thenReturn(
			Optional.of(new PostCommentTarget(20L, true, "hash", true, null)));
	}

	private Board board() {
		return Board.builder().id(1L).slug("free").status("ACTIVE").build();
	}

	private BoardPolicy policy(String readPermission, boolean enabled) {
		return BoardPolicy.builder().boardId(1L).readPermission(readPermission).officialReplyEnabled(enabled).build();
	}

	private PostReplyCreateRequest createRequest() {
		return new PostReplyCreateRequest(document("공식 답변"));
	}

	private PostReplyUpdateRequest updateRequest() {
		return new PostReplyUpdateRequest(document("공식 답변"), 0);
	}

	private PostReplyResponse replyResponse() {
		LocalDateTime time = LocalDateTime.of(2026, 10, 18, 0, 0);

		return new PostReplyResponse(200L, 100L, 10L, "관리자", "공식 답변", document("공식 답변"), 0, time, time);
	}

	private JsonNode document(String text) {
		var root = JsonNodeFactory.instance.objectNode().put("type", "doc");
		var paragraph = root.putArray("content").addObject().put("type", "paragraph");

		paragraph.putArray("content").addObject().put("type", "text").put("text", text);

		return root;
	}

	private void assertForbidden(org.junit.jupiter.api.function.Executable action) {
		ResponseStatusException exception = assertThrows(ResponseStatusException.class, action);

		assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
	}
}
