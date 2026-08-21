package com.greenhouse.backend.farm.application.orchid.mutation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record OrchidGroupHistoryMigrationManifest(
		int schemaVersion,
		List<AttestedQuantityCorrection> attestedQuantityCorrections,
		LegacySalesReference legacySalesReference) {

	public OrchidGroupHistoryMigrationManifest {
		if (schemaVersion != 1) {
			throw new IllegalArgumentException("지원하지 않는 historical migration manifest version입니다.");
		}
		attestedQuantityCorrections = attestedQuantityCorrections == null
				? List.of()
				: List.copyOf(attestedQuantityCorrections);
		if (legacySalesReference == null) {
			throw new IllegalArgumentException("Legacy Sales 참고자료 분류가 필요합니다.");
		}
	}

	public record AttestedQuantityCorrection(
			Long orchidGroupId,
			int beforeQuantity,
			int afterQuantity,
			Instant occurredAt,
			String reason,
			String attestedBy,
			LocalDate attestedOn) {

		public AttestedQuantityCorrection {
			if (orchidGroupId == null || beforeQuantity < 0 || afterQuantity < 0
					|| beforeQuantity == afterQuantity || occurredAt == null
					|| reason == null || reason.isBlank()
					|| attestedBy == null || attestedBy.isBlank() || attestedOn == null) {
				throw new IllegalArgumentException("운영자 확인 수량 보정 정보가 올바르지 않습니다.");
			}
			reason = reason.trim();
			attestedBy = attestedBy.trim();
		}
	}

	public record LegacySalesReference(
			boolean referenceOnly,
			long expectedSlipCount,
			long expectedItemCount,
			String reason) {

		public LegacySalesReference {
			if (!referenceOnly || expectedSlipCount < 0 || expectedItemCount < 0
					|| reason == null || reason.isBlank()) {
				throw new IllegalArgumentException("Legacy Sales 참고자료 분류가 올바르지 않습니다.");
			}
			reason = reason.trim();
		}
	}
}
