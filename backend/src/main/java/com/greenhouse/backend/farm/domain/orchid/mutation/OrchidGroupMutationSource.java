package com.greenhouse.backend.farm.domain.orchid.mutation;

import java.util.UUID;

public record OrchidGroupMutationSource(
		OrchidGroupMutationSourceDomain domain,
		String type,
		String referenceId,
		String operationKey,
		UUID correlationId) {

	public OrchidGroupMutationSource {
		if (domain == null) {
			throw new IllegalArgumentException("Mutation source domain이 필요합니다.");
		}
		type = requireText(type, "Mutation source type", 50);
		referenceId = requireText(referenceId, "Mutation source reference ID", 100);
		operationKey = requireText(operationKey, "Mutation source operation key", 200);
		if (correlationId == null) {
			throw new IllegalArgumentException("Mutation correlation ID가 필요합니다.");
		}
	}

	private static String requireText(String value, String label, int maxLength) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + "가 필요합니다.");
		}
		String normalized = value.trim();
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(label + "는 " + maxLength + "자 이하여야 합니다.");
		}
		return normalized;
	}
}
