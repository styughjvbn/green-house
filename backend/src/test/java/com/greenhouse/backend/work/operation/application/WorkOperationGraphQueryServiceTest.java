package com.greenhouse.backend.work.operation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.greenhouse.backend.work.api.operation.WorkOperationRelationType;
import com.greenhouse.backend.work.api.operation.WorkOperationStatus;
import com.greenhouse.backend.work.correction.domain.WorkOperationCorrection;
import com.greenhouse.backend.work.correction.repository.WorkOperationCorrectionRepository;
import com.greenhouse.backend.work.effect.domain.WorkAppliedEffect;
import com.greenhouse.backend.work.effect.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.operation.domain.WorkOperation;
import com.greenhouse.backend.work.operation.domain.WorkType;
import com.greenhouse.backend.work.operation.repository.WorkOperationRepository;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationGraphDetail;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationGraphEdgeType;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationGraphNodeType;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationGraphResponse;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationOriginType;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationRelationSummaryResponse;
import com.greenhouse.backend.work.spi.operation.WorkOperationMutationGraphPort;
import com.greenhouse.backend.work.target.domain.WorkOperationTarget;
import com.greenhouse.backend.work.target.repository.WorkOperationTargetRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class WorkOperationGraphQueryServiceTest {

  @Mock WorkOperationRepository operationRepository;

  @Mock WorkOperationTargetRepository targetRepository;

  @Mock WorkAppliedEffectRepository effectRepository;

  @Mock WorkOperationCorrectionRepository correctionRepository;

  @Mock WorkOperationRelationSummaryAssembler relationSummaryAssembler;

  @Mock WorkOperationMutationGraphPort mutationGraphPort;

  WorkOperationGraphQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new WorkOperationGraphQueryService(
            operationRepository,
            targetRepository,
            effectRepository,
            correctionRepository,
            relationSummaryAssembler,
            mutationGraphPort);
  }

  @Test
  void excludesCreationBatchRelationshipsFromWorkGraph() {
    WorkOperation selected = operation(51L, null, null);
    when(operationRepository.findWithWorkTypeById(51L)).thenReturn(Optional.of(selected));
    when(operationRepository.findByParentOperationIdAndRelationTypeOrderByIdAsc(
            eq(51L), eq(WorkOperationRelationType.MOVEMENT_DISCARD), any(Pageable.class)))
        .thenReturn(List.of());
    when(relationSummaryAssembler.assembleOrigins(anyCollection(), anyInt()))
        .thenReturn(Map.of(51L, summary(WorkOperationOriginType.WORK_MANAGEMENT, 2)));

    var graph = service.get(51L, WorkOperationGraphDetail.WORK, 1, 120);

    assertThat(graph.nodes())
        .noneMatch(node -> node.nodeType() == WorkOperationGraphNodeType.CREATION_BATCH);
    assertThat(graph.nodes())
        .filteredOn(node -> node.nodeType() == WorkOperationGraphNodeType.WORK_OPERATION)
        .singleElement();
    assertThat(graph.edges())
        .noneMatch(edge -> edge.edgeType() == WorkOperationGraphEdgeType.SAME_COMMAND);
  }

  @Test
  void rendersMovementBeforeDiscardAndExpandsLineageThroughFarmPort() {
    WorkOperation movement = operation(62L, null, null);
    WorkOperation discard = operation(61L, movement, WorkOperationRelationType.MOVEMENT_DISCARD);
    when(operationRepository.findWithWorkTypeById(62L)).thenReturn(Optional.of(movement));
    when(operationRepository.findByParentOperationIdAndRelationTypeOrderByIdAsc(
            eq(62L), eq(WorkOperationRelationType.MOVEMENT_DISCARD), any(Pageable.class)))
        .thenReturn(List.of(discard));
    when(relationSummaryAssembler.assembleOrigins(anyCollection(), anyInt()))
        .thenReturn(
            Map.of(
                62L,
                summary(WorkOperationOriginType.WORK_MANAGEMENT, 1),
                61L,
                summary(WorkOperationOriginType.SYSTEM, 1)));
    when(effectRepository.findByWorkOperationIdInOrderByWorkOperationIdAscIdAsc(
            anyCollection(), any(Pageable.class)))
        .thenReturn(List.of());
    when(mutationGraphPort.load(anyCollection(), eq(true), anyInt(), anyInt()))
        .thenReturn(WorkOperationMutationGraphPort.Fragment.empty());

    var graph = service.get(62L, WorkOperationGraphDetail.LINEAGE, 2, 120);

    assertThat(graph.edges())
        .filteredOn(edge -> edge.edgeType() == WorkOperationGraphEdgeType.PRECEDES)
        .singleElement()
        .satisfies(
            edge -> {
              assertThat(edge.sourceNodeId()).isEqualTo("work-operation-62");
              assertThat(edge.targetNodeId()).isEqualTo("work-operation-61");
              assertThat(edge.relationType()).isEqualTo("MOVEMENT_DISCARD");
            });
    verify(mutationGraphPort).load(eq(List.of()), eq(true), eq(2), anyInt());
  }

  @Test
  void loadsCorrectionMutationUnderTheOriginalOperation() {
    WorkOperation original = operation(80L, null, null);
    WorkOperationCorrection correction = mock(WorkOperationCorrection.class);
    when(operationRepository.findWithWorkTypeById(80L)).thenReturn(Optional.of(original));
    when(relationSummaryAssembler.assembleOrigins(anyCollection(), anyInt()))
        .thenReturn(Map.of(80L, summary(WorkOperationOriginType.WORK_MANAGEMENT, 1)));
    var originalEffect = effect(original, 111L);
    when(effectRepository.findByWorkOperationIdInOrderByWorkOperationIdAscIdAsc(
            anyCollection(), any(Pageable.class)))
        .thenReturn(List.of(originalEffect));
    when(correctionRepository.findByOriginalWorkOperationIdIn(anyCollection(), any(Pageable.class)))
        .thenReturn(List.of(correction));
    when(correction.getOriginalWorkOperation()).thenReturn(original);
    when(correction.getMutationId()).thenReturn(112L);
    when(mutationGraphPort.load(eq(List.of(111L, 112L)), eq(false), eq(1), anyInt()))
        .thenReturn(fragment(111L, 112L));
    var graph = service.get(80L, WorkOperationGraphDetail.MUTATION, 1, 120);
    assertThat(graph.nodes())
        .filteredOn(node -> node.nodeType() == WorkOperationGraphNodeType.WORK_OPERATION)
        .hasSize(1);
    assertThat(graph.edges())
        .filteredOn(edge -> edge.edgeType() == WorkOperationGraphEdgeType.EFFECT)
        .extracting(edge -> Map.entry(edge.sourceNodeId(), edge.targetNodeId()))
        .containsExactlyInAnyOrder(
            Map.entry("work-operation-80", "mutation-111"),
            Map.entry("work-operation-80", "mutation-112"));
  }

  @Test
  void includesWorksDiscoveredFromOrchidGroupLineageUpToRequestedDepth() {
    WorkOperation selected = operation(85L, null, null);
    WorkOperation related = operation(86L, null, null);
    WorkAppliedEffect selectedEffect = effect(selected, 115L);
    WorkAppliedEffect relatedEffect = effect(related, 116L);
    when(operationRepository.findWithWorkTypeById(85L)).thenReturn(Optional.of(selected));
    when(operationRepository.findByParentOperationIdAndRelationTypeOrderByIdAsc(
            eq(85L), eq(WorkOperationRelationType.MOVEMENT_DISCARD), any(Pageable.class)))
        .thenReturn(List.of());
    when(relationSummaryAssembler.assembleOrigins(anyCollection(), anyInt()))
        .thenReturn(Map.of(85L, summary(WorkOperationOriginType.WORK_MANAGEMENT, 1)));
    when(effectRepository.findByWorkOperationIdInOrderByWorkOperationIdAscIdAsc(
            anyCollection(), any(Pageable.class)))
        .thenReturn(List.of(selectedEffect));
    when(mutationGraphPort.load(eq(List.of(115L)), eq(true), eq(3), anyInt()))
        .thenReturn(fragment(115L, 116L));
    when(effectRepository.findByMutationIdInOrderByMutationIdAscIdAsc(
            eq(List.of(115L, 116L)), any(Pageable.class)))
        .thenReturn(List.of(selectedEffect, relatedEffect));

    var graph = service.get(85L, WorkOperationGraphDetail.LINEAGE, 3, 120);

    assertThat(graph.nodes())
        .filteredOn(node -> node.nodeType() == WorkOperationGraphNodeType.WORK_OPERATION)
        .extracting(node -> node.workOperationId())
        .containsExactly(85L, 86L);
    assertThat(graph.edges())
        .filteredOn(edge -> edge.edgeType() == WorkOperationGraphEdgeType.EFFECT)
        .extracting(edge -> Map.entry(edge.sourceNodeId(), edge.targetNodeId()))
        .containsExactlyInAnyOrder(
            Map.entry("work-operation-85", "mutation-115"),
            Map.entry("work-operation-86", "mutation-116"));
    verify(mutationGraphPort).load(eq(List.of(115L)), eq(true), eq(3), anyInt());
  }

  @Test
  void keepsVoidedWorkGraphFixedToOriginalEffects() {
    WorkOperation operation = operation(90L, null, null);
    WorkAppliedEffect originalEffect = effect(operation, 121L);
    when(operation.getStatus()).thenReturn(WorkOperationStatus.VOIDED);
    when(operationRepository.findWithWorkTypeById(90L)).thenReturn(Optional.of(operation));
    when(operationRepository.findByParentOperationIdAndRelationTypeOrderByIdAsc(
            eq(90L), eq(WorkOperationRelationType.MOVEMENT_DISCARD), any(Pageable.class)))
        .thenReturn(List.of());
    when(relationSummaryAssembler.assembleOrigins(anyCollection(), anyInt()))
        .thenReturn(Map.of(90L, summary(WorkOperationOriginType.WORK_MANAGEMENT, 1)));
    when(effectRepository.findByWorkOperationIdInOrderByWorkOperationIdAscIdAsc(
            anyCollection(), any(Pageable.class)))
        .thenReturn(List.of(originalEffect));
    lenient().when(operation.getVoidMutationId()).thenReturn(122L);
    when(mutationGraphPort.load(eq(List.of(121L)), eq(false), eq(1), anyInt()))
        .thenReturn(fragment(121L));

    var graph = service.get(90L, WorkOperationGraphDetail.MUTATION, 1, 120);

    assertThat(graph.nodes())
        .filteredOn(node -> node.nodeType() == WorkOperationGraphNodeType.MUTATION)
        .extracting(node -> node.mutationId())
        .containsExactly(121L);
    assertThat(graph.edges())
        .filteredOn(edge -> edge.edgeType() == WorkOperationGraphEdgeType.EFFECT)
        .singleElement()
        .satisfies(
            edge -> {
              assertThat(edge.sourceNodeId()).isEqualTo("work-operation-90");
              assertThat(edge.targetNodeId()).isEqualTo("mutation-121");
            });
    verify(mutationGraphPort).load(eq(List.of(121L)), eq(false), eq(1), anyInt());
  }

  @ParameterizedTest
  @CsvSource({"8, false, true", "9, false, true", "10, true, false"})
  void preservesSeedOrderAndTruncationAtTheNodeLimit(
      int inboundCount, boolean truncated, boolean rootVisible) {
    var root = prepareRoot(51L);
    var inboundIds = LongStream.rangeClosed(1, inboundCount).boxed().toList();
    when(relationSummaryAssembler.assembleOrigins(anyCollection(), anyInt()))
        .thenReturn(
            Map.of(
                51L,
                new WorkOperationRelationSummaryResponse(
                    WorkOperationOriginType.INBOUND, inboundIds, 1, false, 0)));

    var graph = service.get(51L, WorkOperationGraphDetail.WORK, 1, 10);

    assertThat(graph.truncated()).isEqualTo(truncated);
    assertThat(graph.nodes()).hasSize(Math.min(10, inboundCount + 1));
    assertThat(graph.nodes().subList(0, inboundCount))
        .extracting(node -> node.id())
        .containsExactlyElementsOf(inboundIds.stream().map(id -> "origin-inbound-" + id).toList());
    assertThat(graph.nodes().stream().anyMatch(node -> node.id().equals("work-operation-51")))
        .isEqualTo(rootVisible);
    assertThat(graph.edges()).hasSize(rootVisible ? inboundCount : 0);
    assertVisibleEndpoints(graph);
    verifyNoInteractions(mutationGraphPort, effectRepository, correctionRepository);
  }

  @Test
  void propagatesFarmTruncationEvenWhenSeedsExactlyFillTheNodeLimit() {
    var root = prepareRoot(51L);
    when(relationSummaryAssembler.assembleOrigins(anyCollection(), anyInt()))
        .thenReturn(
            Map.of(
                51L,
                new WorkOperationRelationSummaryResponse(
                    WorkOperationOriginType.INBOUND,
                    LongStream.rangeClosed(1, 9).boxed().toList(),
                    1,
                    false,
                    0)));
    when(mutationGraphPort.load(anyCollection(), eq(false), eq(1), eq(10)))
        .thenReturn(
            new WorkOperationMutationGraphPort.Fragment(
                true, fragment(111L).mutations(), List.of(), List.of()));

    var graph = service.get(51L, WorkOperationGraphDetail.MUTATION, 1, 10);

    assertThat(graph.nodes()).hasSize(10);
    assertThat(graph.truncated()).isTrue();
    assertThat(graph.nodes())
        .noneMatch(node -> node.nodeType() == WorkOperationGraphNodeType.MUTATION);
    assertVisibleEndpoints(graph);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void propagatesFarmTruncationWhenMutationExpansionHasRoom(boolean truncated) {
    prepareRoot(51L);
    when(mutationGraphPort.load(anyCollection(), eq(false), eq(1), eq(10)))
        .thenReturn(
            new WorkOperationMutationGraphPort.Fragment(
                truncated, List.of(), List.of(), List.of()));

    var graph = service.get(51L, WorkOperationGraphDetail.MUTATION, 1, 10);

    assertThat(graph.nodes()).hasSize(2);
    assertThat(graph.truncated()).isEqualTo(truncated);
    assertVisibleEndpoints(graph);
  }

  @Test
  void skipsAnOversizedFlowAsAUnitAndStillIncludesLaterFittingFlows() {
    var root = prepareRoot(85L);
    var skippedWork = mock(WorkOperation.class);
    when(skippedWork.getId()).thenReturn(86L);
    var visibleWork = operation(87L, null, null);
    var rootEffects =
        List.of(effect(root, 111L), effect(root, 112L), effect(root, 113L), effect(root, 114L));
    when(effectRepository.findByWorkOperationIdInOrderByWorkOperationIdAscIdAsc(
            anyCollection(), any(Pageable.class)))
        .thenReturn(rootEffects);
    var discoveredEffects = List.of(effect(skippedWork, 113L), effect(visibleWork, 114L));
    when(effectRepository.findByMutationIdInOrderByMutationIdAscIdAsc(
            eq(List.of(111L, 112L, 113L, 114L)), any(Pageable.class)))
        .thenReturn(discoveredEffects);
    var states =
        LongStream.rangeClosed(1, 8)
            .mapToObj(
                revision ->
                    new WorkOperationMutationGraphPort.StateNode(
                        "state-" + revision, 401L, revision, null))
            .toList();
    var edges =
        List.of(
            stateEdge("in-111", "state-1", "mutation-111", "STATE_INPUT"),
            stateEdge("out-111", "mutation-111", "state-2", "STATE_OUTPUT"),
            stateEdge("in-112", "state-2", "mutation-112", "STATE_INPUT"),
            stateEdge("out-112", "mutation-112", "state-3", "STATE_OUTPUT"),
            stateEdge("in-113", "state-3", "mutation-113", "STATE_INPUT"),
            stateEdge("out-113-a", "mutation-113", "state-5", "STATE_OUTPUT"),
            stateEdge("out-113-b", "mutation-113", "state-6", "STATE_OUTPUT"),
            stateEdge("out-113-c", "mutation-113", "state-7", "STATE_OUTPUT"),
            stateEdge("out-113-d", "mutation-113", "state-8", "STATE_OUTPUT"),
            stateEdge("in-114", "state-3", "mutation-114", "STATE_INPUT"),
            stateEdge("out-114", "mutation-114", "state-4", "STATE_OUTPUT"),
            new WorkOperationMutationGraphPort.Edge(
                "relation-1", "mutation-111", "mutation-112", "MUTATION_RELATION", "CORRECTS"),
            new WorkOperationMutationGraphPort.Edge(
                "relation-hidden",
                "mutation-112",
                "mutation-113",
                "MUTATION_RELATION",
                "CORRECTS"));
    when(mutationGraphPort.load(eq(List.of(111L, 112L, 113L, 114L)), eq(true), eq(3), eq(10)))
        .thenReturn(
            new WorkOperationMutationGraphPort.Fragment(
                false, fragment(111L, 112L, 113L, 114L).mutations(), states, edges));

    var graph = service.get(85L, WorkOperationGraphDetail.LINEAGE, 3, 10);

    assertThat(graph.truncated()).isTrue();
    assertThat(graph.nodes())
        .extracting(node -> node.id())
        .containsExactly(
            "origin-work_management-85",
            "work-operation-85",
            "mutation-111",
            "state-1",
            "state-2",
            "mutation-112",
            "state-3",
            "work-operation-87",
            "mutation-114",
            "state-4");
    assertThat(graph.edges())
        .extracting(edge -> edge.id())
        .containsExactly(
            "originated-85",
            "in-111",
            "out-111",
            "in-112",
            "out-112",
            "in-114",
            "out-114",
            "relation-1",
            "effect-85-111",
            "effect-85-112",
            "effect-85-114",
            "effect-87-114");
    assertThat(graph.nodes())
        .filteredOn(node -> node.selected())
        .extracting(node -> node.id())
        .containsExactly("work-operation-85");
    assertVisibleEndpoints(graph);
  }

  @Test
  void preservesAllNodeKindFieldsAndNullsInTheJsonContract() throws Exception {
    var root = prepareRoot(51L);
    var target = mock(WorkOperationTarget.class);
    when(target.getWorkOperation()).thenReturn(root);
    when(target.getOrchidGroupId()).thenReturn(401L);
    when(target.getVarietyNameSnapshot()).thenReturn(null);
    when(targetRepository.findByWorkOperationIdInAndExcludedAtIsNullOrderByWorkOperationIdAscIdAsc(
            anyCollection(), any(Pageable.class)))
        .thenReturn(List.of(target));
    var rootEffect = effect(root, 111L);
    when(effectRepository.findByWorkOperationIdInOrderByWorkOperationIdAscIdAsc(
            anyCollection(), any(Pageable.class)))
        .thenReturn(List.of(rootEffect));
    var state =
        new WorkOperationMutationGraphPort.State(
            100,
            7,
            "관리",
            301L,
            "팔레놉시스",
            "snapshot 품종",
            2,
            "POT_3_5",
            201L,
            3,
            4,
            "snapshot 구역",
            new BigDecimal("1.25"),
            new BigDecimal("4.75"));
    var states =
        List.of(
            new WorkOperationMutationGraphPort.StateNode("state-401-1", 401L, 1L, state),
            new WorkOperationMutationGraphPort.StateNode("state-401-2", 401L, 2L, null));
    when(mutationGraphPort.load(eq(List.of(111L)), eq(false), eq(1), eq(120)))
        .thenReturn(
            new WorkOperationMutationGraphPort.Fragment(
                false,
                fragment(111L).mutations(),
                states,
                List.of(
                    stateEdge("in-111", "state-401-1", "mutation-111", "STATE_INPUT"),
                    stateEdge("out-111", "mutation-111", "state-401-2", "STATE_OUTPUT"))));
    var mapper =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    try (var fixture = getClass().getResourceAsStream("/work/graph-node-contract.json")) {
      assertThat(fixture).isNotNull();
      JsonNode actual =
          mapper.readTree(
              mapper.writeValueAsString(
                  service.get(51L, WorkOperationGraphDetail.MUTATION, 1, 120)));
      assertThat(actual).isEqualTo(mapper.readTree(fixture));
    }
  }

  private WorkOperation prepareRoot(Long id) {
    var root = operation(id, null, null);
    when(operationRepository.findWithWorkTypeById(id)).thenReturn(Optional.of(root));
    when(relationSummaryAssembler.assembleOrigins(anyCollection(), anyInt()))
        .thenReturn(Map.of(id, summary(WorkOperationOriginType.WORK_MANAGEMENT, 1)));
    return root;
  }

  private WorkOperationMutationGraphPort.Edge stateEdge(
      String id, String source, String target, String type) {
    return new WorkOperationMutationGraphPort.Edge(id, source, target, type, null);
  }

  private void assertVisibleEndpoints(WorkOperationGraphResponse graph) {
    var visibleIds = graph.nodes().stream().map(node -> node.id()).toList();
    assertThat(graph.edges())
        .allSatisfy(
            edge -> {
              assertThat(visibleIds).contains(edge.sourceNodeId(), edge.targetNodeId());
            });
  }

  private WorkOperation operation(
      Long id, WorkOperation parent, WorkOperationRelationType relationType) {
    WorkOperation operation = mock(WorkOperation.class);
    WorkType type = mock(WorkType.class);
    when(operation.getId()).thenReturn(id);
    when(operation.getWorkType()).thenReturn(type);
    when(operation.getTitle()).thenReturn("작업 #" + id);
    when(operation.getStatus()).thenReturn(WorkOperationStatus.COMPLETED);
    when(operation.getPlannedStartDate()).thenReturn(LocalDate.of(2026, 9, 1));
    when(operation.getParentOperation()).thenReturn(parent);
    if (relationType != null) when(operation.getRelationType()).thenReturn(relationType);
    when(type.getCode()).thenReturn("TEST");
    when(type.getName()).thenReturn("테스트 작업");
    return operation;
  }

  private WorkOperationRelationSummaryResponse summary(
      WorkOperationOriginType origin, int batchSize) {
    return new WorkOperationRelationSummaryResponse(origin, List.of(), batchSize, false, 0);
  }

  private WorkAppliedEffect effect(WorkOperation operation, Long mutationId) {
    WorkAppliedEffect effect = mock(WorkAppliedEffect.class);
    when(effect.getWorkOperation()).thenReturn(operation);
    when(effect.getMutationId()).thenReturn(mutationId);
    return effect;
  }

  private WorkOperationMutationGraphPort.Fragment fragment(Long... mutationIds) {
    return new WorkOperationMutationGraphPort.Fragment(
        false,
        List.of(mutationIds).stream()
            .map(
                id ->
                    new WorkOperationMutationGraphPort.MutationNode(
                        id,
                        "TEST",
                        LocalDate.of(2026, 9, 1),
                        Instant.parse("2026-09-01T00:00:00Z")))
            .toList(),
        List.of(),
        List.of());
  }
}
