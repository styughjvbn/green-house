package com.greenhouse.backend.migration.application.orchid;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.migration.domain.orchid.OrchidGroupHistoryMigrationRun;
import com.greenhouse.backend.migration.repository.orchid.OrchidGroupHistoryMigrationRunRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrchidGroupHistoryMigrationRunService {

	private final OrchidGroupHistoryMigrationRunRepository repository;

	@Transactional(propagation = Propagation.MANDATORY)
	public Long registerPlan(OrchidGroupHistoryMigrationRunPlan plan, Instant now) {
		return repository.findByRunKey(plan.runKey())
				.map(existing -> {
					if (!existing.hasSamePlan(
							plan.sourceCutoff(),
							plan.backupFingerprint(),
							plan.manifestFingerprint(),
							plan.sourceStateFingerprint(),
							plan.effectiveBusinessDate(),
							plan.sourceCounts(),
							plan.plannedCounts())) {
						throw new ConflictException(
								"같은 historical migration run key를 다른 plan에 재사용할 수 없습니다.");
					}
					return existing.getId();
				})
				.orElseGet(() -> repository.save(new OrchidGroupHistoryMigrationRun(
						plan.runKey(),
						plan.sourceCutoff(),
						plan.backupFingerprint(),
						plan.manifestFingerprint(),
						plan.sourceStateFingerprint(),
						plan.effectiveBusinessDate(),
						plan.sourceCounts(),
						plan.plannedCounts(),
						now)).getId());
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public OrchidGroupHistoryMigrationRunState start(UUID runKey, Instant now) {
		OrchidGroupHistoryMigrationRun run = findForUpdate(runKey);
		run.start(now);
		return stateOf(run);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public OrchidGroupHistoryMigrationRunState lock(UUID runKey) {
		return stateOf(findForUpdate(runKey));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public OrchidGroupHistoryMigrationRunState completeImport(
			UUID runKey,
			Map<String, Long> importedCounts,
			Instant now) {
		OrchidGroupHistoryMigrationRun run = findForUpdate(runKey);
		run.completeImport(importedCounts, now);
		return stateOf(run);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public OrchidGroupHistoryMigrationRunState verify(
			UUID runKey,
			Map<String, Object> verificationResult,
			Instant now) {
		OrchidGroupHistoryMigrationRun run = findForUpdate(runKey);
		run.verify(verificationResult, now);
		return stateOf(run);
	}

	@Transactional(readOnly = true)
	public Optional<OrchidGroupHistoryMigrationRunState> findLatest() {
		return repository.findFirstByOrderByIdDesc().map(this::stateOf);
	}

	private OrchidGroupHistoryMigrationRun findForUpdate(UUID runKey) {
		return repository.findForUpdateByRunKey(runKey)
				.orElseThrow(() -> new NotFoundException("Historical migration run을 찾을 수 없습니다."));
	}

	private OrchidGroupHistoryMigrationRunState stateOf(OrchidGroupHistoryMigrationRun run) {
		return new OrchidGroupHistoryMigrationRunState(
				run.getId(),
				run.getRunKey(),
				OrchidGroupHistoryMigrationRunPhase.valueOf(run.getStatus().name()),
				run.getSourceCutoff(),
				run.getSourceStateFingerprint(),
				Map.copyOf(run.getPlannedCounts()),
				run.getImportedCounts() == null ? null : Map.copyOf(run.getImportedCounts()),
				run.getVerificationResult() == null ? null : Map.copyOf(run.getVerificationResult()));
	}
}
