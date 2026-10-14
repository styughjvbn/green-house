package com.greenhouse.backend.work.api.operation;

import com.greenhouse.backend.work.api.effect.InboundPottingCommand;

/** Farm-facing potting execution and undo; plan and receipt implementation stays in Work. */
public interface InboundPottingOperationApi {

  WorkOperationView executeNow(InboundPottingCommand request);

  void voidForInbound(Long inboundRecordId, String idempotencyKey, String reason);
}
