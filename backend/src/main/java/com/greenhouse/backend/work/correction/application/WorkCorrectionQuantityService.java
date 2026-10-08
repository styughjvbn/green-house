package com.greenhouse.backend.work.correction.application;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.api.correction.OrchidGroupCorrectionInput;
import com.greenhouse.backend.work.api.correction.WorkCorrectionCommand;
import com.greenhouse.backend.work.api.correction.WorkCorrectionQuantityApi;
import com.greenhouse.backend.work.api.correction.WorkQuantityBalance;
import com.greenhouse.backend.work.api.correction.WorkQuantityBalanceChange;
import com.greenhouse.backend.work.api.correction.WorkQuantityCorrectionInput;
import com.greenhouse.backend.work.api.operation.WorkOperationRelationType;
import com.greenhouse.backend.work.correction.domain.WorkQuantityBalancePolicy;
import com.greenhouse.backend.work.correction.repository.WorkOperationCorrectionRepository;
import com.greenhouse.backend.work.effect.application.WorkEffectJsonCodec;
import com.greenhouse.backend.work.effect.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.operation.domain.WorkTypeDefinition;
import com.greenhouse.backend.work.operation.repository.WorkOperationRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkCorrectionQuantityService implements WorkCorrectionQuantityApi {

  private static final JsonMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

  private final WorkAppliedEffectRepository effects;

  private final WorkOperationCorrectionRepository corrections;

  private final WorkOperationRepository operations;

  @Value("${features.work-quantity-correction.enabled:false}")
  private boolean enabled;

  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void requireEnabled() {
    if (!enabled) throw new ConflictException("FEATURE_ON_HOLD", "작업 기록의 수량 정정은 현재 비활성화되어 있습니다.");
  }

  @Override
  public List<WorkQuantityBalance> context(Long workId) {
    var work =
        operations
            .findWithWorkTypeById(workId)
            .orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
    var type = WorkTypeDefinition.forCode(work.getWorkType().getCode());
    boolean growth = type == WorkTypeDefinition.REPOT || type == WorkTypeDefinition.DIVIDE;
    boolean inputEditable = type.supportsStructureExecution();
    boolean fixedLoss =
        type == WorkTypeDefinition.MOVEMENT
            && !operations
                .findByParentOperationIdAndRelationTypeOrderByIdAsc(
                    workId, WorkOperationRelationType.MOVEMENT_DISCARD)
                .isEmpty();
    var balances = new LinkedHashMap<Long, WorkQuantityBalance>();
    for (var effect : effects.findByWorkOperationIdOrderByIdAsc(workId)) {
      var details = effect.getResultDetails();
      Map<Long, Integer> results = WorkEffectJsonCodec.resultQuantities(details);
      Map<Long, Integer> inputs =
          WorkEffectJsonCodec.inputQuantities(details.get("sourceInputQuantities"));
      if (type == WorkTypeDefinition.MOVEMENT
          && inputs.isEmpty()
          && effect.getTarget() != null
          && details.containsKey("toBedZoneId")) {
        inputs =
            Map.of(effect.getTarget().getOrchidGroupId(), effect.getTarget().getQuantitySnapshot());
        results = inputs;
      }
      if (inputs.isEmpty()
          && details.get("sourceOrchidGroupId") instanceof Number id
          && details.get("inputQuantity") instanceof Number quantity)
        inputs = Map.of(id.longValue(), quantity.intValue());
      if (type == WorkTypeDefinition.POTTING
          && details.get("actualQuantity") instanceof Number quantity) {
        inputs = Map.of(effect.getId(), quantity.intValue());
        if (results.isEmpty() && details.get("createdOrchidGroupIds") instanceof List<?> ids) {
          results =
              WorkEffectJsonCodec.pottingResultQuantities(
                  ids, effect.getCommandDetails().get("results"));
        }
      }
      if (!results.isEmpty() && !inputs.isEmpty())
        balances.put(
            effect.getId(),
            balance(
                effect.getId(),
                inputs,
                results,
                number(details.get("lossQuantity")),
                number(details.get("increaseQuantity")),
                inputEditable,
                growth,
                !fixedLoss));
    }
    // Reconstruct effective work facts from saved work snapshots and audit events,
    // never live groups.
    for (var audit : corrections.findByOriginalWorkOperationIdOrderByCreatedAtAscIdAsc(workId)) {
      var details = audit.getResultDetails();
      var changes =
          MAPPER.convertValue(
              details.getOrDefault("quantityBalances", List.of()),
              WorkQuantityBalanceChange[].class);
      for (var change : changes) balances.put(change.after().executionId(), change.after());
      if (changes.length == 0 && details.get("adjustments") instanceof List<?> rows) {
        for (var raw : rows) {
          if (!(raw instanceof Map<?, ?> row) || "생성 취소".equals(row.get("afterStatus"))) continue;
          Long id = ((Number) row.get("orchidGroupId")).longValue();
          int quantity = ((Number) row.get("afterQuantity")).intValue();
          balances.replaceAll(
              (key, before) -> {
                if (!before.resultQuantities().containsKey(id)) return before;
                var result = new LinkedHashMap<>(before.resultQuantities());
                result.put(id, quantity);
                int delta = before.inputQuantity() - sum(result);
                return balance(
                    key,
                    before.sourceInputQuantities(),
                    result,
                    Math.max(delta, 0),
                    Math.max(-delta, 0),
                    before.inputEditable(),
                    before.increaseAllowed(),
                    before.lossEditable());
              });
        }
      }
    }
    return List.copyOf(balances.values());
  }

  @Override
  public List<WorkQuantityBalanceChange> validate(Long workId, WorkCorrectionCommand request) {
    if (!enabled) {
      if (request.quantityCorrections() != null && !request.quantityCorrections().isEmpty())
        requireEnabled();
      return List.of();
    }
    if (request.cancelResultCreation()) {
      if (request.quantityCorrections() != null && !request.quantityCorrections().isEmpty())
        throw new IllegalArgumentException("생성 취소와 작업 수량 정정은 별도로 처리해야 합니다.");
      return List.of();
    }
    var declared = new LinkedHashMap<Long, WorkQuantityCorrectionInput>();
    for (var item :
        request.quantityCorrections() == null
            ? List.<WorkQuantityCorrectionInput>of()
            : request.quantityCorrections())
      if (declared.put(item.executionId(), item) != null)
        throw new IllegalArgumentException("같은 실행의 수량 정정은 중복될 수 없습니다.");
    var contexts = context(workId);
    var adjustmentById =
        request.orchidGroupAdjustments().stream()
            .collect(
                Collectors.toMap(
                    OrchidGroupCorrectionInput::orchidGroupId,
                    OrchidGroupCorrectionInput::quantity));
    if (!declared.isEmpty()
        && !contexts.stream()
            .map(WorkQuantityBalance::executionId)
            .collect(Collectors.toSet())
            .containsAll(declared.keySet()))
      throw new IllegalArgumentException("수량 정정 대상 실행을 찾을 수 없습니다.");
    var changes = new ArrayList<WorkQuantityBalanceChange>();
    for (var before : contexts) {
      var results = new LinkedHashMap<>(before.resultQuantities());
      adjustmentById.forEach(
          (id, quantity) -> {
            if (results.containsKey(id)) results.put(id, quantity);
          });
      var declaration = declared.get(before.executionId());
      if (results.equals(before.resultQuantities()) && declaration == null) continue;
      if (!results.equals(before.resultQuantities()) && declaration == null)
        throw new IllegalArgumentException(
            "결과 수량 정정에는 투입·손실·증식 수량을 명시해야 합니다. 현재 개수 차이는 실사 수량 조정을 사용하세요.");
      var inputs =
          declaration == null || declaration.sourceInputQuantities() == null
              ? before.sourceInputQuantities()
              : declaration.sourceInputQuantities();
      if (!inputs.keySet().equals(before.sourceInputQuantities().keySet()))
        throw new IllegalArgumentException("정정은 기존 투입 원본을 바꿀 수 없습니다.");
      if (!before.inputEditable() && !inputs.equals(before.sourceInputQuantities()))
        throw new IllegalArgumentException("입고 투입량은 포트 결과 보정으로 변경할 수 없습니다.");
      int input = sum(inputs), result = sum(results);
      int loss = declaration.lossQuantity();
      int increase = declaration.increaseQuantity();
      WorkQuantityBalancePolicy.validate(input, result, loss, increase, before.increaseAllowed());
      if (!before.lossEditable() && loss != before.lossQuantity())
        throw new IllegalArgumentException("연관 폐기 수량은 단독 보정할 수 없습니다. 이동·폐기를 취소 후 함께 다시 기록하세요.");
      var after =
          balance(
              before.executionId(),
              inputs,
              results,
              loss,
              increase,
              before.inputEditable(),
              before.increaseAllowed(),
              before.lossEditable());
      if (!Objects.equals(before, after)) changes.add(new WorkQuantityBalanceChange(before, after));
    }
    return List.copyOf(changes);
  }

  private int number(Object value) {
    return value instanceof Number n ? n.intValue() : 0;
  }

  private int sum(Map<Long, Integer> values) {
    long total = values.values().stream().mapToLong(Integer::longValue).sum();
    if (values.values().stream().anyMatch(q -> q < 0) || total > Integer.MAX_VALUE)
      throw new IllegalArgumentException("작업 수량 범위가 올바르지 않습니다.");
    return (int) total;
  }

  private WorkQuantityBalance balance(
      Long id,
      Map<Long, Integer> inputs,
      Map<Long, Integer> results,
      int loss,
      int growth,
      boolean inputEditable,
      boolean growthAllowed,
      boolean lossEditable) {
    return new WorkQuantityBalance(
        id,
        Map.copyOf(inputs),
        Map.copyOf(results),
        sum(inputs),
        sum(results),
        loss,
        growth,
        inputEditable,
        growthAllowed,
        lossEditable);
  }
}
