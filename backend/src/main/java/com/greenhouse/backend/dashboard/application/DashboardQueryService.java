package com.greenhouse.backend.dashboard.application;

import com.greenhouse.backend.dashboard.dto.DashboardSummaryResponse;
import com.greenhouse.backend.farm.repository.BedZoneRepository;
import com.greenhouse.backend.farm.repository.HouseRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.PhysicalBedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardQueryService {
	private final HouseRepository houseRepository;
	private final PhysicalBedRepository physicalBedRepository;
	private final BedZoneRepository bedZoneRepository;
	private final OrchidGroupRepository orchidGroupRepository;

	public DashboardQueryService(
		HouseRepository houseRepository,
		PhysicalBedRepository physicalBedRepository,
		BedZoneRepository bedZoneRepository,
		OrchidGroupRepository orchidGroupRepository
	) {
		this.houseRepository = houseRepository;
		this.physicalBedRepository = physicalBedRepository;
		this.bedZoneRepository = bedZoneRepository;
		this.orchidGroupRepository = orchidGroupRepository;
	}

	public DashboardSummaryResponse getSummary() {
		return new DashboardSummaryResponse(
			houseRepository.count(),
			physicalBedRepository.count(),
			bedZoneRepository.count(),
			orchidGroupRepository.count(),
			orchidGroupRepository.countWarningStatus(),
			0,
			null);
	}
}
