package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.work.application.effect.WorkHistoricalEffectService;
import com.greenhouse.backend.work.application.effect.WorkHistoricalMutationLinkCommand;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrchidGroupHistoryMigrationOperatorService {

	private static final int IMPORT_BATCH_SIZE = 100;

	private final OrchidGroupHistoryMigrationPlanner planner;
	private final OrchidGroupHistoryMigrationService migrationService;
	private final WorkHistoricalEffectService workEffectService;
	private final OrchidGroupHistoricalLineageLinkService lineageLinkService;

	public OrchidGroupHistoryMigrationOperatorResult execute(
			OrchidGroupHistoryMigrationOperatorCommand command,
			OrchidGroupHistoryMigrationManifest manifest) {
		OrchidGroupHistoryMigrationPlan plan = planner.build(command.sourceCutoff(), manifest);
		Long runId = migrationService.plan(new OrchidGroupHistoryMigrationPlanCommand(
				command.runKey(),
				command.sourceCutoff(),
				command.backupFingerprint(),
				command.manifestFingerprint(),
				command.effectiveBusinessDate(),
				plan.sourceCounts(),
				plan.plannedCounts()));
		if (!command.apply()) {
			return new OrchidGroupHistoryMigrationOperatorResult(
					command.runKey(), runId, false, 0, 0, 0, 0, 0,
					plan.sourceCounts(), plan.plannedCounts(), Map.of());
		}

		migrationService.start(command.runKey());
		int batchCount = 0;
		int imported = 0;
		int replayed = 0;
		Map<String, Long> mutationIdsBySource = new LinkedHashMap<>();
		for (int offset = 0; offset < plan.mutations().size(); offset += IMPORT_BATCH_SIZE) {
			List<OrchidGroupHistoricalMutationInput> batch = plan.mutations().subList(
					offset, Math.min(offset + IMPORT_BATCH_SIZE, plan.mutations().size()));
			OrchidGroupHistoryMigrationBatchResult result = migrationService.importBatch(
					command.runKey(), batch);
			batchCount++;
			imported += result.importedMutations();
			replayed += result.replayedMutations();
			result.importedSources().forEach(source -> mutationIdsBySource.put(
					OrchidGroupHistoricalLineageLinkService.sourceKey(source.source()),
					source.mutationId()));
		}

		List<WorkHistoricalMutationLinkCommand> workLinkCommands = new ArrayList<>();
		for (OrchidGroupHistoryMigrationWorkLink workLink : plan.workLinks()) {
			Long mutationId = mutationIdsBySource.get(
					OrchidGroupHistoricalLineageLinkService.sourceKey(workLink.source()));
			if (mutationId == null) {
				throw new IllegalStateException("Historical Work Mutation ID가 적재 결과에 없습니다.");
			}
			workLinkCommands.add(new WorkHistoricalMutationLinkCommand(
					workLink.workEffectId(), mutationId, workLink.source().correlationId()));
		}
		workEffectService.linkMutations(workLinkCommands);
		int linkedLineages = lineageLinkService.link(
				command.sourceCutoff(), plan.workLinks(), mutationIdsBySource);
		migrationService.completeImport(command.runKey());
		Map<String, Object> verification = migrationService.verify(command.runKey());
		return new OrchidGroupHistoryMigrationOperatorResult(
				command.runKey(),
				runId,
				true,
				batchCount,
				imported,
				replayed,
				workLinkCommands.size(),
				linkedLineages,
				plan.sourceCounts(),
				plan.plannedCounts(),
				verification);
	}
}
