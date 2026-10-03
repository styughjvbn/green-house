package com.greenhouse.backend.farm.dto.inbound;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.farm.domain.inbound.InboundRecord;
import com.greenhouse.backend.farm.domain.inbound.InboundRecordAction;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record InboundRecordResponse(Long id, LocalDate inboundDate, String inboundType, Long varietyId, String genus,
		String varietyName, String status, Integer estimatedQuantity, String tempLocation, LocalDate pottingDueDate,
		LocalDate pottingDate, boolean editable, List<InboundRecordAction> availableActions,
		List<InboundOrchidGroupResponse> createdOrchidGroups, String worker, String memo, LocalDateTime createdAt,
		LocalDateTime updatedAt) {

	public static InboundRecordResponse from(InboundRecord record, List<OrchidGroup> createdOrchidGroups,
			LocalDate pottingDate, boolean hasUndoablePotting) {
		return new InboundRecordResponse(record.getId(), record.getInboundDate(), record.getInboundType().name(),
				record.getVariety().getId(), record.getVariety().getGenus(), record.getVariety().getName(),
				record.getStatus().name(), record.getEstimatedQuantity(), record.getTempLocation(),
				record.getPottingDueDate(), pottingDate, record.isEditable(),
				record.availableActions(!createdOrchidGroups.isEmpty(), hasUndoablePotting),
				createdOrchidGroups.stream().map(InboundOrchidGroupResponse::from).toList(), record.getWorker(),
				record.getMemo(), TimeConfig.toFarmTime(record.getCreatedAt()),
				TimeConfig.toFarmTime(record.getUpdatedAt()));
	}
}
