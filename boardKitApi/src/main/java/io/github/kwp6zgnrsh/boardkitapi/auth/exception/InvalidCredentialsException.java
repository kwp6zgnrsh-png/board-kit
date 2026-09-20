package io.github.kwp6zgnrsh.boardkitapi.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("아이디 또는 비밀번호가 올바르지 않습니다.");
  }
}