package com.greenhouse.backend.farm.application.orchid.mutation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrchidGroupShadowComparisonListener {

	private final OrchidGroupShadowComparisonWriter writer;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(OrchidGroupShadowComparisonEvent event) {
		try {
			writer.save(event);
		} catch (RuntimeException exception) {
			log.error("OrchidGroup shadow 비교 저장에 실패했습니다. Legacy 결과는 유지됩니다.", exception);
		}
	}
}
