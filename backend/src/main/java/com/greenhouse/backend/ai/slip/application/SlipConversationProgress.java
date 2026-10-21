package com.greenhouse.backend.ai.slip.application;

import com.greenhouse.backend.ai.tool.application.AgentToolGateway;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "AiSlipConversationProgress")
public record SlipConversationProgress(
    String requestId, Stage stage, List<String> photoIds, AgentToolGateway.Activity activity) {
  public enum Stage {
    WAITING,
    READING,
    VALIDATING,
    DONE
  }
}
