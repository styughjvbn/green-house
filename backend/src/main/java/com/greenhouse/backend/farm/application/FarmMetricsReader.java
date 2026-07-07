package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.farm.repository.BedZoneRepository;
import com.greenhouse.backend.farm.repository.HouseRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.PhysicalBedRepository;

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

	public Snapshot getSnapshot() {
		return new Snapshot(
				houseRepository.count(),
				physicalBedRepository.count(),
				bedZoneRepository.count(),
				orchidGroupRepository.count(),
				orchidGroupRepository.countWarningStatus());
	}

	public record Snapshot(
			long houseCount,
			long physicalBedCount,
			long bedZoneCount,
			long orchidGroupCount,
			long warningCount) {
	}
}
