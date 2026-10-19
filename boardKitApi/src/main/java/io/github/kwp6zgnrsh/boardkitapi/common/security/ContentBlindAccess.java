package io.github.kwp6zgnrsh.boardkitapi.common.security;

import java.time.LocalDateTime;

import io.github.kwp6zgnrsh.boardkitapi.common.exception.BlindedContentAccessDeniedException;

public final class ContentBlindAccess {

	private ContentBlindAccess() {
	}

	public static void validateRead(LocalDateTime blindedAt, Long authorId, Long memberId, boolean adminAccess) {
		if (blindedAt == null || adminAccess || (memberId != null && memberId.equals(authorId))) {
			return;
		}

		throw new BlindedContentAccessDeniedException("블라인드된 내용은 작성자와 관리자만 확인할 수 있습니다.");
	}

	public static void validateWrite(LocalDateTime blindedAt) {
		if (blindedAt != null) {
			throw new BlindedContentAccessDeniedException("블라인드된 내용에는 이 작업을 수행할 수 없습니다.");
		}
	}
}