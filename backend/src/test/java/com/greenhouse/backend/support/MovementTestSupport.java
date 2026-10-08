package com.greenhouse.backend.support;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupResponse;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.api.effect.StructureChangeCommand;
import com.greenhouse.backend.work.api.effect.StructureChangeResultInput;
import com.greenhouse.backend.work.api.effect.StructureChangeResultPurpose;
import com.greenhouse.backend.work.api.effect.StructureChangeSourceInput;
import com.greenhouse.backend.work.api.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.application.operation.StructureChangeRecordService;
import com.greenhouse.backend.work.dto.effect.StructureChangeRecordBatchCreateRequest;
import com.greenhouse.backend.work.dto.effect.StructureChangeRecordCreateRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationCreateRequest;
import com.greenhouse.backend.work.repository.WorkTypeRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;

/** Existing placement/undo fixtures now execute the same record workflow as the UI. */
@TestComponent
@RequiredArgsConstructor
public class MovementTestSupport {

  private final OrchidGroupRepository groups;

  private final StructureChangeRecordService records;

  private final WorkTypeRepository types;

  private final Clock clock;

  @Transactional
  public OrchidGroupResponse move(Long id, MoveTestRequest request) {
    var group = groups.findDetailById(id).orElseThrow();
    var date = TimeConfig.farmToday(clock);
    var operation =
        new WorkOperationCreateRequest(
            types.findByCode("MOVEMENT").orElseThrow().getId(),
            "자리 이동",
            date,
            null,
            WorkSourceScopeType.MANUAL_SELECTION,
            null,
            null,
            List.of(id),
            null,
            request.worker(),
            request.memo(),
            null);
    var execution =
        new StructureChangeCommand(
            UUID.randomUUID().toString(),
            date,
            request.worker(),
            request.memo(),
            List.of(new StructureChangeSourceInput(id, group.getQuantity(), null, null)),
            List.of(
                new StructureChangeResultInput(
                    request.toBedZoneId(),
                    group.getQuantity(),
                    id,
                    null,
                    null,
                    StructureChangeResultPurpose.NORMAL,
                    null,
                    null,
                    false,
                    request.startPosition(),
                    request.endPosition(),
                    null)));
    records.createStructureChangeRecords(
        new StructureChangeRecordBatchCreateRequest(
            List.of(new StructureChangeRecordCreateRequest(operation, execution))));
    return OrchidGroupResponse.from(groups.findDetailById(id).orElseThrow(), date);
  }

  public record MoveTestRequest(
      Long toBedZoneId,
      BigDecimal startPosition,
      BigDecimal endPosition,
      String worker,
      String memo) {}
}
