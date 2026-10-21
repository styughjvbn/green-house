package com.greenhouse.backend.ai.slip.web;

import com.greenhouse.backend.ai.api.AgentException;
import com.greenhouse.backend.ai.slip.application.SlipProgressChannel;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SlipProgressSse {
  private final ExecutorService writers = Executors.newVirtualThreadPerTaskExecutor();
  private final Semaphore connections = new Semaphore(32);

  public SseEmitter open(SlipProgressChannel.Subscription subscription) {
    if (!connections.tryAcquire()) {
      subscription.close();
      throw new AgentException(429, "AI_PROGRESS_LIMIT", "진행 상태 연결 수를 초과했습니다.");
    }
    var emitter = new SseEmitter(600_000L);
    emitter.onCompletion(subscription::close);
    emitter.onTimeout(subscription::close);
    emitter.onError(error -> subscription.close());
    try {
      writers.execute(
          () -> {
            try (subscription) {
              while (!subscription.ended()) {
                var progress = subscription.next(Duration.ofSeconds(15));
                if (progress != null)
                  emitter.send(SseEmitter.event().name("progress").data(progress));
                else if (!subscription.ended())
                  emitter.send(SseEmitter.event().comment("keepalive"));
              }
              emitter.complete();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              emitter.complete();
            } catch (IOException | IllegalStateException e) {
              // A disconnected viewer does not own or cancel the extraction request.
            } finally {
              connections.release();
            }
          });
    } catch (RuntimeException error) {
      subscription.close();
      connections.release();
      throw error;
    }
    return emitter;
  }

  @PreDestroy
  public void close() {
    writers.shutdownNow();
  }
}
