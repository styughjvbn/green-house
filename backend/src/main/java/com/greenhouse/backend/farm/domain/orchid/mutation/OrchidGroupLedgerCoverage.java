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
/**
 * ORCHID-CUTOVER: TARGET, DATA_RETAIN — ledger 적용 범위와 ACTIVE 전환 증거를 보존한다.
 */
@Entity
@Table(
		name = "orchid_group_ledger_coverages",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_orchid_group_ledger_coverage_cutover",
				columnNames = "cutover_key"))
public class OrchidGroupLedgerCoverage {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orchid_group_ledger_coverages_id_seq")
	@SequenceGenerator(
			name = "orchid_group_ledger_coverages_id_seq",
			sequenceName = "orchid_group_ledger_coverages_id_seq",
			allocationSize = 50)
	private Long id;

	@Column(name = "cutover_key", nullable = false)
	private UUID cutoverKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrchidGroupLedgerCoverageStatus status;

	@Column(name = "engine_schema_version", nullable = false)
	private Integer engineSchemaVersion;

	@Column(name = "snapshot_schema_version", nullable = false)
	private Integer snapshotSchemaVersion;

	@Column(name = "baseline_started_at")
	private Instant baselineStartedAt;

	@Column(name = "baseline_completed_at")
	private Instant baselineCompletedAt;

	@Column(name = "effective_business_date", nullable = false)
	private LocalDate effectiveBusinessDate;

	@Column(name = "baseline_group_count")
	private Long baselineGroupCount;

	@Column(name = "baseline_fingerprint", length = 64)
	private String baselineFingerprint;

	@Column(name = "minimum_writer_version", nullable = false, length = 50)
	private String minimumWriterVersion;

	public OrchidGroupLedgerCoverage(
			UUID cutoverKey,
			int engineSchemaVersion,
			int snapshotSchemaVersion,
			LocalDate effectiveBusinessDate,
			String minimumWriterVersion) {
		if (cutoverKey == null || effectiveBusinessDate == null) {
			throw new IllegalArgumentException("Ledger cutover key와 업무일이 필요합니다.");
		}
		if (engineSchemaVersion < 1 || snapshotSchemaVersion < 1) {
			throw new IllegalArgumentException("Ledger schema version은 1 이상이어야 합니다.");
		}
		if (minimumWriterVersion == null || minimumWriterVersion.isBlank()) {
			throw new IllegalArgumentException("최소 writer version이 필요합니다.");
		}
		if (minimumWriterVersion.trim().length() > 50) {
			throw new IllegalArgumentException("최소 writer version은 50자 이하여야 합니다.");
		}
		this.cutoverKey = cutoverKey;
		this.status = OrchidGroupLedgerCoverageStatus.PREPARING;
		this.engineSchemaVersion = engineSchemaVersion;
		this.snapshotSchemaVersion = snapshotSchemaVersion;
		this.effectiveBusinessDate = effectiveBusinessDate;
		this.minimumWriterVersion = minimumWriterVersion.trim();
	}

	public boolean hasSamePreparation(
			int engineSchemaVersion,
			int snapshotSchemaVersion,
			LocalDate effectiveBusinessDate,
			String minimumWriterVersion) {
		return this.engineSchemaVersion == engineSchemaVersion
				&& this.snapshotSchemaVersion == snapshotSchemaVersion
				&& this.effectiveBusinessDate.equals(effectiveBusinessDate)
				&& this.minimumWriterVersion.equals(minimumWriterVersion == null ? null : minimumWriterVersion.trim());
	}

	public void startBaseline(Instant startedAt) {
		if (status != OrchidGroupLedgerCoverageStatus.PREPARING || startedAt == null) {
			throw new IllegalStateException("PREPARING coverage만 baseline을 시작할 수 있습니다.");
		}
		this.baselineStartedAt = startedAt;
	}

	public void activate(Instant completedAt, long groupCount, String fingerprint) {
		if (status != OrchidGroupLedgerCoverageStatus.PREPARING || baselineStartedAt == null) {
			throw new IllegalStateException("시작된 PREPARING coverage만 활성화할 수 있습니다.");
		}
		if (completedAt == null || groupCount < 0 || fingerprint == null
				|| !fingerprint.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("Baseline 완료 정보가 올바르지 않습니다.");
		}
		this.status = OrchidGroupLedgerCoverageStatus.ACTIVE;
		this.baselineCompletedAt = completedAt;
		this.baselineGroupCount = groupCount;
		this.baselineFingerprint = fingerprint;
	}

	public void fail(Instant failedAt) {
		if (status == OrchidGroupLedgerCoverageStatus.ACTIVE || failedAt == null) {
			throw new IllegalStateException("ACTIVE coverage는 실패 상태로 되돌릴 수 없습니다.");
		}
		this.status = OrchidGroupLedgerCoverageStatus.FAILED;
		this.baselineCompletedAt = failedAt;
	}
}
