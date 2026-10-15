package com.greenhouse.backend.ai.api;

public class AgentException extends RuntimeException {
  private final String code;
  private final int status;

  public AgentException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public String code() {
    return code;
  }

  public int status() {
    return status;
  }
}
