package com.greenhouse.backend.sales.document.integration.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import kr.co.slip.api.ManagedSlipImageProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class SlipImageConfigurationTest {
  @Test
  void defaultsToThreeWorkers() {
    try (var context = context(Map.of())) {
      assertThat(context.getBean(ManagedSlipImageProcessor.class).status().workers()).isEqualTo(3);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 4})
  void environmentVariableConfiguresWorkerCount(int workers) {
    try (var context = context(Map.of("SALES_DOCUMENT_IMAGE_WORKERS", String.valueOf(workers)))) {
      assertThat(context.getBean(ManagedSlipImageProcessor.class).status().workers())
          .isEqualTo(workers);
    }
  }

  private AnnotationConfigApplicationContext context(Map<String, Object> variables) {
    var context = new AnnotationConfigApplicationContext();
    context.getEnvironment().getPropertySources().remove("systemEnvironment");
    context.getEnvironment().getPropertySources().remove("systemProperties");
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(new SystemEnvironmentPropertySource("image-test-environment", variables));
    context.register(SlipImageConfiguration.class);
    context.refresh();
    return context;
  }
}
