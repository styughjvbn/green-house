package com.greenhouse.backend.farm.application.status;

import com.greenhouse.backend.farm.repository.structure.BedZoneRepository;
import com.greenhouse.backend.farm.repository.structure.HouseRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.structure.PhysicalBedRepository;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroupStatusPolicy;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FarmMetricsReader {
	private final HouseRepository houseRepository;
	private final PhysicalBedRepository physicalBedRepository;
	private final BedZoneRepository bedZoneRepository;
	private final OrchidGroupRepository orchidGroupRepository;

	public InventorySummary getInventorySummary() {
		var varieties = orchidGroupRepository.summarizeInventory(
				OrchidGroupStatusPolicy.unavailableForSaleStatuses(), OrchidGroupStatusPolicy.warningStatuses())
				.stream().map(row -> new VarietyInventory(
						row.getVarietyName(), row.getSaleableQuantity(), row.getWarningGroupCount()))
				.toList();
		return new InventorySummary(varieties.stream().mapToLong(VarietyInventory::saleableQuantity).sum(), varieties);
	}

	public Snapshot getSnapshot() {
		return new Snapshot(
				houseRepository.count(),
				physicalBedRepository.count(),
				bedZoneRepository.count(),
				orchidGroupRepository.count(),
				orchidGroupRepository.countWarningStatus(OrchidGroupStatusPolicy.warningStatuses()));
	}

	public record Snapshot(
			long houseCount,
			long physicalBedCount,
			long bedZoneCount,
			long orchidGroupCount,
			long warningCount) {
	}

	public record InventorySummary(long saleableQuantity, List<VarietyInventory> varieties) {
		public InventorySummary {
			varieties = List.copyOf(varieties);
		}
	}

	public record VarietyInventory(String varietyName, long saleableQuantity, long warningGroupCount) {
	}
}
