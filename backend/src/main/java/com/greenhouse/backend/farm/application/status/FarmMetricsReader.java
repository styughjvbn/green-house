package com.greenhouse.backend.farm.application.status;

import com.greenhouse.backend.farm.api.status.FarmMetricsApi;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroupStatusPolicy;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.structure.repository.BedZoneRepository;
import com.greenhouse.backend.farm.structure.repository.HouseRepository;
import com.greenhouse.backend.farm.structure.repository.PhysicalBedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FarmMetricsReader implements FarmMetricsApi {

  private final HouseRepository houseRepository;

  private final PhysicalBedRepository physicalBedRepository;

  private final BedZoneRepository bedZoneRepository;

  private final OrchidGroupRepository orchidGroupRepository;

  @Override
  public InventorySummary getInventorySummary() {
    var varieties =
        orchidGroupRepository
            .summarizeInventory(
                OrchidGroupStatusPolicy.unavailableForSaleStatuses(),
                OrchidGroupStatusPolicy.warningStatuses())
            .stream()
            .map(
                row ->
                    new VarietyInventory(
                        row.getVarietyName(),
                        row.getSaleableQuantity(),
                        row.getWarningGroupCount()))
            .toList();
    return new InventorySummary(
        varieties.stream().mapToLong(VarietyInventory::saleableQuantity).sum(), varieties);
  }

  @Override
  public Snapshot getSnapshot() {
    return new Snapshot(
        houseRepository.count(),
        physicalBedRepository.count(),
        bedZoneRepository.count(),
        orchidGroupRepository.count(),
        orchidGroupRepository.countWarningStatus(OrchidGroupStatusPolicy.warningStatuses()));
  }
}
