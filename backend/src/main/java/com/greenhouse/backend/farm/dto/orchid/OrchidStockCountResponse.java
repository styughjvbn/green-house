package com.greenhouse.backend.farm.dto.orchid;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.farm.domain.orchid.OrchidStockCount;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record OrchidStockCountResponse(String idempotencyKey, Long orchidGroupId, LocalDateTime recordedAt,
		LocalDate countedDate, int beforeQuantity, int actualQuantity, int difference, String reason, String worker,
		String memo, Long mutationId) {
	public static OrchidStockCountResponse from(OrchidStockCount event) {
		return new OrchidStockCountResponse(event.getRequestKey(), event.getOrchidGroupId(),
				TimeConfig.toFarmTime(event.getRecordedAt()), event.getBusinessDate(), event.getBeforeQuantity(),
				event.getActualQuantity(), event.getActualQuantity() - event.getBeforeQuantity(), event.getReason(),
				event.getWorker(), event.getMemo(), event.getMutationId());
	}
}
