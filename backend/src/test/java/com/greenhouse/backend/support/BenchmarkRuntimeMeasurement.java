package com.greenhouse.backend.support;

import com.sun.management.ThreadMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Whole-JVM sampled heap/GC and measured-thread allocation, excluding fixture preparation. */
public final class BenchmarkRuntimeMeasurement implements AutoCloseable {
  private final ScheduledExecutorService sampler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            var thread = new Thread(r, "domain-benchmark-heap-sampler");
            thread.setDaemon(true);
            return thread;
          });
  private final AtomicLong peak = new AtomicLong();
  private final AtomicLong samples = new AtomicLong();
  private final long beforeHeap = heap();
  private final long thread = Thread.currentThread().threadId();
  private final Long beforeAllocation = allocation(thread);
  private final List<GcValue> beforeGc = gc();
  private boolean closed;

  public BenchmarkRuntimeMeasurement() {
    sample();
    sampler.scheduleAtFixedRate(this::sample, 10, 10, TimeUnit.MILLISECONDS);
  }

  public Sample stop() {
    close();
    long afterHeap = heap();
    Long afterAllocation = allocation(thread);
    var afterGc = gc();
    return new Sample(
        beforeHeap,
        afterHeap,
        Math.max(peak.get(), afterHeap),
        samples.get(),
        10,
        beforeAllocation == null || afterAllocation == null
            ? null
            : afterAllocation - beforeAllocation,
        beforeGc.stream()
            .map(
                before -> {
                  var after =
                      afterGc.stream()
                          .filter(g -> g.name().equals(before.name()))
                          .findFirst()
                          .orElseThrow();
                  return new GcValue(
                      before.name(),
                      delta(before.collections(), after.collections()),
                      delta(before.collectionMillis(), after.collectionMillis()));
                })
            .toList());
  }

  private void sample() {
    peak.accumulateAndGet(heap(), Math::max);
    samples.incrementAndGet();
  }

  private static long heap() {
    return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
  }

  private static Long allocation(long thread) {
    var bean = ManagementFactory.getThreadMXBean();
    if (bean instanceof ThreadMXBean allocation && allocation.isThreadAllocatedMemorySupported()) {
      if (!allocation.isThreadAllocatedMemoryEnabled())
        allocation.setThreadAllocatedMemoryEnabled(true);
      long value = allocation.getThreadAllocatedBytes(thread);
      return value < 0 ? null : value;
    }
    return null;
  }

  private static List<GcValue> gc() {
    return ManagementFactory.getGarbageCollectorMXBeans().stream()
        .map(BenchmarkRuntimeMeasurement::gc)
        .toList();
  }

  private static GcValue gc(GarbageCollectorMXBean bean) {
    return new GcValue(bean.getName(), bean.getCollectionCount(), bean.getCollectionTime());
  }

  private static long delta(long before, long after) {
    return before < 0 || after < 0 ? -1 : after - before;
  }

  public record GcValue(String name, long collections, long collectionMillis) {}

  public record Sample(
      long heapUsedBeforeBytes,
      long heapUsedAfterBytes,
      long sampledPeakHeapUsedBytes,
      long heapSamples,
      long heapSamplingIntervalMillis,
      Long measuredThreadAllocatedBytes,
      List<GcValue> gc) {}

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    sample();
    sampler.shutdownNow();
    try {
      if (!sampler.awaitTermination(2, TimeUnit.SECONDS))
        throw new IllegalStateException("Heap sampler did not stop");
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Heap sampler interrupted", failure);
    }
  }
}
