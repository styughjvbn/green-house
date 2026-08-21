package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class OrchidGroupMutationSources {

	private OrchidGroupMutationSources() {
	}

	public static OrchidGroupMutationSource farmRequest(
			String sourceType,
			String referenceId,
			String operation) {
		UUID correlationId = UUID.randomUUID();
		return new OrchidGroupMutationSource(
				OrchidGroupMutationSourceDomain.FARM,
				sourceType,
				referenceId,
				operation + ":" + correlationId,
				correlationId);
	}

	public static OrchidGroupMutationSource farmBatch(
			String sourceType,
			String referenceId,
			String operation,
			UUID correlationId) {
		return new OrchidGroupMutationSource(
				OrchidGroupMutationSourceDomain.FARM,
				sourceType,
				referenceId,
				operation,
				correlationId);
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

	public static OrchidGroupMutationSource sales(Long salesSlipId, String operationKey) {
		return stable(
				OrchidGroupMutationSourceDomain.SALES,
				"SALES_SLIP",
				salesSlipId.toString(),
				operationKey,
				"SALES_SLIP:" + salesSlipId);
	}

	public static OrchidGroupMutationSource historicalAudit(Long auditEventId, String action) {
		return stable(
				OrchidGroupMutationSourceDomain.FARM,
				"AUDIT_EVENT",
				auditEventId.toString(),
				action,
				"AUDIT_EVENT:" + auditEventId);
	}

	public static OrchidGroupMutationSource migration(
			String sourceType,
			String referenceId,
			String operationKey) {
		return stable(
				OrchidGroupMutationSourceDomain.MIGRATION,
				sourceType,
				referenceId,
				operationKey,
				sourceType + ":" + referenceId);
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
