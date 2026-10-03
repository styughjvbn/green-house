package com.greenhouse.backend.farm.controller.orchid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationGraphQueryService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class OrchidGroupMutationQueryControllerConditionTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(
              OrchidGroupMutationQueryService.class,
              () -> mock(OrchidGroupMutationQueryService.class))
          .withBean(
              OrchidGroupMutationGraphQueryService.class,
              () -> mock(OrchidGroupMutationGraphQueryService.class))
          .withUserConfiguration(TestConfiguration.class);

  @Test
  void registersControllerOnlyInDevEnvironment() {
    contextRunner
        .withPropertyValues("app.environment=dev")
        .run(
            context -> assertThat(context).hasSingleBean(OrchidGroupMutationQueryController.class));
  }

  @Test
  void doesNotRegisterControllerInProdOrWithoutEnvironment() {
    contextRunner
        .withPropertyValues("app.environment=prod")
        .run(
            context ->
                assertThat(context).doesNotHaveBean(OrchidGroupMutationQueryController.class));
    contextRunner.run(
        context -> assertThat(context).doesNotHaveBean(OrchidGroupMutationQueryController.class));
  }

  @Configuration(proxyBeanMethods = false)
  @Import(OrchidGroupMutationQueryController.class)
  static class TestConfiguration {}
}
