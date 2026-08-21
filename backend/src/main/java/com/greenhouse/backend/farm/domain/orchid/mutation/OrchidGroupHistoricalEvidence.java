package com.greenhouse.backend.farm.domain.orchid.mutation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
		name = "orchid_group_historical_evidence",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_orchid_group_historical_evidence_group",
				columnNames = {"mutation_id", "orchid_group_id"}))
public class OrchidGroupHistoricalEvidence {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orchid_group_historical_evidence_id_seq")
	@SequenceGenerator(
			name = "orchid_group_historical_evidence_id_seq",
			sequenceName = "orchid_group_historical_evidence_id_seq",
			allocationSize = 50)
	private Long id;

	@Column(name = "migration_run_id", nullable = false)
	private Long migrationRunId;

	@ManyToOne(fetch = jakarta.persistence.FetchType.LAZY, optional = false)
	@JoinColumn(name = "mutation_id", nullable = false)
	private OrchidGroupMutation mutation;

	@Column(name = "orchid_group_id", nullable = false)
	private Long orchidGroupId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrchidGroupMutationEntryRole role;

	@Enumerated(EnumType.STRING)
	@Column(name = "evidence_kind", nullable = false, length = 20)
	private OrchidGroupHistoricalEvidenceKind evidenceKind;

	@Enumerated(EnumType.STRING)
	@Column(name = "evidence_quality", nullable = false, length = 20)
	private OrchidGroupHistoricalEvidenceQuality evidenceQuality;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "known_fields", nullable = false)
	private String[] knownFields;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "before_fragment", columnDefinition = "jsonb")
	private Map<String, Object> beforeFragment;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "after_fragment", columnDefinition = "jsonb")
	private Map<String, Object> afterFragment;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "change_set", columnDefinition = "jsonb")
	private Map<String, Object> changeSet;

	@Column(name = "source_payload_fingerprint", nullable = false, length = 64)
	private String sourcePayloadFingerprint;

	public OrchidGroupHistoricalEvidence(
			Long migrationRunId,
			OrchidGroupMutation mutation,
			Long orchidGroupId,
			OrchidGroupMutationEntryRole role,
			OrchidGroupHistoricalEvidenceKind evidenceKind,
			OrchidGroupHistoricalEvidenceQuality evidenceQuality,
			List<String> knownFields,
			Map<String, Object> beforeFragment,
			Map<String, Object> afterFragment,
			Map<String, Object> changeSet,
			String sourcePayloadFingerprint) {
		if (migrationRunId == null || mutation == null || orchidGroupId == null || role == null
				|| evidenceKind == null || evidenceQuality == null) {
			throw new IllegalArgumentException("Historical evidence 필수 값이 누락되었습니다.");
		}
		if ((evidenceKind == OrchidGroupHistoricalEvidenceKind.GAP)
				!= (evidenceQuality == OrchidGroupHistoricalEvidenceQuality.GAP)) {
			throw new IllegalArgumentException("GAP kind와 quality는 함께 사용해야 합니다.");
		}
		if (beforeFragment == null && afterFragment == null && changeSet == null) {
			throw new IllegalArgumentException("Historical evidence에는 알려진 상태 조각이 필요합니다.");
		}
		if (sourcePayloadFingerprint == null || !sourcePayloadFingerprint.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("Historical evidence fingerprint 형식이 올바르지 않습니다.");
		}
		this.migrationRunId = migrationRunId;
		this.mutation = mutation;
		this.orchidGroupId = orchidGroupId;
		this.role = role;
		this.evidenceKind = evidenceKind;
		this.evidenceQuality = evidenceQuality;
		this.knownFields = normalizeFields(knownFields);
		this.beforeFragment = immutableMap(beforeFragment);
		this.afterFragment = immutableMap(afterFragment);
		this.changeSet = immutableMap(changeSet);
		this.sourcePayloadFingerprint = sourcePayloadFingerprint;
	}

	public List<String> knownFields() {
		return List.of(knownFields);
	}

	private String[] normalizeFields(List<String> values) {
		if (values == null) {
			throw new IllegalArgumentException("Historical evidence known fields가 필요합니다.");
		}
		return values.stream()
				.map(value -> value == null ? "" : value.trim())
				.filter(value -> !value.isEmpty())
				.distinct()
				.sorted()
				.toArray(String[]::new);
	}

	private Map<String, Object> immutableMap(Map<String, Object> value) {
		return value == null
				? null
				: Collections.unmodifiableMap(new LinkedHashMap<>(value));
	}
}
