package com.greenhouse.backend.work.application.operation;

/**
 * Applies the inbound validation, potting compensation and inbound audit in the caller transaction.
 */
public interface InboundPottingVoidPort {

  Long voidPotting(Long inboundRecordId, String requestKey, String reason);
}
