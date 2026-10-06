package com.greenhouse.backend.support;

import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationReport;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverage;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;

/** Synthetic ledger setup for isolated tests; no manifest import, replay or operator workflow. */
@TestComponent
@RequiredArgsConstructor
@Transactional
public class OrchidGroupLedgerTestFixture {
  private static final Instant TIME = Instant.parse("2026-08-20T00:00:00Z");
  private final OrchidGroupRepository groups;
  private final OrchidGroupLedgerCoverageRepository coverages;
  private final OrchidGroupMutationRepository mutations;
  private final OrchidGroupMutationEntryRepository entries;
  private final OrchidGroupLedgerReconciliationService reconciliation;

  public int seedBaseline(UUID key, LocalDate date, String writerVersion) {
    var coverage = new OrchidGroupLedgerCoverage(key, 1, 1, date, writerVersion);
    coverage.startImport(TIME);
    coverage.claimImport("b".repeat(64));
    coverages.save(coverage);
    var mutation =
        mutations.save(
            new OrchidGroupMutation(
                OrchidGroupMutationType.BASELINE_IMPORT,
                new OrchidGroupMutationSource(
                    OrchidGroupMutationSourceDomain.MIGRATION,
                    "LEDGER_BASELINE",
                    key.toString(),
                    "test-baseline-" + key,
                    key),
                "b".repeat(64),
                TIME,
                date,
                "Synthetic test baseline",
                1));
    var ids = groups.findAll().stream().map(group -> group.getId()).toList();
    for (var group : groups.findDetailsByIds(ids)) {
      group.establishBaselineRevision();
      entries.save(
          OrchidGroupMutationEntry.baseline(
              mutation, group.getId(), OrchidGroupStateSnapshot.from(group)));
    }
    entries.flush();
    return 1;
  }

  public OrchidGroupLedgerReconciliationReport activate(UUID key) {
    var report = reconciliation.reconcile();
    if (!report.ready() || !key.equals(report.cutoverKey())) {
      throw new IllegalStateException("Invalid synthetic ledger fixture: " + report.issues());
    }
    var coverage = coverages.findByCutoverKey(key).orElseThrow();
    coverage.activate(TIME, report.baselineGroupCount(), report.baselineFingerprint());
    coverages.flush();
    return reconciliation.reconcile();
  }
}
