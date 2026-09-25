package io.github.kwp6zgnrsh.boardkitapi.board.policy;

import io.github.kwp6zgnrsh.boardkitapi.board.exception.BoardAccessDeniedException;

public final class BoardReadAccess {

	private BoardReadAccess() {
	}

	public static boolean isAllowed(String readPermission, boolean memberAccess, boolean adminAccess) {
		return switch (readPermission) {
			case "PUBLIC" -> true;
			case "MEMBER" -> memberAccess || adminAccess;
			case "ADMIN" -> adminAccess;
			default -> throw new IllegalStateException(
				"지원하지 않는 읽기 권한입니다: " + readPermission
			);
		};
	}

	public static void validate(String readPermission, boolean memberAccess, boolean adminAccess) {
		if (!isAllowed(readPermission, memberAccess, adminAccess)) {
			throw new BoardAccessDeniedException();
		}
	}
}