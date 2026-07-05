package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.farm.domain.BedZone;
import com.greenhouse.backend.farm.domain.BedZoneSide;
import com.greenhouse.backend.farm.domain.BedZoneSegment;
import com.greenhouse.backend.farm.domain.BedZoneSegmentType;
import com.greenhouse.backend.farm.domain.House;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.domain.PhysicalBed;
import com.greenhouse.backend.farm.repository.BedZoneRepository;
import com.greenhouse.backend.farm.repository.HouseRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FarmSeedDataInitializer implements CommandLineRunner {

	private final HouseRepository houseRepository;
	private final BedZoneRepository bedZoneRepository;
	private final OrchidGroupRepository orchidGroupRepository;

	public FarmSeedDataInitializer(
		HouseRepository houseRepository,
		BedZoneRepository bedZoneRepository,
		OrchidGroupRepository orchidGroupRepository
	) {
		this.houseRepository = houseRepository;
		this.bedZoneRepository = bedZoneRepository;
		this.orchidGroupRepository = orchidGroupRepository;
	}

	@Override
	@Transactional
	public void run(String... args) {
		if (houseRepository.count() == 0) {
			houseRepository.saveAll(createFarmStructure());
		}

		if (!orchidGroupRepository.existsByVarietyName("카틀레야 A")) {
			seedSampleOrchidGroups();
		}

		bedZoneRepository.findAll().stream()
			.filter(zone -> zone.getSegments().isEmpty())
			.forEach(this::addDefaultSegments);
	}

	private void addDefaultSegments(BedZone zone) {
		zone.addSegment(new BedZoneSegment("앞 구간", BedZoneSegmentType.START, 1, null));
		zone.addSegment(new BedZoneSegment("중간1", BedZoneSegmentType.MIDDLE, 2, null));
		zone.addSegment(new BedZoneSegment("중간2", BedZoneSegmentType.MIDDLE, 3, null));
		zone.addSegment(new BedZoneSegment("중간3", BedZoneSegmentType.MIDDLE, 4, null));
		zone.addSegment(new BedZoneSegment("끝 구간", BedZoneSegmentType.END, 5, null));
	}

	private List<House> createFarmStructure() {
		List<House> houses = new ArrayList<>();
		for (int houseNumber = 1; houseNumber <= 15; houseNumber++) {
			House house = new House(houseNumber, houseNumber + "동");
			for (int bedNumber = 1; bedNumber <= 3; bedNumber++) {
				PhysicalBed physicalBed = new PhysicalBed(bedNumber, bedNumber);
				physicalBed.addBedZone(new BedZone(bedNumber + "배드 좌", BedZoneSide.LEFT, 1));
				physicalBed.addBedZone(new BedZone(bedNumber + "배드 우", BedZoneSide.RIGHT, 2));
				house.addPhysicalBed(physicalBed);
			}
			houses.add(house);
		}
		return houses;
	}

	private void seedSampleOrchidGroups() {
		BedZone sampleZone = bedZoneRepository.findSeedZone(3, 2, BedZoneSide.LEFT)
			.orElseThrow(() -> new IllegalStateException("Sample seed zone was not created."));

		orchidGroupRepository.saveAll(List.of(
			new OrchidGroup(sampleZone, "카틀레야", "카틀레야 A", 120, "4치", 2, "정상", 1),
			new OrchidGroup(sampleZone, "카틀레야", "카틀레야 B", 80, "5치", 3, "개화중", 2),
			new OrchidGroup(sampleZone, "덴드로비움", "덴드로비움 C", 200, "3.5치", 1, "판매 가능", 3)
		));
	}
}
