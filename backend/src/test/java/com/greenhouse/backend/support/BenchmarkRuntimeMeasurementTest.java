package com.greenhouse.backend.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BenchmarkRuntimeMeasurementTest {
  @Test
  void recordsAllocationHeapAndGcAndTerminatesItsSampler() {
    var measurement = new BenchmarkRuntimeMeasurement();
    var retained = new byte[1024 * 1024];
    Arrays.fill(retained, (byte) 7);
    var sample = measurement.stop();
    assertThat(sample.sampledPeakHeapUsedBytes())
        .isGreaterThanOrEqualTo(sample.heapUsedBeforeBytes());
    assertThat(sample.sampledPeakHeapUsedBytes())
        .isGreaterThanOrEqualTo(sample.heapUsedAfterBytes());
    assertThat(sample.heapSamples()).isGreaterThanOrEqualTo(2);
    assertThat(sample.gc()).isNotEmpty();
    if (sample.measuredThreadAllocatedBytes() != null)
      assertThat(sample.measuredThreadAllocatedBytes()).isGreaterThanOrEqualTo(retained.length);
    assertThat(Thread.getAllStackTraces().keySet())
        .noneMatch(
            thread -> thread.isAlive() && thread.getName().equals("domain-benchmark-heap-sampler"));
    assertThat(retained[retained.length - 1]).isEqualTo((byte) 7);
  }
}
