package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSourceDomain;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class OrchidGroupMutationSources {

  private OrchidGroupMutationSources() {}

  public static OrchidGroupMutationSource farmRequest(
      String sourceType, String referenceId, String operation) {
    UUID correlationId = UUID.randomUUID();
    return new OrchidGroupMutationSource(
        OrchidGroupMutationSourceDomain.FARM,
        sourceType,
        referenceId,
        operation + ":" + correlationId,
        correlationId);
  }

  public static OrchidGroupMutationSource farmBatch(
      String sourceType, String referenceId, String operation, UUID correlationId) {
    return new OrchidGroupMutationSource(
        OrchidGroupMutationSourceDomain.FARM, sourceType, referenceId, operation, correlationId);
  }

  public static OrchidGroupMutationSource inbound(Long inboundRecordId, String operationKey) {
    return stable(
        OrchidGroupMutationSourceDomain.INBOUND,
        "INBOUND_RECORD",
        inboundRecordId.toString(),
        operationKey,
        "INBOUND_RECORD:" + inboundRecordId);
  }

  public static OrchidGroupMutationSource work(Long workOperationId, String effectKey) {
    return stable(
        OrchidGroupMutationSourceDomain.WORK,
        "WORK_EFFECT",
        workOperationId.toString(),
        effectKey,
        "WORK_OPERATION:" + workOperationId);
  }

  public static OrchidGroupMutationSource workCorrection(Long correctionId) {
    return stable(
        OrchidGroupMutationSourceDomain.WORK,
        "WORK_CORRECTION",
        correctionId.toString(),
        "CORRECTION",
        "WORK_CORRECTION:" + correctionId);
  }

  public static OrchidGroupMutationSource sales(Long salesSlipId, String operationKey) {
    return stable(
        OrchidGroupMutationSourceDomain.SALES,
        "SALES_SLIP",
        salesSlipId.toString(),
        operationKey,
        "SALES_SLIP:" + salesSlipId);
  }

  public static OrchidGroupMutationSource auctionReturnArrival(
      Long arrivalId, String operationKey) {
    return stable(
        OrchidGroupMutationSourceDomain.SALES,
        "AUCTION_RETURN_ARRIVAL",
        arrivalId.toString(),
        operationKey,
        "AUCTION_RETURN_ARRIVAL:" + arrivalId);
  }

  private static OrchidGroupMutationSource stable(
      OrchidGroupMutationSourceDomain domain,
      String sourceType,
      String referenceId,
      String operationKey,
      String correlationSeed) {
    return new OrchidGroupMutationSource(
        domain,
        sourceType,
        referenceId,
        operationKey,
        UUID.nameUUIDFromBytes(correlationSeed.getBytes(StandardCharsets.UTF_8)));
  }
}
