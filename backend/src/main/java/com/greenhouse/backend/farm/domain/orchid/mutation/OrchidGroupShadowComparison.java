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
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
		name = "orchid_group_shadow_comparisons",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_orchid_group_shadow_comparison_source",
				columnNames = {"source_domain", "source_type", "source_reference_id", "source_operation_key"}))
public class OrchidGroupShadowComparison {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orchid_group_shadow_comparisons_id_seq")
	@SequenceGenerator(
			name = "orchid_group_shadow_comparisons_id_seq",
			sequenceName = "orchid_group_shadow_comparisons_id_seq",
			allocationSize = 50)
	private Long id;

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
	private java.util.UUID correlationId;

	@Enumerated(EnumType.STRING)
	@Column(name = "mutation_type", nullable = false, length = 50)
	private OrchidGroupMutationType mutationType;

	@Column(name = "command_fingerprint", nullable = false, length = 64)
	private String commandFingerprint;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private OrchidGroupShadowComparisonStatus status;

	@Column(name = "writer_version", nullable = false, length = 50)
	private String writerVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "command_payload", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> commandPayload;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "expected_entries", nullable = false, columnDefinition = "jsonb")
	private List<Map<String, Object>> expectedEntries;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "actual_entries", nullable = false, columnDefinition = "jsonb")
	private List<Map<String, Object>> actualEntries;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private List<Map<String, Object>> mismatches;

	@Column(name = "engine_error", columnDefinition = "text")
	private String engineError;

	@Column(name = "recorded_at", nullable = false)
	private Instant recordedAt;

	public OrchidGroupShadowComparison(
			OrchidGroupMutationSource source,
			OrchidGroupMutationType mutationType,
			String commandFingerprint,
			OrchidGroupShadowComparisonStatus status,
			String writerVersion,
			Map<String, Object> commandPayload,
			List<Map<String, Object>> expectedEntries,
			List<Map<String, Object>> actualEntries,
			List<Map<String, Object>> mismatches,
			String engineError,
			Instant recordedAt) {
		if (source == null || mutationType == null || status == null || recordedAt == null) {
			throw new IllegalArgumentException("Shadow comparison 필수 값이 누락되었습니다.");
		}
		if (commandFingerprint == null || !commandFingerprint.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("Shadow command fingerprint 형식이 올바르지 않습니다.");
		}
		if (writerVersion == null || writerVersion.isBlank()) {
			throw new IllegalArgumentException("Shadow writer version이 필요합니다.");
		}
		this.sourceDomain = source.domain();
		this.sourceType = source.type();
		this.sourceReferenceId = source.referenceId();
		this.sourceOperationKey = source.operationKey();
		this.correlationId = source.correlationId();
		this.mutationType = mutationType;
		this.commandFingerprint = commandFingerprint;
		this.status = status;
		this.writerVersion = writerVersion.trim();
		this.commandPayload = commandPayload == null ? Map.of() : commandPayload;
		this.expectedEntries = expectedEntries == null ? List.of() : expectedEntries;
		this.actualEntries = actualEntries == null ? List.of() : actualEntries;
		this.mismatches = mismatches == null ? List.of() : mismatches;
		this.engineError = normalize(engineError);
		this.recordedAt = recordedAt;
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
