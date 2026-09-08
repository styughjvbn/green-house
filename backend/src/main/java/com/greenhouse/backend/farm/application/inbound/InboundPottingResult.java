package com.greenhouse.backend.farm.application.inbound;

import com.greenhouse.backend.farm.dto.inbound.InboundRecordResponse;
import com.greenhouse.backend.work.application.effect.WorkMutationLink;
import java.util.List;

public record InboundPottingResult(InboundRecordResponse inboundRecord, List<Long> createdOrchidGroupIds,
		int actualQuantity, WorkMutationLink mutationLink) {
}
