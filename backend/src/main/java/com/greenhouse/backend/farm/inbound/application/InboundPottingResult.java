package com.greenhouse.backend.farm.inbound.application;

import com.greenhouse.backend.farm.inbound.web.dto.InboundRecordResponse;
import com.greenhouse.backend.work.api.effect.WorkMutationLink;
import java.util.List;

public record InboundPottingResult(
    InboundRecordResponse inboundRecord,
    List<Long> createdOrchidGroupIds,
    int actualQuantity,
    WorkMutationLink mutationLink) {}
