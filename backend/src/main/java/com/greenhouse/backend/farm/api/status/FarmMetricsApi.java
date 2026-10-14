package com.greenhouse.backend.farm.api.status;

import java.util.List;

public interface FarmMetricsApi {

  InventorySummary getInventorySummary();

  Snapshot getSnapshot();

  public record Snapshot(
      long houseCount,
      long physicalBedCount,
      long bedZoneCount,
      long orchidGroupCount,
      long warningCount) {}

  public record InventorySummary(long saleableQuantity, List<VarietyInventory> varieties) {
    public InventorySummary {
      varieties = List.copyOf(varieties);
    }
  }

  public record VarietyInventory(
      String varietyName, long saleableQuantity, long warningGroupCount) {}
}
