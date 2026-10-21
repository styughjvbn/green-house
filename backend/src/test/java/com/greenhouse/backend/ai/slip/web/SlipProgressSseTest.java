package com.greenhouse.backend.ai.slip.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.greenhouse.backend.ai.slip.application.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SlipProgressSseTest {
  @Test
  void eventsAreFlushedWithOwnedSnapshotsAndCompletionWithoutCancellingExtraction()
      throws Exception {
    var service = mock(SlipConversationService.class);
    var channel =
        new SlipProgressChannel(
            new SlipConversationProgress(
                "r", SlipConversationProgress.Stage.READING, List.of("p"), null));
    when(service.subscribeProgress("worker", "session", "r")).thenReturn(channel.subscribe());
    var writer = new SlipProgressSse();
    try {
      var mvc =
          MockMvcBuilders.standaloneSetup(new SlipConversationController(service, writer)).build();
      var result =
          mvc.perform(
                  get("/api/ai/slips/sessions/session/requests/r/events").principal(() -> "worker"))
              .andExpect(request().asyncStarted())
              .andExpect(header().string("X-Accel-Buffering", "no"))
              .andReturn();
      channel.finish(
          new SlipConversationProgress(
              "r", SlipConversationProgress.Stage.DONE, List.of("p"), null));
      result.getAsyncResult(5000);
      mvc.perform(asyncDispatch(result))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith("text/event-stream"));
      assertThat(result.getResponse().getContentAsString())
          .contains("event:progress", "\"requestId\":\"r\"", "\"stage\":\"DONE\"");
      verify(service).subscribeProgress("worker", "session", "r");
      verifyNoMoreInteractions(service);
    } finally {
      writer.close();
    }
  }
}
