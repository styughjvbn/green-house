package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupBatchUpdateItem;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupBatchUpdateRequest;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupUpdateRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrchidGroupBatchUpdateIntegrationTest extends AbstractBackendIntegrationTest {
	@Autowired OrchidGroupCommandService commandService;

	@Test
	void updatesEverySelectedOrchidGroupInOneRequest() {
		House house = new House(9910, "일괄 보정동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		bed.updatePositionUnits(new BigDecimal("20"), "칸");
		BedZone zone = new BedZone("왼쪽", BedZoneSide.LEFT, 1);
		bed.addBedZone(zone);
		house.addPhysicalBed(bed);
		houseRepository.saveAndFlush(house);
		Variety variety = varietyRepository.saveAndFlush(new Variety(
				"BATCH-" + System.nanoTime(), "Phal", "일괄품종", null, "4인치", true, true, null, null));
		OrchidGroup first = saveGroup(zone, variety, 10, 1, 2);
		OrchidGroup second = saveGroup(zone, variety, 20, 3, 4);

		commandService.updateBatch(new OrchidGroupBatchUpdateRequest(List.of(
				new OrchidGroupBatchUpdateItem(first.getId(), update(variety.getId(), 15, 1, 2)),
				new OrchidGroupBatchUpdateItem(second.getId(), update(variety.getId(), 20, 3, 4)))));

		assertThat(orchidGroupRepository.findById(first.getId()).orElseThrow().getQuantity()).isEqualTo(15);
		assertThat(orchidGroupRepository.findById(second.getId()).orElseThrow().getQuantity()).isEqualTo(20);
		assertThat(orchidGroupRepository.findById(first.getId()).orElseThrow().getAgeYear()).isEqualTo(4);
		assertThat(orchidGroupRepository.findById(second.getId()).orElseThrow().getAgeYear()).isEqualTo(4);
	}

	@Test
	void rollsBackEveryUpdateWhenOneSelectedOrchidGroupFails() {
		House house = new House(9911, "일괄 보정 롤백동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		bed.updatePositionUnits(new BigDecimal("20"), "칸");
		BedZone zone = new BedZone("왼쪽", BedZoneSide.LEFT, 1);
		bed.addBedZone(zone);
		house.addPhysicalBed(bed);
		houseRepository.saveAndFlush(house);
		Variety variety = varietyRepository.saveAndFlush(new Variety(
				"BATCH-ROLLBACK-" + System.nanoTime(), "Phal", "롤백품종", null, "4인치", true, true, null, null));
		OrchidGroup first = saveGroup(zone, variety, 10, 1, 2);

		assertThatThrownBy(() -> commandService.updateBatch(new OrchidGroupBatchUpdateRequest(List.of(
				new OrchidGroupBatchUpdateItem(first.getId(), update(variety.getId(), 99, 1, 2)),
				new OrchidGroupBatchUpdateItem(Long.MAX_VALUE, update(variety.getId(), 20, 3, 4))))))
				.hasMessage("난 묶음을 찾을 수 없습니다.");

		assertThat(orchidGroupRepository.findById(first.getId()).orElseThrow().getQuantity()).isEqualTo(10);
	}

	private OrchidGroup saveGroup(BedZone zone, Variety variety, int quantity, int start, int end) {
		OrchidGroup group = new OrchidGroup(zone, variety.getGenus(), variety.getName(), quantity, "4인치", 2,
				"정상", start, BigDecimal.valueOf(start), BigDecimal.valueOf(end));
		group.assignVariety(variety);
		return orchidGroupRepository.saveAndFlush(group);
	}

	private OrchidGroupUpdateRequest update(Long varietyId, int quantity, int start, int end) {
		return new OrchidGroupUpdateRequest(varietyId, quantity, "4인치", 4, "정상", null, null, false,
				BigDecimal.valueOf(start), BigDecimal.valueOf(end), null);
	}
}
