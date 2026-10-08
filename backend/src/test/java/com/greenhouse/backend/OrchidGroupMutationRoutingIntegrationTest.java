package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.application.inbound.InboundPlacementInput;
import com.greenhouse.backend.farm.application.inbound.InboundRecordCreateCommand;
import com.greenhouse.backend.farm.application.inbound.InboundRecordService;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService;
import com.greenhouse.backend.farm.application.transformation.RepotWorkOperationService;
import com.greenhouse.backend.farm.application.variety.VarietyService;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupCreateRequest;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupUpdateRequest;
import com.greenhouse.backend.farm.dto.transformation.RepotResultOrchidGroupRequest;
import com.greenhouse.backend.farm.dto.transformation.RepotWorkOperationRequest;
import com.greenhouse.backend.farm.dto.variety.VarietyUpdateRequest;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRepository;
import com.greenhouse.backend.farm.repository.transformation.OrchidGroupLineageRepository;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.application.document.SalesSlipCreationService;
import com.greenhouse.backend.sales.application.document.SalesSlipStatusService;
import com.greenhouse.backend.sales.application.document.SalesSlipUpdateService;
import com.greenhouse.backend.sales.application.document.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.application.document.command.SalesSlipCommand;
import com.greenhouse.backend.sales.application.document.command.SalesSlipItemInput;
import com.greenhouse.backend.sales.domain.document.SalesInventoryMovementType;
import com.greenhouse.backend.sales.domain.document.SalesSlip;
import com.greenhouse.backend.sales.domain.partner.BusinessPartner;
import com.greenhouse.backend.sales.dto.document.SalesSlipStatusUpdateRequest;
import com.greenhouse.backend.sales.repository.document.SalesInventoryMovementRepository;
import com.greenhouse.backend.sales.repository.partner.BusinessPartnerRepository;
import com.greenhouse.backend.work.api.effect.InboundPottingCommand;
import com.greenhouse.backend.work.api.effect.InboundPottingResultInput;
import com.greenhouse.backend.work.api.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.api.target.WorkTargetSelection;
import com.greenhouse.backend.work.application.operation.DiscardRecordService;
import com.greenhouse.backend.work.application.operation.InboundPottingOperationService;
import com.greenhouse.backend.work.application.operation.WorkOperationPlanService;
import com.greenhouse.backend.work.application.operation.WorkOperationProgressService;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import com.greenhouse.backend.work.dto.effect.DiscardRecordCreateRequest;
import com.greenhouse.backend.work.dto.effect.DiscardRecordResultRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationCreateRequest;
import com.greenhouse.backend.work.dto.target.WorkTargetExecutionRequest;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OrchidGroupMutationRoutingIntegrationTest extends AbstractBackendIntegrationTest {

  @Autowired private OrchidGroupCommandService orchidGroupCommandService;

  @Autowired private InboundRecordService inboundRecordService;

  @Autowired private RepotWorkOperationService repotWorkOperationService;

  @Autowired private DiscardRecordService discardRecordService;

  @Autowired private InboundPottingOperationService inboundPottingOperationService;

  @Autowired private WorkOperationPlanService workOperationPlanService;

  @Autowired private WorkOperationProgressService workOperationProgressService;

  @Autowired private VarietyService varietyService;

  @Autowired private SalesSlipCreationService salesSlipCreationService;

  @Autowired private SalesSlipStatusService salesSlipStatusService;

  @Autowired private SalesSlipUpdateService salesSlipUpdateService;

  @Autowired private OrchidGroupMutationRepository mutationRepository;

  @Autowired private OrchidGroupLineageRepository lineageRepository;

  @Autowired private WorkAppliedEffectRepository workAppliedEffectRepository;

  @Autowired private SalesInventoryMovementRepository salesInventoryMovementRepository;

  @Autowired private BusinessPartnerRepository businessPartnerRepository;

  @Autowired private EntityManager entityManager;

  @Test
  void routesFarmAndImmediateInboundWritesToMutationEngine() {
    Fixture fixture = createFixture(9961, "라우팅 Farm/Inbound");
    ensureWorkType(WorkTypeDefinition.INBOUND.name(), "입고", WorkTypeTemplate.MEMO, 1);

    var created = orchidGroupCommandService.create(groupRequest(fixture, 20, "0", "1", "정상"));
    orchidGroupCommandService.update(
        created.id(),
        new OrchidGroupUpdateRequest(
            fixture.variety().getId(),
            18,
            "4인치",
            2,
            "관리",
            "단일",
            null,
            false,
            new BigDecimal("0"),
            new BigDecimal("1"),
            "상세 수정"));

    var inbound =
        inboundRecordService.create(
            new InboundRecordCreateCommand(
                LocalDate.of(2026, 8, 20),
                InboundType.PRODUCT_POT,
                fixture.variety().getId(),
                null,
                null,
                null,
                null,
                new InboundPlacementInput(
                    7,
                    fixture.zone().getId(),
                    "4인치",
                    2,
                    "단일",
                    null,
                    new BigDecimal("2"),
                    new BigDecimal("3")),
                "입고 담당",
                "즉시 배치"));
    long mutationCountBeforeMetadataUpdate = mutationRepository.count();
    varietyService.update(
        fixture.variety().getId(),
        new VarietyUpdateRequest(
            fixture.variety().getGenus(),
            fixture.variety().getName(),
            "별칭",
            "4인치",
            null,
            true,
            null,
            null));
    assertThat(mutationRepository.count()).isEqualTo(mutationCountBeforeMetadataUpdate);
    varietyService.update(
        fixture.variety().getId(),
        new VarietyUpdateRequest(
            fixture.variety().getGenus(), "라우팅 변경 품종", "별칭", "4인치", null, true, null, null));

    entityManager.flush();
    OrchidGroup updated = orchidGroupRepository.findById(created.id()).orElseThrow();
    OrchidGroup inboundGroup =
        orchidGroupRepository.findById(inbound.createdOrchidGroups().getFirst().id()).orElseThrow();
    assertThat(updated.getStateRevision()).isEqualTo(3L);
    assertThat(inboundGroup.getStateRevision()).isEqualTo(2L);
    assertThat(updated.getVarietyName()).isEqualTo("라우팅 변경 품종");
    assertThat(inboundGroup.getVarietyName()).isEqualTo("라우팅 변경 품종");
    assertThat(inboundGroup.getInboundRecord().getId()).isEqualTo(inbound.id());
    assertThat(mutationRepository.findAll())
        .extracting(mutation -> mutation.getSourceDomain())
        .contains(OrchidGroupMutationSourceDomain.FARM, OrchidGroupMutationSourceDomain.INBOUND);
    assertThat(workAppliedEffectRepository.findAll())
        .anySatisfy(
            effect -> {
              assertThat(effect.getMutationId()).isNotNull();
              assertThat(effect.getCorrelationId()).isNotNull();
            });
  }

  @Test
  void routesRepotWorkAndLinksEffectAndLineageToOneMutation() {
    Fixture fixture = createFixture(9962, "라우팅 Work");
    ensureWorkType(WorkTypeDefinition.REPOT.name(), "분갈이", WorkTypeTemplate.REPOT, 2);
    var source = orchidGroupCommandService.create(groupRequest(fixture, 20, "0", "2", "정상"));

    var response =
        repotWorkOperationService.execute(
            new RepotWorkOperationRequest(
                "routing-repot-9962",
                "라우팅 분갈이",
                LocalDate.of(2026, 8, 20),
                "작업자",
                "부분 분갈이",
                source.id(),
                10,
                List.of(
                    new RepotResultOrchidGroupRequest(
                        fixture.zone().getId(),
                        10,
                        "4인치",
                        2,
                        "단일",
                        null,
                        false,
                        new BigDecimal("2"),
                        new BigDecimal("3"),
                        "결과")),
                Set.of()));

    entityManager.flush();
    var effect =
        workAppliedEffectRepository
            .findByWorkOperationIdOrderByIdAsc(response.operation().id())
            .getFirst();
    var lineage =
        lineageRepository.findBySourceOrchidGroupIdOrderByCreatedAtAscIdAsc(source.id()).getFirst();
    assertThat(effect.getMutationId()).isNotNull();
    assertThat(effect.getCorrelationId()).isNotNull();
    assertThat(lineage.getMutationId()).isEqualTo(effect.getMutationId());
    assertThat(orchidGroupRepository.findById(source.id()).orElseThrow().getStateRevision())
        .isEqualTo(2L);
    assertThat(
            orchidGroupRepository
                .findById(response.resultOrchidGroups().getFirst().id())
                .orElseThrow()
                .getStateRevision())
        .isEqualTo(1L);
  }

  @Test
  void routesSalesReservationOutboundAndCompensationWithMovementLinks() {
    Fixture fixture = createFixture(9963, "라우팅 Sales");
    var group = orchidGroupCommandService.create(groupRequest(fixture, 20, "0", "2", "정상"));
    BusinessPartner partner =
        businessPartnerRepository.save(
            new BusinessPartner("라우팅 판매처", PartnerType.WHOLESALE, null, null, null, null));
    var slip =
        salesSlipCreationService.create(
            new SalesSlipCommand(
                LocalDate.of(2026, 8, 20),
                SalesType.DIRECT,
                partner.getId(),
                null,
                "미입금",
                SalesSlip.STATUS_DRAFT,
                null,
                "엔진 판매",
                List.of(
                    new SalesSlipItemInput(
                        fixture.variety().getName(),
                        fixture.variety().getGenus(),
                        "4인치",
                        3,
                        10_000,
                        null,
                        List.of(new SalesSlipAllocationInput(group.id(), 3))))));
    salesSlipUpdateService.update(
        slip.id(),
        new SalesSlipCommand(
            LocalDate.of(2026, 8, 20),
            SalesType.DIRECT,
            partner.getId(),
            null,
            "미입금",
            SalesSlip.STATUS_DRAFT,
            null,
            "엔진 판매 수정",
            List.of(
                new SalesSlipItemInput(
                    fixture.variety().getName(),
                    fixture.variety().getGenus(),
                    "4인치",
                    4,
                    10_000,
                    null,
                    List.of(new SalesSlipAllocationInput(group.id(), 4))))));

    salesSlipStatusService.updateStatus(
        slip.id(),
        new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_DIRECT_OUTBOUND_COMPLETED, null));
    salesSlipStatusService.updateStatus(
        slip.id(), new SalesSlipStatusUpdateRequest(SalesSlip.STATUS_CANCELED, null));
    entityManager.flush();

    OrchidGroup restored = orchidGroupRepository.findById(group.id()).orElseThrow();
    assertThat(restored.getQuantity()).isEqualTo(20);
    assertThat(restored.getReservedQuantity()).isZero();
    assertThat(restored.getStateRevision()).isEqualTo(6L);
    assertThat(salesInventoryMovementRepository.findAll())
        .filteredOn(movement -> movement.getSalesSlip().getId().equals(slip.id()))
        .hasSize(5)
        .allSatisfy(
            movement -> {
              assertThat(movement.getMutationId()).isNotNull();
              assertThat(movement.getCorrelationId()).isNotNull();
            });
    assertThat(
            salesInventoryMovementRepository.findBySalesSlipIdAndChangeType(
                slip.id(), SalesInventoryMovementType.SALES_CANCEL_OUTBOUND))
        .hasSize(1);
  }

  @Test
  void routesDiscardEffectAndLinksItsMutation() {
    Fixture fixture = createFixture(9965, "라우팅 폐기");
    ensureWorkType(WorkTypeDefinition.DISCARD.name(), "폐기", WorkTypeTemplate.DISCARD, 5);
    var group = orchidGroupCommandService.create(groupRequest(fixture, 15, "0", "1", "정상"));
    WorkType discardType =
        workTypeRepository.findByCode(WorkTypeDefinition.DISCARD.name()).orElseThrow();

    var operation =
        discardRecordService
            .create(
                new DiscardRecordCreateRequest(
                    new WorkOperationCreateRequest(
                        discardType.getId(),
                        "라우팅 폐기",
                        LocalDate.of(2026, 8, 20),
                        LocalDate.of(2026, 8, 20),
                        WorkTargetSelection.orchidGroup(group.id()),
                        Map.of(),
                        "작업자",
                        null,
                        List.of()),
                    LocalDate.of(2026, 8, 20),
                    "작업자",
                    List.of(new DiscardRecordResultRequest(group.id(), 5, "상태 불량"))))
            .getFirst();

    var effect =
        workAppliedEffectRepository.findByWorkOperationIdOrderByIdAsc(operation.id()).getFirst();
    OrchidGroup discarded = orchidGroupRepository.findById(group.id()).orElseThrow();
    assertThat(discarded.getQuantity()).isEqualTo(10);
    assertThat(discarded.getStateRevision()).isEqualTo(2L);
    assertThat(effect.getMutationId()).isNotNull();
    assertThat(effect.getCorrelationId()).isNotNull();
  }

  @Test
  void routesInboundPottingResultsThroughWorkMutation() {
    Fixture fixture = createFixture(9966, "라우팅 포트 작업");
    ensureWorkType(WorkTypeDefinition.INBOUND.name(), "입고", WorkTypeTemplate.MEMO, 6);
    ensureWorkType(WorkTypeDefinition.POTTING.name(), "포트 작업", WorkTypeTemplate.REPOT, 7);
    var inbound =
        inboundRecordService.create(
            new InboundRecordCreateCommand(
                LocalDate.of(2026, 8, 19),
                InboundType.FLASK_SEEDLING,
                fixture.variety().getId(),
                null,
                30,
                "배양실",
                LocalDate.of(2026, 8, 20),
                null,
                "입고 담당",
                null));

    var operation =
        inboundPottingOperationService.executeNow(
            new InboundPottingCommand(
                "routing-potting-9966",
                inbound.id(),
                LocalDate.of(2026, 8, 20),
                List.of(
                    new InboundPottingResultInput(
                        fixture.zone().getId(),
                        28,
                        "2인치",
                        1,
                        "트레이",
                        2,
                        false,
                        new BigDecimal("0"),
                        new BigDecimal("2"),
                        null)),
                "포트 담당",
                "포트 완료"));

    var effect =
        workAppliedEffectRepository.findByWorkOperationIdOrderByIdAsc(operation.id()).stream()
            .filter(item -> item.getEffectKey().endsWith(":routing-potting-9966"))
            .findFirst()
            .orElseThrow();
    Long groupId =
        effect.getResultDetails().get("createdOrchidGroupIds") instanceof List<?> ids
            ? ((Number) ids.getFirst()).longValue()
            : null;
    assertThat(groupId).isNotNull();
    assertThat(orchidGroupRepository.findById(groupId).orElseThrow().getStateRevision())
        .isEqualTo(1L);
    assertThat(effect.getMutationId()).isNotNull();
    assertThat(effect.getCorrelationId()).isNotNull();
    assertThat(mutationRepository.findById(effect.getMutationId()).orElseThrow().getSourceDomain())
        .isEqualTo(OrchidGroupMutationSourceDomain.WORK);
  }

  @Test
  void rejectsTheRemovedDirectMovementExecutionBranch() {
    Fixture fixture = createFixture(9967, "라우팅 이동");
    ensureWorkType(WorkTypeDefinition.MOVEMENT.name(), "위치 이동", WorkTypeTemplate.MOVEMENT, 8);
    var group = orchidGroupCommandService.create(groupRequest(fixture, 8, "0", "1", "정상"));
    WorkType movementType =
        workTypeRepository.findByCode(WorkTypeDefinition.MOVEMENT.name()).orElseThrow();
    var planned =
        workOperationPlanService.create(
            new WorkOperationCreateRequest(
                movementType.getId(),
                "라우팅 직접 이동",
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 20),
                WorkTargetSelection.orchidGroup(group.id()),
                Map.of(),
                "작업자",
                null,
                List.of()));
    workOperationProgressService.start(planned.id());
    Assertions.assertThatThrownBy(
            () ->
                workOperationProgressService.completeTarget(
                    planned.id(),
                    planned.targets().getFirst().id(),
                    new WorkTargetExecutionRequest(
                        "작업자",
                        Map.of(
                            "toBedZoneId",
                            fixture.zone().getId(),
                            "startPosition",
                            2,
                            "endPosition",
                            3),
                        LocalDate.of(2026, 8, 20))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("자리 이동은 구조 변경 실행 또는 즉시 기록으로 처리해야 합니다.");

    OrchidGroup moved = orchidGroupRepository.findById(group.id()).orElseThrow();
    assertThat(moved.getStartPosition()).isEqualByComparingTo("0");
    assertThat(moved.getEndPosition()).isEqualByComparingTo("1");
    assertThat(moved.getStateRevision()).isEqualTo(1L);
    assertThat(workAppliedEffectRepository.findByWorkOperationIdOrderByIdAsc(planned.id()))
        .isEmpty();
  }

  private Fixture createFixture(int houseNumber, String suffix) {
    House house = new House(houseNumber, suffix);
    PhysicalBed bed = new PhysicalBed(1, 1);
    bed.updatePositionUnits(new BigDecimal("20"), "칸");
    BedZone zone = new BedZone("좌측", BedZoneSide.LEFT, 1);
    bed.addBedZone(zone);
    house.addPhysicalBed(bed);
    houseRepository.save(house);
    Variety variety =
        varietyRepository.save(
            new Variety(
                "ROUTING-" + houseNumber, "팔레놉시스", suffix, null, "4인치", true, true, null, null));
    return new Fixture(zone, variety);
  }

  private OrchidGroupCreateRequest groupRequest(
      Fixture fixture, int quantity, String start, String end, String status) {
    return new OrchidGroupCreateRequest(
        fixture.zone().getId(),
        fixture.variety().getId(),
        quantity,
        "4인치",
        2,
        status,
        "단일",
        null,
        false,
        new BigDecimal(start),
        new BigDecimal(end),
        null);
  }

  private void ensureWorkType(String code, String name, WorkTypeTemplate template, int sortOrder) {
    if (workTypeRepository.findByCode(code).isEmpty()) {
      workTypeRepository.save(new WorkType(code, name, template, true, true, true, sortOrder));
    }
  }

  private record Fixture(BedZone zone, Variety variety) {}
}
