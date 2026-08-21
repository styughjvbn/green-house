package com.greenhouse.backend.migration.domain.orchid;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
		name = "orchid_group_history_migration_runs",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_orchid_group_history_migration_run_key",
				columnNames = "run_key"))
public class OrchidGroupHistoryMigrationRun {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orchid_group_history_migration_runs_id_seq")
	@SequenceGenerator(
			name = "orchid_group_history_migration_runs_id_seq",
			sequenceName = "orchid_group_history_migration_runs_id_seq",
			allocationSize = 50)
	private Long id;

	@Column(name = "run_key", nullable = false)
	private UUID runKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrchidGroupHistoryMigrationStatus status;

	@Column(name = "source_cutoff", nullable = false)
	private Instant sourceCutoff;

	@Column(name = "backup_fingerprint", nullable = false, length = 64)
	private String backupFingerprint;

	@Column(name = "manifest_fingerprint", nullable = false, length = 64)
	private String manifestFingerprint;

	@Column(name = "source_state_fingerprint", nullable = false, length = 64)
	private String sourceStateFingerprint;

	@Column(name = "effective_business_date", nullable = false)
	private LocalDate effectiveBusinessDate;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "source_counts", nullable = false, columnDefinition = "jsonb")
	private Map<String, Long> sourceCounts;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "planned_counts", nullable = false, columnDefinition = "jsonb")
	private Map<String, Long> plannedCounts;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "imported_counts", columnDefinition = "jsonb")
	private Map<String, Long> importedCounts;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "verification_result", columnDefinition = "jsonb")
	private Map<String, Object> verificationResult;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public OrchidGroupHistoryMigrationRun(
			UUID runKey,
			Instant sourceCutoff,
			String backupFingerprint,
			String manifestFingerprint,
			String sourceStateFingerprint,
			LocalDate effectiveBusinessDate,
			Map<String, Long> sourceCounts,
			Map<String, Long> plannedCounts,
			Instant now) {
		if (runKey == null || sourceCutoff == null || effectiveBusinessDate == null || now == null) {
			throw new IllegalArgumentException("Historical migration run 필수 값이 누락되었습니다.");
		}
		this.runKey = runKey;
		this.status = OrchidGroupHistoryMigrationStatus.DRY_RUN;
		this.sourceCutoff = sourceCutoff;
		this.backupFingerprint = requireFingerprint(backupFingerprint, "백업");
		this.manifestFingerprint = requireFingerprint(manifestFingerprint, "manifest");
		this.sourceStateFingerprint = requireFingerprint(sourceStateFingerprint, "현재 상태");
		this.effectiveBusinessDate = effectiveBusinessDate;
		this.sourceCounts = immutableCounts(sourceCounts, "source counts");
		this.plannedCounts = immutableCounts(plannedCounts, "planned counts");
		this.createdAt = now;
		this.updatedAt = now;
	}

	public boolean hasSamePlan(
			Instant sourceCutoff,
			String backupFingerprint,
			String manifestFingerprint,
			String sourceStateFingerprint,
			LocalDate effectiveBusinessDate,
			Map<String, Long> sourceCounts,
			Map<String, Long> plannedCounts) {
		return this.sourceCutoff.equals(sourceCutoff)
				&& this.backupFingerprint.equals(backupFingerprint)
				&& this.manifestFingerprint.equals(manifestFingerprint)
				&& this.sourceStateFingerprint.equals(sourceStateFingerprint)
				&& this.effectiveBusinessDate.equals(effectiveBusinessDate)
				&& this.sourceCounts.equals(sourceCounts)
				&& this.plannedCounts.equals(plannedCounts);
	}

	public void start(Instant now) {
		if (status == OrchidGroupHistoryMigrationStatus.PREPARING
				|| status == OrchidGroupHistoryMigrationStatus.IMPORTED
				|| status == OrchidGroupHistoryMigrationStatus.VERIFIED) {
			return;
		}
		if (status != OrchidGroupHistoryMigrationStatus.DRY_RUN) {
			throw new IllegalStateException("DRY_RUN migration만 적재를 시작할 수 있습니다.");
		}
		status = OrchidGroupHistoryMigrationStatus.PREPARING;
		startedAt = now;
		updatedAt = now;
	}

	public void completeImport(Map<String, Long> importedCounts, Instant now) {
		if (status == OrchidGroupHistoryMigrationStatus.IMPORTED
				&& this.importedCounts != null && this.importedCounts.equals(importedCounts)) {
			return;
		}
		if (status != OrchidGroupHistoryMigrationStatus.PREPARING) {
			throw new IllegalStateException("PREPARING migration만 적재 완료할 수 있습니다.");
		}
		this.importedCounts = immutableCounts(importedCounts, "imported counts");
		status = OrchidGroupHistoryMigrationStatus.IMPORTED;
		updatedAt = now;
	}

	public void verify(Map<String, Object> verificationResult, Instant now) {
		if (status == OrchidGroupHistoryMigrationStatus.VERIFIED) {
			return;
		}
		if (status != OrchidGroupHistoryMigrationStatus.IMPORTED) {
			throw new IllegalStateException("IMPORTED migration만 검증 완료할 수 있습니다.");
		}
		if (verificationResult == null || !Boolean.TRUE.equals(verificationResult.get("ready"))) {
			throw new IllegalArgumentException("ready=true인 migration 검증 결과가 필요합니다.");
		}
		this.verificationResult = Map.copyOf(verificationResult);
		status = OrchidGroupHistoryMigrationStatus.VERIFIED;
		completedAt = now;
		updatedAt = now;
	}

	public void fail(Map<String, Object> failureResult, Instant now) {
		if (status == OrchidGroupHistoryMigrationStatus.VERIFIED) {
			throw new IllegalStateException("VERIFIED migration은 실패 상태로 바꿀 수 없습니다.");
		}
		if (failureResult == null || failureResult.isEmpty()) {
			throw new IllegalArgumentException("Migration 실패 결과가 필요합니다.");
		}
		this.verificationResult = Map.copyOf(failureResult);
		status = OrchidGroupHistoryMigrationStatus.FAILED;
		completedAt = now;
		updatedAt = now;
	}

	private String requireFingerprint(String value, String label) {
		if (value == null || !value.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException(label + " fingerprint 형식이 올바르지 않습니다.");
		}
		return value;
	}

	private Map<String, Long> immutableCounts(Map<String, Long> values, String label) {
		if (values == null || values.entrySet().stream().anyMatch(entry ->
				entry.getKey() == null || entry.getKey().isBlank()
						|| entry.getValue() == null || entry.getValue() < 0)) {
			throw new IllegalArgumentException(label + "가 올바르지 않습니다.");
		}
		return Map.copyOf(new LinkedHashMap<>(values));
	}
}
