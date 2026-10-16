package com.greenhouse.backend.ai.slip.application.port.out;

import com.greenhouse.backend.ai.slip.application.SlipConversationInput;
import com.greenhouse.backend.ai.slip.application.SlipConversationModelResult;
import com.greenhouse.backend.ai.slip.application.SlipModelResult;
import java.util.List;

public interface AgentRuntimePort {
  SlipModelResult extract(String requestId, String toolToken, String contentType, byte[] image);

  void openSession(String sessionId, List<String> varietyNames);

  void closeSession(String sessionId);

  void cancelRequest(String sessionId, String requestId);

  SlipConversationModelResult extractConversation(
      String requestId, String sessionId, String toolToken, SlipConversationInput input);
}
