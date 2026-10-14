package com.greenhouse.backend.work.spi.correction;

import com.greenhouse.backend.work.api.correction.StructureChangeMutationReferences;
import com.greenhouse.backend.work.api.correction.WorkQuantityBalanceChange;
import com.greenhouse.backend.work.api.effect.WorkEffectResults;
import java.util.List;

/** Values prepared under Farm locks, retained in the calling Work transaction. */
public record WorkCorrectionPlan(
    List<WorkEffectResults.Adjustment> adjustments,
    List<WorkQuantityBalanceChange> quantityBalances,
    StructureChangeMutationReferences mutationReferences) {

  public WorkCorrectionPlan {
    adjustments = List.copyOf(adjustments);
    quantityBalances = List.copyOf(quantityBalances);
  }

  public static WorkCorrectionPlan noChanges() {
    return new WorkCorrectionPlan(List.of(), List.of(), StructureChangeMutationReferences.legacy());
  }

  public boolean hasChanges() {
    return !adjustments.isEmpty() || !quantityBalances.isEmpty();
  }
}
