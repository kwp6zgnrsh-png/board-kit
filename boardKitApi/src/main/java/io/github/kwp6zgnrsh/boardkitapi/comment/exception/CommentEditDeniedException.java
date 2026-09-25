package io.github.kwp6zgnrsh.boardkitapi.comment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class CommentEditDeniedException extends RuntimeException {

  public CommentEditDeniedException() {
    super("댓글을 수정할 권한이 없습니다.");
  }
}