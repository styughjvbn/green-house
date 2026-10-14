package com.greenhouse.backend;

import com.greenhouse.backend.farm.material.domain.Material;
import com.greenhouse.backend.farm.orchid.domain.OrchidGroup;
import com.greenhouse.backend.farm.structure.domain.BedZone;
import com.greenhouse.backend.farm.structure.domain.BedZoneSide;
import com.greenhouse.backend.farm.structure.domain.House;
import com.greenhouse.backend.farm.structure.domain.PhysicalBed;
import com.greenhouse.backend.farm.support.FarmTestFixtures;
import com.greenhouse.backend.farm.variety.domain.Variety;
import com.greenhouse.backend.work.api.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.operation.domain.WorkType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Each test owns its data and rolls it back; no application seed or fixed database IDs. */
@org.springframework.test.context.TestPropertySource(
    properties =
        "spring.datasource.url=jdbc:h2:mem:restored-fixtures;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional
abstract class FarmFixtureIntegrationTest extends AbstractBackendIntegrationTest {

  @Autowired protected EntityManager fixtureEntityManager;

  protected List<Integer> fixtureHouseNumbers() {
    return IntStream.rangeClosed(1, 15).boxed().toList();
  }

  @BeforeEach
  void createFarmFixture() {
    BedZone occupiedZone = null;
    for (int number : fixtureHouseNumbers()) {
      var house = new House(number, number + "동");
      for (int bedNumber = 1; bedNumber <= 3; bedNumber++) {
        var bed = new PhysicalBed(bedNumber, bedNumber);
        bed.updatePositionUnits(BigDecimal.valueOf(24), "칸");
        var left = new BedZone(bedNumber + "다이 좌", BedZoneSide.LEFT, 1);
        bed.addBedZone(left);
        bed.addBedZone(new BedZone(bedNumber + "다이 우", BedZoneSide.RIGHT, 2));
        house.addPhysicalBed(bed);
        if (number == 3 && bedNumber == 2) occupiedZone = left;
      }
      fixtureEntityManager.persist(house);
    }
    for (int index = 0; index < 3; index++) {
      var variety =
          new Variety(
              "V-FIX-" + index,
              "카틀레야",
              "카틀레야 " + (char) ('A' + index),
              null,
              "4치",
              true,
              true,
              null,
              null);
      fixtureEntityManager.persist(variety);
      var group =
          new OrchidGroup(
              occupiedZone,
              variety.getGenus(),
              variety.getName(),
              300,
              "4치",
              1,
              "정상",
              index + 1,
              BigDecimal.valueOf(index * 7),
              BigDecimal.valueOf((index + 1) * 7));
      group.assignVariety(variety);
      fixtureEntityManager.persist(group);
      FarmTestFixtures.baseline(fixtureEntityManager, group);
      fixtureEntityManager.persist(
          new Material("M-FIX-" + index, "자재", "자재 " + index, null, null, null, null, null, true));
    }
    for (var template : WorkTypeTemplate.values()) {
      if (workTypeRepository.findByCode(template.name()).isEmpty()) {
        fixtureEntityManager.persist(
            new WorkType(
                template.name(),
                template == WorkTypeTemplate.MOVEMENT ? "자리 이동" : template.name(),
                template,
                true,
                false,
                true,
                template.ordinal()));
      }
    }
    fixtureEntityManager.persist(
        new WorkType("INBOUND", "입고", WorkTypeTemplate.MEMO, true, true, true, 11));
    fixtureEntityManager.persist(
        new WorkType("POTTING", "포트", WorkTypeTemplate.REPOT, true, true, true, 12));
    fixtureEntityManager.flush();
  }
}
