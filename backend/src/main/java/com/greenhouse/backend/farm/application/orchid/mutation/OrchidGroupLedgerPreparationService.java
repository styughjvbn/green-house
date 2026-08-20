package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverage;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverageStatus;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupLedgerCoverageRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrchidGroupLedgerPreparationService {

	private static final int ENGINE_SCHEMA_VERSION = 1;
	private static final int SNAPSHOT_SCHEMA_VERSION = 1;

	private final OrchidGroupLedgerCoverageRepository coverageRepository;
	private final OrchidGroupRepository orchidGroupRepository;
	private final OrchidGroupMutationRepository mutationRepository;
	private final OrchidGroupMutationEntryRepository entryRepository;
	private final OrchidGroupMutationFingerprint fingerprint;
	private final OrchidGroupMutationReplayResolver replayResolver;
	private final Clock clock;

	@Transactional
	public Long prepare(
			UUID cutoverKey,
			LocalDate effectiveBusinessDate,
			String minimumWriterVersion) {
		coverageRepository.findFirstByStatus(OrchidGroupLedgerCoverageStatus.ACTIVE).ifPresent(active -> {
			throw new ConflictException("이미 ACTIVE 상태인 OrchidGroup ledger coverage가 있습니다.");
		});
		return coverageRepository.findByCutoverKey(cutoverKey)
				.map(existing -> {
					if (!existing.hasSamePreparation(
							ENGINE_SCHEMA_VERSION,
							SNAPSHOT_SCHEMA_VERSION,
							effectiveBusinessDate,
							minimumWriterVersion)) {
						throw new ConflictException("같은 cutover key를 다른 coverage 설정에 재사용할 수 없습니다.");
					}
					return existing.getId();
				})
				.orElseGet(() -> {
					coverageRepository.findFirstByStatus(OrchidGroupLedgerCoverageStatus.PREPARING)
							.ifPresent(preparing -> {
								throw new ConflictException(
										"다른 PREPARING OrchidGroup ledger coverage가 이미 있습니다.");
							});
					return coverageRepository.save(new OrchidGroupLedgerCoverage(
							cutoverKey,
							ENGINE_SCHEMA_VERSION,
							SNAPSHOT_SCHEMA_VERSION,
							effectiveBusinessDate,
							minimumWriterVersion)).getId();
				});
	}

	@Transactional
	public void start(UUID cutoverKey) {
		OrchidGroupLedgerCoverage coverage = findCoverage(cutoverKey);
		if (coverage.getStatus() != OrchidGroupLedgerCoverageStatus.PREPARING) {
			throw new IllegalStateException("PREPARING coverage만 baseline을 시작할 수 있습니다.");
		}
		if (coverage.getBaselineStartedAt() == null) {
			coverage.startBaseline(Instant.now(clock));
		}
	}

	@Transactional
	public OrchidGroupMutationResult baselineBatch(BaselineOrchidGroupsCommand command) {
		OrchidGroupLedgerCoverage coverage = findCoverage(command.cutoverKey());
		if (coverage.getStatus() != OrchidGroupLedgerCoverageStatus.PREPARING
				|| coverage.getBaselineStartedAt() == null) {
			throw new IllegalStateException("시작된 PREPARING coverage에서만 baseline batch를 실행할 수 있습니다.");
		}

		List<OrchidGroup> groups = orchidGroupRepository.findAllForUpdateByIdIn(command.orchidGroupIds());
		if (groups.size() != command.orchidGroupIds().size()) {
			throw new NotFoundException("Baseline 대상 난 묶음을 모두 찾을 수 없습니다.");
		}
		List<BaselineFingerprintEntry> fingerprintEntries = groups.stream()
				.map(group -> new BaselineFingerprintEntry(group.getId(), OrchidGroupStateSnapshot.from(group)))
				.toList();
		String commandFingerprint = fingerprint.calculate(new BaselineFingerprintPayload(
				OrchidGroupMutationType.BASELINE_IMPORT,
				command.effectiveBusinessDate(),
				fingerprintEntries));
		OrchidGroupMutationSource source = new OrchidGroupMutationSource(
				OrchidGroupMutationSourceDomain.MIGRATION,
				"LEDGER_BASELINE",
				command.cutoverKey().toString(),
				command.batchKey(),
				command.cutoverKey());
		var replay = replayResolver.findExisting(source, commandFingerprint);
		if (replay.isPresent()) {
			return replay.get();
		}
		if (groups.stream().anyMatch(group -> group.getStateRevision() != null)) {
			throw new ConflictException("이미 revision이 설정된 난 묶음을 새 baseline batch에 포함할 수 없습니다.");
		}

		OrchidGroupMutation mutation = mutationRepository.save(new OrchidGroupMutation(
				OrchidGroupMutationType.BASELINE_IMPORT,
				source,
				commandFingerprint,
				Instant.now(clock),
				command.effectiveBusinessDate(),
				"OrchidGroup mutation ledger baseline",
				ENGINE_SCHEMA_VERSION));
		List<OrchidGroupMutationEntry> entries = IntStream.range(0, groups.size())
				.mapToObj(index -> {
					OrchidGroup group = groups.get(index);
					group.establishBaselineRevision();
					return OrchidGroupMutationEntry.baseline(
							mutation, group.getId(), fingerprintEntries.get(index).snapshot());
				})
				.toList();
		entryRepository.saveAll(entries);
		return OrchidGroupMutationResult.from(mutation, entries);
	}

	private OrchidGroupLedgerCoverage findCoverage(UUID cutoverKey) {
		return coverageRepository.findByCutoverKey(cutoverKey)
				.orElseThrow(() -> new NotFoundException("OrchidGroup ledger coverage를 찾을 수 없습니다."));
	}

	private record BaselineFingerprintPayload(
			OrchidGroupMutationType mutationType,
			LocalDate effectiveBusinessDate,
			List<BaselineFingerprintEntry> groups) {
	}

	private record BaselineFingerprintEntry(Long orchidGroupId, OrchidGroupStateSnapshot snapshot) {
	}
}
