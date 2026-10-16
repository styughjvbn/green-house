package com.greenhouse.backend.sales.document.integration.image;

import java.time.Duration;
import kr.co.slip.api.ExecutionSettings;
import kr.co.slip.api.ManagedSlipImageProcessor;
import kr.co.slip.opencv.SlipProcessors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SlipImageConfiguration {
  @Bean(destroyMethod = "close")
  public ManagedSlipImageProcessor slipImageProcessor(
      @Value("${sales.document.image.workers:2}") int workers,
      @Value("${sales.document.image.queue-capacity:4}") int queueCapacity,
      @Value("${sales.document.image.opencv-threads:1}") int openCvThreads) {
    return SlipProcessors.bounded(
        new ExecutionSettings(workers, queueCapacity, Duration.ofSeconds(30)), openCvThreads);
  }
}
