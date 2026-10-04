package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/** Frozen legacy v1 JSON shape. Nested application records are projected before hashing. */
@Component
public class OrchidGroupMutationCommandFingerprint {

  private final OrchidGroupMutationFingerprint fingerprint;

  public OrchidGroupMutationCommandFingerprint(OrchidGroupMutationFingerprint fingerprint) {
    this.fingerprint = fingerprint;
  }

  public String calculate(OrchidGroupMutationCommand command) {
    return switch (command) {
      case CreateOrchidGroupMutationCommand value ->
          fingerprint.calculate(
              new CreatePayload(
                  OrchidGroupMutationType.CREATE,
                  value.bedZoneId(),
                  DetailsV1.from(value.details()),
                  value.effectiveBusinessDate(),
                  value.reason()));
      case CreateInboundOrchidGroupsMutationCommand value ->
          fingerprint.calculate(
              new CreateInboundPayload(
                  OrchidGroupMutationType.CREATE,
                  value.inboundRecordId(),
                  value.groups().stream().map(PlacedDetailsV1::from).toList(),
                  value.effectiveBusinessDate(),
                  value.reason()));
      case TransformOrchidGroupsMutationCommand value ->
          fingerprint.calculate(
              new TransformPayload(
                  OrchidGroupMutationType.TRANSFORM,
                  value.sources().stream().map(TransformSourceV1::from).toList(),
                  value.results().stream().map(PlacedDetailsV1::from).toList(),
                  value.effectiveBusinessDate(),
                  value.reason(),
                  new TreeSet<>(value.placementExclusionOrchidGroupIds())));
      case UpdateOrchidGroupMutationCommand value ->
          fingerprint.calculate(
              new UpdatePayload(
                  OrchidGroupMutationType.UPDATE_DETAILS,
                  value.orchidGroupId(),
                  DetailsV1.from(value.details()),
                  value.effectiveBusinessDate(),
                  value.reason()));
      case MoveOrchidGroupMutationCommand value ->
          fingerprint.calculate(
              new MovePayload(
                  OrchidGroupMutationType.MOVE,
                  value.orchidGroupId(),
                  value.toBedZoneId(),
                  value.startPosition(),
                  value.endPosition(),
                  value.effectiveBusinessDate(),
                  value.reason()));
      case MoveOrchidGroupsMutationCommand value ->
          fingerprint.calculate(
              new BatchMovePayload(
                  OrchidGroupMutationType.MOVE,
                  value.items().stream().map(MoveItemV1::from).toList(),
                  value.effectiveBusinessDate(),
                  value.reason(),
                  new TreeSet<>(value.placementExclusionOrchidGroupIds())));
      case CancelOrchidGroupCreationMutationCommand value ->
          value.correctedMutations() == null
              ? fingerprint.calculate(
                  new CancelCreationPayload(
                      OrchidGroupMutationType.CANCEL_CREATION,
                      value.orchidGroupId(),
                      value.effectiveBusinessDate(),
                      value.reason()))
              : fingerprint.calculate(
                  new CorrectedCancelCreationPayload(
                      OrchidGroupMutationType.CANCEL_CREATION,
                      value.orchidGroupId(),
                      RelatedMutationsV1.from(value.correctedMutations()),
                      value.effectiveBusinessDate(),
                      value.reason()));
      case DiscardOrchidGroupMutationCommand value ->
          fingerprint.calculate(
              new DiscardPayload(
                  OrchidGroupMutationType.DISCARD,
                  value.orchidGroupId(),
                  value.quantity(),
                  value.effectiveBusinessDate(),
                  value.reason()));
      case ReserveOrchidGroupsMutationCommand value ->
          quantity(
              OrchidGroupMutationType.RESERVE,
              value.items(),
              null,
              value.effectiveBusinessDate(),
              value.reason());
      case ReleaseOrchidGroupReservationsMutationCommand value ->
          quantity(
              OrchidGroupMutationType.RELEASE_RESERVATION,
              value.items(),
              null,
              value.effectiveBusinessDate(),
              value.reason());
      case ConsumeOrchidGroupReservationsMutationCommand value ->
          quantity(
              OrchidGroupMutationType.CONSUME_RESERVATION,
              value.items(),
              null,
              value.effectiveBusinessDate(),
              value.reason());
      case RestoreOutboundOrchidGroupsMutationCommand value ->
          quantity(
              OrchidGroupMutationType.RESTORE_OUTBOUND,
              value.items(),
              value.compensatedMutations(),
              value.effectiveBusinessDate(),
              value.reason());
      case CorrectOrchidGroupsMutationCommand value ->
          fingerprint.calculate(
              new CorrectionPayload(
                  OrchidGroupMutationType.CORRECTION,
                  value.items().stream().map(CorrectionItemV1::from).toList(),
                  RelatedMutationsV1.from(value.correctedMutations()),
                  value.effectiveBusinessDate(),
                  value.reason()));
      case ReconcileOrchidGroupMutationCommand value ->
          fingerprint.calculate(
              new ReconciliationPayload(
                  OrchidGroupMutationType.RECONCILIATION,
                  value.orchidGroupId(),
                  value.actualQuantity(),
                  value.actualStatus(),
                  value.actualBedZoneId(),
                  value.actualStartPosition(),
                  value.actualEndPosition(),
                  value.effectiveBusinessDate(),
                  value.reason()));
      case StockCountOrchidGroupMutationCommand value ->
          fingerprint.calculate(
              new StockCountPayload(
                  OrchidGroupMutationType.RECONCILIATION,
                  value.orchidGroupId(),
                  value.expectedRevision(),
                  value.actualQuantity(),
                  value.effectiveBusinessDate(),
                  value.reason()));
      case CompensateTransformMutationsCommand value ->
          value.creationCancellationOrchidGroupIds().isEmpty()
              ? fingerprint.calculate(
                  new CompensationPayload(
                      OrchidGroupMutationType.COMPENSATION,
                      value.mutationIds(),
                      value.effectiveBusinessDate(),
                      value.reason()))
              : fingerprint.calculate(
                  new CompensationWithCreationCancellationPayload(
                      OrchidGroupMutationType.COMPENSATION,
                      value.mutationIds(),
                      value.creationCancellationOrchidGroupIds().stream().sorted().toList(),
                      value.effectiveBusinessDate(),
                      value.reason()));
      case CompensateCreateMutationsCommand value ->
          fingerprint.calculate(
              new CompensationPayload(
                  OrchidGroupMutationType.COMPENSATION,
                  value.mutationIds(),
                  value.effectiveBusinessDate(),
                  value.reason()));
    };
  }

  private String quantity(
      OrchidGroupMutationType mutationType,
      List<OrchidGroupQuantityMutationItem> items,
      RelatedOrchidGroupMutations relatedMutations,
      LocalDate effectiveBusinessDate,
      String reason) {
    return fingerprint.calculate(
        new QuantityPayload(
            mutationType,
            items.stream().map(QuantityItemV1::from).toList(),
            RelatedMutationsV1.from(relatedMutations),
            effectiveBusinessDate,
            reason));
  }

  private record CreatePayload(
      OrchidGroupMutationType mutationType,
      Long bedZoneId,
      DetailsV1 details,
      LocalDate effectiveBusinessDate,
      String reason) {}

  private record CreateInboundPayload(
      OrchidGroupMutationType mutationType,
      Long inboundRecordId,
      List<PlacedDetailsV1> groups,
      LocalDate effectiveBusinessDate,
      String reason) {}

  private record TransformPayload(
      OrchidGroupMutationType mutationType,
      List<TransformSourceV1> sources,
      List<PlacedDetailsV1> results,
      LocalDate effectiveBusinessDate,
      String reason,
      Set<Long> placementExclusionOrchidGroupIds) {}

  private record UpdatePayload(
      OrchidGroupMutationType mutationType,
      Long orchidGroupId,
      DetailsV1 details,
      LocalDate effectiveBusinessDate,
      String reason) {}

  private record MovePayload(
      OrchidGroupMutationType mutationType,
      Long orchidGroupId,
      Long toBedZoneId,
      BigDecimal startPosition,
      BigDecimal endPosition,
      LocalDate effectiveBusinessDate,
      String reason) {}

  private record BatchMovePayload(
      OrchidGroupMutationType mutationType,
      List<MoveItemV1> items,
      LocalDate effectiveBusinessDate,
      String reason,
      Set<Long> placementExclusionOrchidGroupIds) {}

  private record CancelCreationPayload(
      OrchidGroupMutationType mutationType,
      Long orchidGroupId,
      LocalDate effectiveBusinessDate,
      String reason) {}

  private record CorrectedCancelCreationPayload(
      OrchidGroupMutationType mutationType,
      Long orchidGroupId,
      RelatedMutationsV1 correctedMutations,
      LocalDate effectiveBusinessDate,
      String reason) {}

  private record DiscardPayload(
      OrchidGroupMutationType mutationType,
      Long orchidGroupId,
      Integer quantity,
      LocalDate effectiveBusinessDate,
      String reason) {}

  private record QuantityPayload(
      OrchidGroupMutationType mutationType,
      List<QuantityItemV1> items,
      RelatedMutationsV1 relatedMutations,
      LocalDate effectiveBusinessDate,
      String reason) {}

  private record CorrectionPayload(
      OrchidGroupMutationType mutationType,
      List<CorrectionItemV1> items,
      RelatedMutationsV1 correctedMutations,
      LocalDate effectiveBusinessDate,
      String reason) {}

  private record ReconciliationPayload(
      OrchidGroupMutationType mutationType,
      Long orchidGroupId,
      Integer actualQuantity,
      String actualStatus,
      Long actualBedZoneId,
      BigDecimal actualStartPosition,
      BigDecimal actualEndPosition,
      LocalDate effectiveBusinessDate,
      String reason) {}

  private record CompensationPayload(
      OrchidGroupMutationType mutationType,
      List<Long> mutationIds,
      LocalDate effectiveBusinessDate,
      String reason) {}

  private record StockCountPayload(
      OrchidGroupMutationType mutationType,
      Long orchidGroupId,
      Long expectedRevision,
      Integer actualQuantity,
      LocalDate effectiveBusinessDate,
      String reason) {}

  private record CompensationWithCreationCancellationPayload(
      OrchidGroupMutationType mutationType,
      List<Long> mutationIds,
      List<Long> creationCancellationOrchidGroupIds,
      LocalDate effectiveBusinessDate,
      String reason) {}

  // These v1 values describe persisted hash input, not a second application/domain model.
  private record DetailsV1(
      Long varietyId,
      Integer quantity,
      String potSize,
      Integer ageYear,
      String status,
      String placementType,
      Integer trayCount,
      Boolean splitPlacementAllowed,
      BigDecimal startPosition,
      BigDecimal endPosition,
      String memo) {
    static DetailsV1 from(OrchidGroupMutationDetails value) {
      return new DetailsV1(
          value.varietyId(),
          value.quantity(),
          value.potSize(),
          value.ageYear(),
          value.status(),
          value.placementType(),
          value.trayCount(),
          value.splitPlacementAllowed(),
          value.startPosition(),
          value.endPosition(),
          value.memo());
    }
  }

  private record PlacedDetailsV1(Long bedZoneId, DetailsV1 details) {
    static PlacedDetailsV1 from(CreateOrchidGroupMutationItem value) {
      return new PlacedDetailsV1(value.bedZoneId(), DetailsV1.from(value.details()));
    }

    static PlacedDetailsV1 from(TransformOrchidGroupMutationResult value) {
      return new PlacedDetailsV1(value.bedZoneId(), DetailsV1.from(value.details()));
    }
  }

  private record TransformSourceV1(
      Long orchidGroupId,
      Integer transformedQuantity,
      BigDecimal releasedStartPosition,
      BigDecimal releasedEndPosition) {
    static TransformSourceV1 from(TransformOrchidGroupMutationSource value) {
      return new TransformSourceV1(
          value.orchidGroupId(),
          value.transformedQuantity(),
          value.releasedStartPosition(),
          value.releasedEndPosition());
    }
  }

  private record MoveItemV1(
      Long orchidGroupId, Long toBedZoneId, BigDecimal startPosition, BigDecimal endPosition) {
    static MoveItemV1 from(MoveOrchidGroupMutationItem value) {
      return new MoveItemV1(
          value.orchidGroupId(), value.toBedZoneId(), value.startPosition(), value.endPosition());
    }
  }

  private record QuantityItemV1(Long orchidGroupId, Integer quantity) {
    static QuantityItemV1 from(OrchidGroupQuantityMutationItem value) {
      return new QuantityItemV1(value.orchidGroupId(), value.quantity());
    }
  }

  private record CorrectionItemV1(
      Long orchidGroupId, Integer correctedQuantity, String correctedStatus) {
    static CorrectionItemV1 from(CorrectOrchidGroupMutationItem value) {
      return new CorrectionItemV1(
          value.orchidGroupId(), value.correctedQuantity(), value.correctedStatus());
    }
  }

  private record RelatedMutationsV1(boolean legacySource, List<Long> mutationIds) {
    static RelatedMutationsV1 from(RelatedOrchidGroupMutations value) {
      return value == null
          ? null
          : new RelatedMutationsV1(value.legacySource(), value.mutationIds());
    }
  }
}
