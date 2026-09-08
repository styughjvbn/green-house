package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OrchidGroupMutationCommandFingerprint {

	private final OrchidGroupMutationFingerprint fingerprint;

	public OrchidGroupMutationCommandFingerprint(OrchidGroupMutationFingerprint fingerprint) {
		this.fingerprint = fingerprint;
	}

	public String calculate(OrchidGroupMutationCommand command) {
		return switch (command) {
			case CreateOrchidGroupMutationCommand value ->
				fingerprint.calculate(new CreatePayload(OrchidGroupMutationType.CREATE, value.bedZoneId(),
						value.details(), value.effectiveBusinessDate(), value.reason()));
			case CreateOrchidGroupsMutationCommand value ->
				fingerprint.calculate(new CreateManyPayload(OrchidGroupMutationType.CREATE, value.groups(),
						value.effectiveBusinessDate(), value.reason()));
			case CreateInboundOrchidGroupsMutationCommand value ->
				fingerprint.calculate(new CreateInboundPayload(OrchidGroupMutationType.CREATE, value.inboundRecordId(),
						value.groups(), value.effectiveBusinessDate(), value.reason()));
			case TransformOrchidGroupsMutationCommand value -> fingerprint
				.calculate(new TransformPayload(OrchidGroupMutationType.TRANSFORM, value.sources(), value.results(),
						value.effectiveBusinessDate(), value.reason(), value.placementExclusionOrchidGroupIds()));
			case UpdateOrchidGroupMutationCommand value ->
				fingerprint.calculate(new UpdatePayload(OrchidGroupMutationType.UPDATE_DETAILS, value.orchidGroupId(),
						value.details(), value.effectiveBusinessDate(), value.reason()));
			case MoveOrchidGroupMutationCommand value -> fingerprint
				.calculate(new MovePayload(OrchidGroupMutationType.MOVE, value.orchidGroupId(), value.toBedZoneId(),
						value.startPosition(), value.endPosition(), value.effectiveBusinessDate(), value.reason()));
			case CancelOrchidGroupCreationMutationCommand value ->
				fingerprint.calculate(new CancelCreationPayload(OrchidGroupMutationType.CANCEL_CREATION,
						value.orchidGroupId(), value.effectiveBusinessDate(), value.reason()));
			case DiscardOrchidGroupMutationCommand value ->
				fingerprint.calculate(new DiscardPayload(OrchidGroupMutationType.DISCARD, value.orchidGroupId(),
						value.quantity(), value.effectiveBusinessDate(), value.reason()));
			case ReserveOrchidGroupsMutationCommand value -> quantity(OrchidGroupMutationType.RESERVE, value.items(),
					null, value.effectiveBusinessDate(), value.reason());
			case ReleaseOrchidGroupReservationsMutationCommand value ->
				quantity(OrchidGroupMutationType.RELEASE_RESERVATION, value.items(), null,
						value.effectiveBusinessDate(), value.reason());
			case ConsumeOrchidGroupReservationsMutationCommand value ->
				quantity(OrchidGroupMutationType.CONSUME_RESERVATION, value.items(), null,
						value.effectiveBusinessDate(), value.reason());
			case RestoreOutboundOrchidGroupsMutationCommand value -> quantity(OrchidGroupMutationType.RESTORE_OUTBOUND,
					value.items(), value.compensatedMutations(), value.effectiveBusinessDate(), value.reason());
			case CorrectOrchidGroupsMutationCommand value ->
				fingerprint.calculate(new CorrectionPayload(OrchidGroupMutationType.CORRECTION, value.items(),
						value.correctedMutations(), value.effectiveBusinessDate(), value.reason()));
		};
	}

	private String quantity(OrchidGroupMutationType mutationType, List<OrchidGroupQuantityMutationItem> items,
			RelatedOrchidGroupMutations relatedMutations, LocalDate effectiveBusinessDate, String reason) {
		return fingerprint
			.calculate(new QuantityPayload(mutationType, items, relatedMutations, effectiveBusinessDate, reason));
	}

	private record CreatePayload(OrchidGroupMutationType mutationType, Long bedZoneId,
			OrchidGroupMutationDetails details, LocalDate effectiveBusinessDate, String reason) {
	}

	private record CreateManyPayload(OrchidGroupMutationType mutationType, List<CreateOrchidGroupMutationItem> groups,
			LocalDate effectiveBusinessDate, String reason) {
	}

	private record CreateInboundPayload(OrchidGroupMutationType mutationType, Long inboundRecordId,
			List<CreateOrchidGroupMutationItem> groups, LocalDate effectiveBusinessDate, String reason) {
	}

	private record TransformPayload(OrchidGroupMutationType mutationType,
			List<TransformOrchidGroupMutationSource> sources, List<TransformOrchidGroupMutationResult> results,
			LocalDate effectiveBusinessDate, String reason, Set<Long> placementExclusionOrchidGroupIds) {
	}

	private record UpdatePayload(OrchidGroupMutationType mutationType, Long orchidGroupId,
			OrchidGroupMutationDetails details, LocalDate effectiveBusinessDate, String reason) {
	}

	private record MovePayload(OrchidGroupMutationType mutationType, Long orchidGroupId, Long toBedZoneId,
			BigDecimal startPosition, BigDecimal endPosition, LocalDate effectiveBusinessDate, String reason) {
	}

	private record CancelCreationPayload(OrchidGroupMutationType mutationType, Long orchidGroupId,
			LocalDate effectiveBusinessDate, String reason) {
	}

	private record DiscardPayload(OrchidGroupMutationType mutationType, Long orchidGroupId, Integer quantity,
			LocalDate effectiveBusinessDate, String reason) {
	}

	private record QuantityPayload(OrchidGroupMutationType mutationType, List<OrchidGroupQuantityMutationItem> items,
			RelatedOrchidGroupMutations relatedMutations, LocalDate effectiveBusinessDate, String reason) {
	}

	private record CorrectionPayload(OrchidGroupMutationType mutationType, List<CorrectOrchidGroupMutationItem> items,
			RelatedOrchidGroupMutations correctedMutations, LocalDate effectiveBusinessDate, String reason) {
	}

}
