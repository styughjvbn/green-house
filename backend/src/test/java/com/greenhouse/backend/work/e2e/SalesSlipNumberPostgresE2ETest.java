package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.sales.repository.document.SalesSlipNumberRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("work-e2e")
class SalesSlipNumberPostgresE2ETest extends WorkE2ETestBase {

  @Autowired SalesSlipNumberRepository repository;

  @Test
  void allocatesUniqueDailyNumbersConcurrentlyOnPostgres() throws Exception {
    LocalDate saleDate = LocalDate.of(2040, 1, 2);
    var executor = Executors.newFixedThreadPool(8);
    try {
      var tasks = new ArrayList<Callable<Long>>();
      for (int index = 0; index < 8; index++) {
        tasks.add(() -> repository.nextDailySequence(saleDate));
      }
      List<Long> values =
          executor.invokeAll(tasks, 60, TimeUnit.SECONDS).stream()
              .map(
                  future -> {
                    try {
                      assertThat(future.isCancelled())
                          .as("Daily sequence allocation exceeded 60 seconds")
                          .isFalse();
                      return future.get(1, TimeUnit.SECONDS);
                    } catch (Exception exception) {
                      throw new AssertionError(exception);
                    }
                  })
              .sorted()
              .toList();

      assertThat(values).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
          .as("Daily sequence workers terminated")
          .isTrue();
    }
  }
}
