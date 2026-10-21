package com.greenhouse.backend.ai.slip.application;

import static org.assertj.core.api.Assertions.*;

import com.greenhouse.backend.ai.api.AgentException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlipProgressChannelTest {
  private SlipConversationProgress state(SlipConversationProgress.Stage stage) {
    return new SlipConversationProgress("r", stage, List.of("photo"), null);
  }

  @Test
  void subscribersReceiveSnapshotsReconnectAtLatestAndDoNotOwnTheRequest() throws Exception {
    var channel = new SlipProgressChannel(state(SlipConversationProgress.Stage.WAITING));
    try (var subscription = channel.subscribe()) {
      assertThat(subscription.next(Duration.ZERO).stage())
          .isEqualTo(SlipConversationProgress.Stage.WAITING);
      channel.publish(state(SlipConversationProgress.Stage.READING));
      assertThat(subscription.next(Duration.ZERO).stage())
          .isEqualTo(SlipConversationProgress.Stage.READING);
    }
    channel.publish(state(SlipConversationProgress.Stage.VALIDATING));
    try (var reconnect = channel.subscribe()) {
      assertThat(reconnect.next(Duration.ZERO).stage())
          .isEqualTo(SlipConversationProgress.Stage.VALIDATING);
      channel.finish(state(SlipConversationProgress.Stage.DONE));
      assertThat(reconnect.next(Duration.ZERO).stage())
          .isEqualTo(SlipConversationProgress.Stage.DONE);
      assertThat(reconnect.ended()).isTrue();
    }
    try (var reconnect = channel.subscribe()) {
      assertThat(reconnect.next(Duration.ZERO).stage())
          .isEqualTo(SlipConversationProgress.Stage.DONE);
      assertThat(reconnect.ended()).isTrue();
    }
  }

  @Test
  void slowReadersHaveABoundedBufferAndConnectionsAreReleased() throws Exception {
    var channel = new SlipProgressChannel(state(SlipConversationProgress.Stage.READING));
    try (var first = channel.subscribe();
        var second = channel.subscribe()) {
      assertThatThrownBy(channel::subscribe).isInstanceOf(AgentException.class);
      for (int i = 0; i < 1000; i++) channel.publish(state(SlipConversationProgress.Stage.READING));
      channel.finish(state(SlipConversationProgress.Stage.DONE));
      int count = 0;
      SlipConversationProgress latest = null;
      while (!first.ended()) {
        latest = first.next(Duration.ZERO);
        count++;
      }
      assertThat(count).isEqualTo(32);
      assertThat(latest.stage()).isEqualTo(SlipConversationProgress.Stage.DONE);
    }
    try (var reconnect = channel.subscribe()) {
      assertThat(reconnect.next(Duration.ZERO)).isNotNull();
    }
  }
}
