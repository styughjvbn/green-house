package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.application.status.FarmMetricsReader;
import com.greenhouse.backend.farm.application.status.FarmMetricsReader.VarietyInventory;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.work.application.operation.WorkOperationMetricsReader;
import com.greenhouse.backend.work.application.operation.WorkOperationMetricsReader.RecentRecord;
import com.greenhouse.backend.work.application.operation.WorkOperationMetricsReader.TypeCount;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.operation.WorkTypeTemplate;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Tag("work-e2e")
@Transactional
class AnalyticsMetricsPostgresE2ETest extends WorkE2ETestBase {

	private static final LocalDate FROM = LocalDate.of(2040, 7, 1);
	private static final LocalDate TO = LocalDate.of(2040, 7, 31);

	@Autowired private EntityManager entityManager;
	@Autowired private WorkOperationMetricsReader workMetrics;
	@Autowired private FarmMetricsReader farmMetrics;

	@Test
	void countsCompletedAndCorrectedWorkByPlannedStartAndCurrentTypeName() {
		var movement = type("METRICS_MOVE", "Shared", WorkTypeTemplate.MOVEMENT);
		var status = type("METRICS_STATUS", "Old name", WorkTypeTemplate.STATUS);
		var repot = type("METRICS_REPOT", "Repot", WorkTypeTemplate.REPOT);
		work(movement, FROM, WorkOperationStatus.COMPLETED);
		work(status, TO, WorkOperationStatus.COMPLETED);
		var corrected = work(repot, TO, WorkOperationStatus.CORRECTED);
		work(movement, FROM.minusDays(1), WorkOperationStatus.COMPLETED);
		work(movement, TO.plusDays(1), WorkOperationStatus.COMPLETED);
		for (var excluded : new WorkOperationStatus[] { WorkOperationStatus.PLANNED,
				WorkOperationStatus.IN_PROGRESS, WorkOperationStatus.PAUSED, WorkOperationStatus.CANCELED }) {
			work(movement, TO, excluded);
		}
		status.update("Shared", WorkTypeTemplate.STATUS, false);
		flushAndResetStatistics();

		var summary = workMetrics.getSummary(FROM, TO);

		assertThat(summary.totalCount()).isEqualTo(3);
		assertThat(summary.movementCount()).isEqualTo(1);
		assertThat(summary.statusCount()).isEqualTo(1);
		assertThat(summary.latestWorkDate()).isEqualTo(TO);
		assertThat(summary.typeCounts()).containsExactly(new TypeCount("Shared", 2), new TypeCount("Repot", 1));
		assertThat(summary.recentRecords()).hasSize(3);
		assertThat(summary.recentRecords().getFirst()).isEqualTo(new RecentRecord(
				corrected.getId(), TO, "Repot", WorkTypeTemplate.REPOT, "통계 작업",
				WorkSourceScopeType.FARM, "작업자", "메모", WorkOperationStatus.CORRECTED));
		assertThat(summary.recentRecords().get(1).workType()).isEqualTo("Shared");
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 10, 50 })
	void workSummaryUsesTwoScalarQueriesAndKeepsTheLatestTen(int size) {
		var ids = new ArrayList<Long>();
		for (int index = 0; index < size; index++) {
			var type = type("METRICS_" + index, "Type " + index, WorkTypeTemplate.MEMO);
			ids.add(work(type, TO, WorkOperationStatus.COMPLETED).getId());
		}
		var statistics = flushAndResetStatistics();

		var summary = workMetrics.getSummary(FROM, TO);

		assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
		assertThat(statistics.getEntityLoadCount()).isZero();
		assertThat(summary.totalCount()).isEqualTo(size);
		assertThat(summary.typeCounts()).hasSize(size).isSortedAccordingTo(Comparator.comparing(TypeCount::name));
		assertThat(summary.recentRecords()).extracting(RecentRecord::id)
				.containsExactlyElementsOf(ids.reversed().stream().limit(10).toList());
	}

	@Test
	void inventoryMergesNamesAndExcludesReservationsAndUnavailableStatuses() {
		var zone = zone();
		var reserved = group(zone, "Shared", 20, "정상");
		reserved.reserve(7);
		group(zone, "Shared", 10, "정상");
		group(zone, "Shared", 9, "주의");
		group(zone, "Shared", 8, "이상");
		group(zone, "Shared", 7, "병해충");
		group(zone, "Zero", 0, "주의");
		for (var status : new String[] { "종료", "폐기", "판매 완료", "생성 취소" }) {
			group(zone, "Unavailable", 50, status);
		}
		group(zone, "Reserved", 5, "정상").reserve(5);
		group(zone, "Custom", 23, "개화");
		flushAndResetStatistics();

		var inventory = farmMetrics.getInventorySummary();

		assertThat(inventory.saleableQuantity()).isEqualTo(46);
		assertThat(inventory.varieties()).containsExactly(
				new VarietyInventory("Custom", 23, 0), new VarietyInventory("Shared", 23, 3),
				new VarietyInventory("Reserved", 0, 0), new VarietyInventory("Unavailable", 0, 0));
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 10, 50 })
	void inventoryUsesOneScalarQueryWithLongSums(int size) {
		var zone = zone();
		for (int index = 0; index < size; index++) {
			group(zone, "Variety " + index, 1_500_000_000, "정상");
			group(zone, "Variety " + index, 1_500_000_000, "정상");
		}
		var statistics = flushAndResetStatistics();

		var inventory = farmMetrics.getInventorySummary();

		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
		assertThat(statistics.getEntityLoadCount()).isZero();
		assertThat(inventory.saleableQuantity()).isEqualTo(3_000_000_000L * size);
		assertThat(inventory.varieties()).hasSize(size).allSatisfy(variety -> {
			assertThat(variety.saleableQuantity()).isEqualTo(3_000_000_000L);
			assertThat(variety.warningGroupCount()).isZero();
		});
	}

	@Test
	void emptySummariesHaveZeroCountsAndNoLatestDate() {
		var work = workMetrics.getSummary(FROM, TO);
		assertThat(work.totalCount()).isZero();
		assertThat(work.movementCount()).isZero();
		assertThat(work.statusCount()).isZero();
		assertThat(work.latestWorkDate()).isNull();
		assertThat(work.typeCounts()).isEmpty();
		assertThat(work.recentRecords()).isEmpty();
		var inventory = farmMetrics.getInventorySummary();
		assertThat(inventory.saleableQuantity()).isZero();
		assertThat(inventory.varieties()).isEmpty();
	}

	private WorkType type(String code, String name, WorkTypeTemplate template) {
		var type = new WorkType(code, name, template, false, false, true, 100);
		entityManager.persist(type);
		return type;
	}

	private WorkOperation work(WorkType type, LocalDate date, WorkOperationStatus status) {
		var actualTime = TO.plusDays(5).atStartOfDay();
		var work = new WorkOperation(type, "통계 작업", date, TO.plusDays(10), WorkSourceScopeType.FARM,
				null, null, null, "작업자", "메모", actualTime);
		switch (status) {
			case COMPLETED -> work.complete(actualTime);
			case CORRECTED -> {
				work.complete(actualTime);
				work.markCorrected();
			}
			case IN_PROGRESS -> work.start(actualTime);
			case PAUSED -> {
				work.start(actualTime);
				work.pause();
			}
			case CANCELED -> work.cancel(actualTime);
			case PLANNED -> { }
		}
		entityManager.persist(work);
		return work;
	}

	private BedZone zone() {
		var house = new House(990, "통계 테스트동");
		var bed = new PhysicalBed(1, 1);
		var zone = new BedZone("구역", BedZoneSide.LEFT, 1);
		bed.addBedZone(zone);
		house.addPhysicalBed(bed);
		entityManager.persist(house);
		return zone;
	}

	private OrchidGroup group(BedZone zone, String variety, int quantity, String status) {
		var group = new OrchidGroup(zone, "카틀레야", variety, quantity, "3.5치", 2, status, 1, null, null);
		entityManager.persist(group);
		return group;
	}

	private Statistics flushAndResetStatistics() {
		entityManager.flush();
		entityManager.clear();
		var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		return statistics;
	}
}
