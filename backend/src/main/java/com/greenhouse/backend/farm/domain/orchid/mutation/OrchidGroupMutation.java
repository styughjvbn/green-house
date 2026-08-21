package com.greenhouse.backend.farm.domain.orchid.mutation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
		name = "orchid_group_mutations",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_orchid_group_mutation_source",
				columnNames = {"source_domain", "source_type", "source_reference_id", "source_operation_key"}))
public class OrchidGroupMutation {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orchid_group_mutations_id_seq")
	@SequenceGenerator(
			name = "orchid_group_mutations_id_seq",
			sequenceName = "orchid_group_mutations_id_seq",
			allocationSize = 50)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "mutation_type", nullable = false, length = 50)
	private OrchidGroupMutationType mutationType;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_domain", nullable = false, length = 30)
	private OrchidGroupMutationSourceDomain sourceDomain;

	@Column(name = "source_type", nullable = false, length = 50)
	private String sourceType;

	@Column(name = "source_reference_id", nullable = false, length = 100)
	private String sourceReferenceId;

	@Column(name = "source_operation_key", nullable = false, length = 200)
	private String sourceOperationKey;

	@Column(name = "correlation_id", nullable = false)
	private UUID correlationId;

	@Column(name = "command_fingerprint", nullable = false, length = 64)
	private String commandFingerprint;

	@Column(name = "recorded_at", nullable = false)
	private Instant recordedAt;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "effective_business_date", nullable = false)
	private LocalDate effectiveBusinessDate;

	@Column(columnDefinition = "text")
	private String reason;

	@Column(name = "schema_version", nullable = false)
	private Integer schemaVersion;

	public OrchidGroupMutation(
			OrchidGroupMutationType mutationType,
			OrchidGroupMutationSource source,
			String commandFingerprint,
			Instant recordedAt,
			LocalDate effectiveBusinessDate,
			String reason,
			int schemaVersion) {
		this(
				mutationType,
				source,
				commandFingerprint,
				recordedAt,
				recordedAt,
				effectiveBusinessDate,
				reason,
				schemaVersion);
	}

	public OrchidGroupMutation(
			OrchidGroupMutationType mutationType,
			OrchidGroupMutationSource source,
			String commandFingerprint,
			Instant occurredAt,
			Instant recordedAt,
			LocalDate effectiveBusinessDate,
			String reason,
			int schemaVersion) {
		if (mutationType == null || source == null || occurredAt == null
				|| recordedAt == null || effectiveBusinessDate == null) {
			throw new IllegalArgumentException("Mutation type, source와 적용 시점이 필요합니다.");
		}
		if (commandFingerprint == null || !commandFingerprint.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("Mutation command fingerprint 형식이 올바르지 않습니다.");
		}
		if (schemaVersion < 1) {
			throw new IllegalArgumentException("Mutation schema version은 1 이상이어야 합니다.");
		}
		this.mutationType = mutationType;
		this.sourceDomain = source.domain();
		this.sourceType = source.type();
		this.sourceReferenceId = source.referenceId();
		this.sourceOperationKey = source.operationKey();
		this.correlationId = source.correlationId();
		this.commandFingerprint = commandFingerprint;
		this.occurredAt = occurredAt;
		this.recordedAt = recordedAt;
		this.effectiveBusinessDate = effectiveBusinessDate;
		this.reason = normalize(reason);
		this.schemaVersion = schemaVersion;
	}

	public boolean hasSameCommandFingerprint(String fingerprint) {
		return commandFingerprint.equals(fingerprint);
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}
}
