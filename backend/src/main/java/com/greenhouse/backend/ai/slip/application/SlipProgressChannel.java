package com.greenhouse.backend.ai.slip.application;

import com.greenhouse.backend.ai.api.AgentException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** One request's latest snapshot and bounded live subscriptions; no replay log or job ownership. */
public final class SlipProgressChannel {
  private SlipConversationProgress latest;
  private boolean finished;
  private final Set<Subscription> subscriptions = new HashSet<>();

  public SlipProgressChannel(SlipConversationProgress initial) {
    latest = initial;
  }

  public synchronized SlipConversationProgress latest() {
    return latest;
  }

  public synchronized void publish(SlipConversationProgress value) {
    if (finished) return;
    latest = value;
    subscriptions.forEach(subscription -> subscription.offer(value));
  }

  public synchronized void finish(SlipConversationProgress value) {
    publish(value);
    finished = true;
  }

  public synchronized Subscription subscribe() {
    if (subscriptions.size() >= 2)
      throw new AgentException(429, "AI_PROGRESS_LIMIT", "진행 상태 연결 수를 초과했습니다.");
    var subscription = new Subscription();
    subscriptions.add(subscription);
    subscription.offer(latest);
    return subscription;
  }

  public final class Subscription implements AutoCloseable {
    private final ArrayBlockingQueue<SlipConversationProgress> queue = new ArrayBlockingQueue<>(32);
    private volatile boolean closed;

    private void offer(SlipConversationProgress value) {
      // Slow clients receive the newest snapshot without blocking inference or growing memory.
      if (!queue.offer(value)) {
        queue.poll();
        queue.offer(value);
      }
    }

    public SlipConversationProgress next(Duration timeout) throws InterruptedException {
      return closed ? null : queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public boolean ended() {
      synchronized (SlipProgressChannel.this) {
        return closed || (finished && queue.isEmpty());
      }
    }

    @Override
    public void close() {
      synchronized (SlipProgressChannel.this) {
        closed = true;
        subscriptions.remove(this);
        queue.clear();
      }
    }
  }
}
