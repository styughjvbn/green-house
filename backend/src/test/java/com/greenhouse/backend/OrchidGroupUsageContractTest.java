package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupUsage;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.spi.orchid.OrchidGroupUsageInspector;
import com.greenhouse.backend.farm.support.FarmTestFixtures;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.domain.document.SalesSlip;
import com.greenhouse.backend.sales.domain.document.SalesSlipItem;
import com.greenhouse.backend.sales.domain.document.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.domain.partner.BusinessPartner;
import com.greenhouse.backend.support.DirectSaleFixtures;
import com.greenhouse.backend.work.api.operation.WorkOperationStatus;
import com.greenhouse.backend.work.api.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.api.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.api.target.WorkTargetInclusionSource;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrchidGroupUsageContractTest {

  @Autowired List<OrchidGroupUsageInspector> inspectors;

  @Autowired EntityManager entityManager;

  @Test
  void translatesWorkReferencesWithoutBlockingTheSourceOperationItself() {
    var fixtures = new FarmTestFixtures(entityManager);
    OrchidGroup group = fixtures.orchidGroup(fixtures.layout(984).left(), "WORK-USAGE", 20);
    var type = new WorkType("USAGE_TEST", "사용 참조", WorkTypeTemplate.MEMO, false, false, true, 1);
    entityManager.persist(type);
    WorkOperation source = operation(type, group);
    assertThat(inspect(group.getId(), source.getId())).isEmpty();

    operation(type, group);
    operation(type, group);

    assertThat(inspect(group.getId(), source.getId()))
        .containsExactly(new OrchidGroupUsage("WORK_OPERATION", "다른 작업에 포함된 난 묶음이 있습니다.", 2));
  }

  @Test
  void discoversSalesReferencesThroughTheFarmOwnedPort() {
    var fixtures = new FarmTestFixtures(entityManager);
    OrchidGroup group = fixtures.orchidGroup(fixtures.layout(985).left(), "SALES-USAGE", 20);
    var partner = new BusinessPartner("참조 거래처", PartnerType.WHOLESALE, null, null, null, null);
    entityManager.persist(partner);
    var slip =
        new SalesSlip(
            "USAGE-TEST",
            LocalDate.of(2026, 9, 5),
            SalesType.DIRECT,
            null,
            partner.getId(),
            "미입금",
            SalesSlip.STATUS_DRAFT,
            null,
            null);
    var item = new SalesSlipItem(null, group.getVarietyName(), null, null, 2, 1000, null);
    item.addAllocation(new SalesSlipItemAllocation(group.getId(), 2));
    slip.addItem(item);
    DirectSaleFixtures.refreshProjection(slip);
    entityManager.persist(slip);

    assertThat(inspect(group.getId(), -1L))
        .containsExactly(new OrchidGroupUsage("SALES", "판매 또는 재고 이동에 연결된 난 묶음이 있습니다.", 1));

    var type = new WorkType("USAGE_TEST", "사용 참조", WorkTypeTemplate.MEMO, false, false, true, 1);
    entityManager.persist(type);
    operation(type, group);
    assertThat(inspect(group.getId(), -1L))
        .extracting(OrchidGroupUsage::code)
        .containsExactly("SALES", "WORK_OPERATION");
  }

  @Test
  void ignoresCanceledAndVoidedWorkReferences() {
    var fixtures = new FarmTestFixtures(entityManager);
    OrchidGroup group = fixtures.orchidGroup(fixtures.layout(986).left(), "VOIDED-WORK-USAGE", 20);
    var type = new WorkType("MOVEMENT", "자리 이동", WorkTypeTemplate.MOVEMENT, true, true, true, 1);
    entityManager.persist(type);
    WorkOperation source = operation(type, group);
    WorkOperation canceled = operation(type, group);
    canceled.cancel(LocalDateTime.of(2026, 9, 5, 1, 0));
    WorkOperation voided = operation(type, group);
    voided.complete(LocalDateTime.of(2026, 9, 5, 1, 0));
    voided.voidCompletedMutationWork(
        LocalDateTime.of(2026, 9, 5, 2, 0), "잘못 등록", "void-request", 100L);

    assertThat(canceled.getStatus()).isEqualTo(WorkOperationStatus.CANCELED);
    assertThat(voided.getStatus()).isEqualTo(WorkOperationStatus.VOIDED);
    assertThat(inspect(group.getId(), source.getId())).isEmpty();
  }

  private List<OrchidGroupUsage> inspect(Long groupId, Long sourceOperationId) {
    entityManager.flush();
    return inspectors.stream()
        .flatMap(inspector -> inspector.inspect(Set.of(groupId), sourceOperationId).stream())
        .toList();
  }

  private WorkOperation operation(WorkType type, OrchidGroup group) {
    LocalDate date = LocalDate.of(2026, 9, 5);
    LocalDateTime timestamp = date.atStartOfDay();
    var operation =
        new WorkOperation(
            type,
            "참조 작업",
            date,
            date,
            WorkSourceScopeType.ORCHID_GROUP,
            group.getId(),
            Map.of(),
            Map.of(),
            "worker",
            null,
            timestamp);
    entityManager.persist(operation);
    entityManager.persist(
        new WorkOperationTarget(
            operation,
            group.getId(),
            WorkTargetInclusionSource.DIRECT,
            group.getId(),
            group.getVariety().getId(),
            group.getVarietyName(),
            group.getAgeYear(),
            group.getPotSizeCode().name(),
            group.getPotSize(),
            group.getQuantity(),
            Map.of(),
            timestamp));
    return operation;
  }
}
