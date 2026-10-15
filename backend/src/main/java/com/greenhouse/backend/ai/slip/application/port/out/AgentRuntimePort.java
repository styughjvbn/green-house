package com.greenhouse.backend.ai.slip.application.port.out;

import com.greenhouse.backend.ai.slip.application.SlipConversationInput;
import com.greenhouse.backend.ai.slip.application.SlipConversationModelResult;
import com.greenhouse.backend.ai.slip.application.SlipModelResult;

public interface AgentRuntimePort {
  SlipModelResult extract(String requestId, String toolToken, String contentType, byte[] image);

  void openSession(String sessionId);

  void closeSession(String sessionId);

  SlipConversationModelResult extractConversation(
      String requestId, String sessionId, String toolToken, SlipConversationInput input);
}
