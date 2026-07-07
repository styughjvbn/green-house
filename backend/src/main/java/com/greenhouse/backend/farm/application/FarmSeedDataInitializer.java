package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.farm.domain.BedZone;
import com.greenhouse.backend.farm.domain.BedZoneSide;
import com.greenhouse.backend.farm.domain.House;
import com.greenhouse.backend.farm.domain.Material;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.domain.PhysicalBed;
import com.greenhouse.backend.farm.domain.Variety;
import com.greenhouse.backend.farm.repository.BedZoneRepository;
import com.greenhouse.backend.farm.repository.HouseRepository;
import com.greenhouse.backend.farm.repository.MaterialRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.VarietyRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FarmSeedDataInitializer implements CommandLineRunner {

	private final HouseRepository houseRepository;
	private final BedZoneRepository bedZoneRepository;
	private final OrchidGroupRepository orchidGroupRepository;
	private final VarietyRepository varietyRepository;
	private final MaterialRepository materialRepository;

	@Override
	@Transactional
	public void run(String... args) {
		if (houseRepository.count() == 0) {
			houseRepository.saveAll(createFarmStructure());
		}

		if (!orchidGroupRepository.existsByVarietyName("카틀레야 A")) {
			seedSampleOrchidGroups();
		}

		if (materialRepository.count() == 0) {
			seedSampleMaterials();
		}

		orchidGroupRepository.findByVarietyIsNull().forEach(this::bindExistingVariety);
	}

	private void bindExistingVariety(OrchidGroup orchidGroup) {
		varietyRepository.findByGenusAndName(orchidGroup.getGenus(), orchidGroup.getVarietyName())
				.ifPresent(orchidGroup::assignVariety);
	}

	private List<House> createFarmStructure() {
		List<House> houses = new ArrayList<>();
		for (int houseNumber = 1; houseNumber <= 15; houseNumber++) {
			House house = new House(houseNumber, houseNumber + "동");
			for (int bedNumber = 1; bedNumber <= 3; bedNumber++) {
				PhysicalBed physicalBed = new PhysicalBed(bedNumber, bedNumber);
				physicalBed.updatePositionUnits(bedNumber == 3 ? BigDecimal.valueOf(28) : BigDecimal.valueOf(24), "치");
				physicalBed.addBedZone(new BedZone(bedNumber + "다이 좌", BedZoneSide.LEFT, 1));
				physicalBed.addBedZone(new BedZone(bedNumber + "다이 우", BedZoneSide.RIGHT, 2));
				house.addPhysicalBed(physicalBed);
			}
			houses.add(house);
		}
		return houses;
	}

	private void seedSampleOrchidGroups() {
		BedZone sampleZone = bedZoneRepository.findSeedZone(3, 2, BedZoneSide.LEFT)
				.orElseThrow(() -> new IllegalStateException("Sample seed zone was not created."));

		if (varietyRepository.count() == 0) {
			varietyRepository.saveAll(List.of(
					new Variety("VAR-0001", "카틀레야", "카틀레야 A", null, "4치", true, true, "기본 카틀레야 품종", null),
					new Variety("VAR-0002", "카틀레야", "카틀레야 B", null, "5치", true, true, "개화 관리 품종", null),
					new Variety("VAR-0003", "덴드로비움", "덴드로비움 C", null, "3.5치", true, true, "초기 생육 품종", null)));
		}
		var varieties = varietyRepository.findAll().stream()
				.collect(java.util.stream.Collectors.toMap(Variety::getName, variety -> variety));

		var groupA = new OrchidGroup(sampleZone, "카틀레야", "카틀레야 A", 120, "4치", 2, "정상", 1,
				BigDecimal.ZERO, BigDecimal.valueOf(7));
		groupA.assignVariety(varieties.get("카틀레야 A"));
		groupA.updateDetails("카틀레야", "카틀레야 A", 120, "4치", 2, "정상", "TRAY_20", 6, true,
				BigDecimal.ZERO, BigDecimal.valueOf(7), null);

		var groupB = new OrchidGroup(sampleZone, "카틀레야", "카틀레야 B", 80, "5치", 3, "개화중", 2,
				BigDecimal.valueOf(7), BigDecimal.valueOf(14));
		groupB.assignVariety(varieties.get("카틀레야 B"));
		groupB.updateDetails("카틀레야", "카틀레야 B", 80, "5치", 3, "개화중", "TRAY_20", 4, true,
				BigDecimal.valueOf(7), BigDecimal.valueOf(14), null);

		var groupC = new OrchidGroup(sampleZone, "덴드로비움", "덴드로비움 C", 200, "3.5치", 1, "판매 가능", 3,
				BigDecimal.valueOf(14), BigDecimal.valueOf(21));
		groupC.assignVariety(varieties.get("덴드로비움 C"));
		groupC.updateDetails("덴드로비움", "덴드로비움 C", 200, "3.5치", 1, "판매 가능", "TRAY_20", 10, true,
				BigDecimal.valueOf(14), BigDecimal.valueOf(21), null);

		orchidGroupRepository.saveAll(List.of(groupA, groupB, groupC));
	}

	private void seedSampleMaterials() {
		materialRepository.saveAll(List.of(
				new Material("MAT-0001", "농약", "응애 약제", "그린팜", "500ml", "2병", "창고 A", "응애 관리 희석 사용", true),
				new Material("MAT-0002", "비료", "개화 비료", "농원케어", "1kg", "3포", "창고 B", "개화기 주기 사용", true),
				new Material("MAT-0003", "자재", "20구 트레이", "원예자재", "20구", "12개", "선반 C", "분갈이 작업용", true)));
	}
}
