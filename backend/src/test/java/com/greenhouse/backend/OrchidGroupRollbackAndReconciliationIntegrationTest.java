package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.farm.api.orchid.CompensateTransformMutationsCommand;
import com.greenhouse.backend.farm.api.orchid.CreateOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationType;
import com.greenhouse.backend.farm.api.orchid.ReconcileOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.api.orchid.TransformOrchidGroupMutationResult;
import com.greenhouse.backend.farm.api.orchid.TransformOrchidGroupMutationSource;
import com.greenhouse.backend.farm.api.orchid.TransformOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelationType;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRelationRepository;
import com.greenhouse.backend.farm.structure.domain.BedZone;
import com.greenhouse.backend.farm.structure.domain.BedZoneSide;
import com.greenhouse.backend.farm.structure.domain.House;
import com.greenhouse.backend.farm.structure.domain.PhysicalBed;
import com.greenhouse.backend.farm.variety.domain.Variety;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class OrchidGroupRollbackAndReconciliationIntegrationTest extends AbstractBackendIntegrationTest {

  @Autowired OrchidGroupMutationEngine mutationEngine;

  @Autowired OrchidGroupMutationRelationRepository relationRepository;

  @Autowired EntityManager entityManager;

  @Test
  void directlyCancelsOriginalCreationWithoutRestoringAnOccupiedPosition() {
    var fixture = fixture(934);
    var date = LocalDate.of(2026, 9, 22);
    var created =
        mutationEngine.create(
            new CreateOrchidGroupMutationCommand(
                source("net-create", "CREATE"),
                fixture.sourceZone().getId(),
                details(fixture.variety().getId(), 20, "0", "4"),
                date,
                "생성"));
    Long originalId = created.entries().getFirst().orchidGroupId();
    var transformed =
        mutationEngine.transform(
            new TransformOrchidGroupsMutationCommand(
                source("net-transform", "TRANSFORM"),
                List.of(new TransformOrchidGroupMutationSource(originalId, 20, null, null)),
                List.of(
                    new TransformOrchidGroupMutationResult(
                        fixture.resultZone().getId(),
                        details(fixture.variety().getId(), 20, "0", "4"))),
                date,
                "변환",
                Set.of()));
    var occupant =
        mutationEngine.create(
            new CreateOrchidGroupMutationCommand(
                source("net-occupant", "CREATE"),
                fixture.sourceZone().getId(),
                details(fixture.variety().getId(), 9, "0", "4"),
                date,
                "현재 배치"));
    var command =
        new CompensateTransformMutationsCommand(
            source("net-void", "VOID"),
            List.of(transformed.mutationId()),
            date,
            "생성 취소까지 적용",
            Set.of(originalId));
    var result = mutationEngine.compensateTransforms(command);
    assertThat(orchidGroupRepository.findById(originalId).orElseThrow().getStatus())
        .isEqualTo("생성 취소");
    assertThat(orchidGroupRepository.findById(originalId).orElseThrow().getQuantity()).isZero();
    assertThat(
            orchidGroupRepository
                .findById(occupant.entries().getFirst().orchidGroupId())
                .orElseThrow()
                .getQuantity())
        .isEqualTo(9);
    assertThat(mutationEngine.compensateTransforms(command).mutationId())
        .isEqualTo(result.mutationId());
    assertThatThrownBy(
            () ->
                mutationEngine.compensateTransforms(
                    new CompensateTransformMutationsCommand(
                        command.source(), command.mutationIds(), date, command.reason())))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void compensatesAWholeTransformWithoutRewindingRevisions() {
    Fixture fixture = fixture(930);
    LocalDate date = LocalDate.of(2026, 9, 22);
    var created =
        mutationEngine.create(
            new CreateOrchidGroupMutationCommand(
                source("create", "CREATE"),
                fixture.sourceZone().getId(),
                details(fixture.variety().getId(), 20, "0", "4"),
                date,
                "원본 생성"));
    Long sourceId = created.entries().getFirst().orchidGroupId();
    var transformed =
        mutationEngine.transform(
            new TransformOrchidGroupsMutationCommand(
                source("transform", "EXECUTION:1"),
                List.of(new TransformOrchidGroupMutationSource(sourceId, 8, null, null)),
                List.of(
                    new TransformOrchidGroupMutationResult(
                        fixture.resultZone().getId(),
                        details(fixture.variety().getId(), 8, "0", "2"))),
                date,
                "분할",
                Set.of()));
    Long resultId =
        transformed.entries().stream()
            .filter(entry -> entry.orchidGroupId() != sourceId)
            .findFirst()
            .orElseThrow()
            .orchidGroupId();

    var compensation =
        mutationEngine.compensateTransforms(
            new CompensateTransformMutationsCommand(
                source("void", "VOID:1"), List.of(transformed.mutationId()), date, "실수로 등록"));

    var source = orchidGroupRepository.findById(sourceId).orElseThrow();
    var result = orchidGroupRepository.findById(resultId).orElseThrow();
    assertThat(compensation.mutationType()).isEqualTo(OrchidGroupMutationType.COMPENSATION);
    assertThat(source.getQuantity()).isEqualTo(20);
    assertThat(source.getStateRevision()).isEqualTo(3L);
    assertThat(result.getQuantity()).isZero();
    assertThat(result.getStatus()).isEqualTo("생성 취소");
    assertThat(result.getStateRevision()).isEqualTo(2L);
    assertThat(relationRepository.findByMutationIdOrderByIdAsc(compensation.mutationId()))
        .singleElement()
        .satisfies(
            relation -> {
              assertThat(relation.getRelatedMutation().getId()).isEqualTo(transformed.mutationId());
              assertThat(relation.getRelationType())
                  .isEqualTo(OrchidGroupMutationRelationType.COMPENSATES);
            });
    assertThatThrownBy(
            () ->
                mutationEngine.compensateTransforms(
                    new CompensateTransformMutationsCommand(
                        source("void-again", "VOID:2"),
                        List.of(transformed.mutationId()),
                        date,
                        "중복 무효화")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이미 취소");
  }

  @Test
  void compensatesConsecutiveTransformsFromNewestToOldest() {
    Fixture fixture = fixture(932);
    LocalDate date = LocalDate.of(2026, 9, 22);
    var created =
        mutationEngine.create(
            new CreateOrchidGroupMutationCommand(
                source("stack-create", "CREATE"),
                fixture.sourceZone().getId(),
                details(fixture.variety().getId(), 20, "0", "4"),
                date,
                "원본 생성"));
    Long sourceId = created.entries().getFirst().orchidGroupId();
    var first =
        mutationEngine.transform(
            new TransformOrchidGroupsMutationCommand(
                source("stack-first", "EXECUTION:1"),
                List.of(new TransformOrchidGroupMutationSource(sourceId, 8, null, null)),
                List.of(
                    new TransformOrchidGroupMutationResult(
                        fixture.resultZone().getId(),
                        details(fixture.variety().getId(), 8, "0", "2"))),
                date,
                "첫 번째 분할",
                Set.of()));
    Long firstResultId =
        first.entries().stream()
            .filter(entry -> !entry.orchidGroupId().equals(sourceId))
            .findFirst()
            .orElseThrow()
            .orchidGroupId();
    var second =
        mutationEngine.transform(
            new TransformOrchidGroupsMutationCommand(
                source("stack-second", "EXECUTION:2"),
                List.of(new TransformOrchidGroupMutationSource(firstResultId, 3, null, null)),
                List.of(
                    new TransformOrchidGroupMutationResult(
                        fixture.sourceZone().getId(),
                        details(fixture.variety().getId(), 3, "6", "7"))),
                date,
                "두 번째 분할",
                Set.of()));

    mutationEngine.compensateTransforms(
        new CompensateTransformMutationsCommand(
            source("stack-void-second", "VOID:2"),
            List.of(second.mutationId()),
            date,
            "두 번째 작업 취소"));
    var firstResultAfterSecondUndo = orchidGroupRepository.findById(firstResultId).orElseThrow();
    assertThat(firstResultAfterSecondUndo.getQuantity()).isEqualTo(8);
    assertThat(firstResultAfterSecondUndo.getStateRevision()).isEqualTo(3L);

    var firstCompensation =
        mutationEngine.compensateTransforms(
            new CompensateTransformMutationsCommand(
                source("stack-void-first", "VOID:1"),
                List.of(first.mutationId()),
                date,
                "첫 번째 작업 취소"));

    var restoredSource = orchidGroupRepository.findById(sourceId).orElseThrow();
    var canceledFirstResult = orchidGroupRepository.findById(firstResultId).orElseThrow();
    assertThat(restoredSource.getQuantity()).isEqualTo(20);
    assertThat(restoredSource.getStateRevision()).isEqualTo(3L);
    assertThat(canceledFirstResult.getQuantity()).isZero();
    assertThat(canceledFirstResult.getStatus()).isEqualTo("생성 취소");
    assertThat(canceledFirstResult.getStateRevision()).isEqualTo(4L);
    assertThat(firstCompensation.mutationType()).isEqualTo(OrchidGroupMutationType.COMPENSATION);
  }

  @Test
  void rejectsAnOlderTransformWhileANewerTransformRemainsEffective() {
    Fixture fixture = fixture(933);
    LocalDate date = LocalDate.of(2026, 9, 22);
    var created =
        mutationEngine.create(
            new CreateOrchidGroupMutationCommand(
                source("blocked-create", "CREATE"),
                fixture.sourceZone().getId(),
                details(fixture.variety().getId(), 20, "0", "4"),
                date,
                "원본 생성"));
    Long sourceId = created.entries().getFirst().orchidGroupId();
    var first =
        mutationEngine.transform(
            new TransformOrchidGroupsMutationCommand(
                source("blocked-first", "EXECUTION:1"),
                List.of(new TransformOrchidGroupMutationSource(sourceId, 8, null, null)),
                List.of(
                    new TransformOrchidGroupMutationResult(
                        fixture.resultZone().getId(),
                        details(fixture.variety().getId(), 8, "0", "2"))),
                date,
                "첫 번째 분할",
                Set.of()));
    Long firstResultId =
        first.entries().stream()
            .filter(entry -> !entry.orchidGroupId().equals(sourceId))
            .findFirst()
            .orElseThrow()
            .orchidGroupId();
    mutationEngine.transform(
        new TransformOrchidGroupsMutationCommand(
            source("blocked-second", "EXECUTION:2"),
            List.of(new TransformOrchidGroupMutationSource(firstResultId, 3, null, null)),
            List.of(
                new TransformOrchidGroupMutationResult(
                    fixture.sourceZone().getId(), details(fixture.variety().getId(), 3, "6", "7"))),
            date,
            "두 번째 분할",
            Set.of()));

    assertThatThrownBy(
            () ->
                mutationEngine.compensateTransforms(
                    new CompensateTransformMutationsCommand(
                        source("blocked-void-first", "VOID:1"),
                        List.of(first.mutationId()),
                        date,
                        "첫 작업부터 취소")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("상쇄되지 않은 후속 변경");
  }

  @Test
  void reconcilesObservedQuantityStatusAndLocationAsANewRevision() {
    Fixture fixture = fixture(931);
    LocalDate date = LocalDate.of(2026, 9, 22);
    var created =
        mutationEngine.create(
            new CreateOrchidGroupMutationCommand(
                source("sync-create", "CREATE"),
                fixture.sourceZone().getId(),
                details(fixture.variety().getId(), 20, "0", "4"),
                date,
                "원본 생성"));
    Long groupId = created.entries().getFirst().orchidGroupId();

    var reconciled =
        mutationEngine.reconcile(
            new ReconcileOrchidGroupMutationCommand(
                source("sync", "RECONCILIATION"),
                groupId,
                17,
                "실사 조정",
                fixture.resultZone().getId(),
                new BigDecimal("2"),
                new BigDecimal("5"),
                date,
                "현장 실사 차이"));

    var group = orchidGroupRepository.findById(groupId).orElseThrow();
    assertThat(reconciled.mutationType()).isEqualTo(OrchidGroupMutationType.RECONCILIATION);
    assertThat(group.getQuantity()).isEqualTo(17);
    assertThat(group.getStatus()).isEqualTo("실사 조정");
    assertThat(group.getBedZone().getId()).isEqualTo(fixture.resultZone().getId());
    assertThat(group.getStartPosition()).isEqualByComparingTo("2.00");
    assertThat(group.getEndPosition()).isEqualByComparingTo("5.00");
    assertThat(group.getStateRevision()).isEqualTo(2L);
  }

  private Fixture fixture(int houseNumber) {
    House house = new House(houseNumber, "Rollback 테스트동 " + houseNumber);
    PhysicalBed bed = new PhysicalBed(1, 1);
    bed.updatePositionUnits(new BigDecimal("20"), "칸");
    BedZone sourceZone = new BedZone("원본 구역", BedZoneSide.LEFT, 1);
    BedZone resultZone = new BedZone("결과 구역", BedZoneSide.RIGHT, 2);
    bed.addBedZone(sourceZone);
    bed.addBedZone(resultZone);
    house.addPhysicalBed(bed);
    houseRepository.save(house);
    Variety variety =
        varietyRepository.save(
            new Variety(
                "ROLLBACK-" + houseNumber,
                "Phalaenopsis",
                "Rollback " + houseNumber,
                null,
                "4치",
                true,
                true,
                null,
                null));
    return new Fixture(sourceZone, resultZone, variety);
  }

  private OrchidGroupMutationDetails details(
      Long varietyId, int quantity, String start, String end) {
    return new OrchidGroupMutationDetails(
        varietyId,
        quantity,
        "4치",
        2,
        "정상",
        "POT",
        null,
        false,
        new BigDecimal(start),
        new BigDecimal(end),
        null);
  }

  private OrchidGroupMutationSource source(String referenceId, String operationKey) {
    return new OrchidGroupMutationSource(
        OrchidGroupMutationSourceDomain.WORK,
        "WORK_EFFECT",
        referenceId,
        operationKey,
        UUID.randomUUID());
  }

  private record Fixture(BedZone sourceZone, BedZone resultZone, Variety variety) {}
}
