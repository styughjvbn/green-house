package com.greenhouse.backend.farm.application.orchid.mutation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * ORCHID-CUTOVER: TRANSITION_ONLY — 전환 기간의 writer mode 설정을 활성화한다.
 * Removal gate: 모든 환경의 Engine 단일 writer 고정.
 */
@Configuration
@EnableConfigurationProperties(OrchidGroupLedgerWriterProperties.class)
public class OrchidGroupLedgerWriterConfiguration {
}
