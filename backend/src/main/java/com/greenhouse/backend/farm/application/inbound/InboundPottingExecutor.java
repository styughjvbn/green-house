package com.greenhouse.backend.farm.application.inbound;

import com.greenhouse.backend.work.api.effect.InboundPottingCommand;
import com.greenhouse.backend.work.api.effect.WorkEffectCommand;
import com.greenhouse.backend.work.api.effect.WorkEffectContext;
import com.greenhouse.backend.work.api.effect.WorkEffectKind;
import com.greenhouse.backend.work.api.effect.WorkEffectResults;
import com.greenhouse.backend.work.api.effect.WorkExecutionResult;
import com.greenhouse.backend.work.api.target.WorkTargetReferenceType;
import com.greenhouse.backend.work.application.effect.InboundPottingCommandCodec;
import com.greenhouse.backend.work.spi.effect.WorkEffectHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InboundPottingExecutor implements WorkEffectHandler {

  private final InboundPottingService inboundPottingService;

  private final InboundPottingCommandCodec commandCodec;

  @Override
  public String supports() {
    return "POTTING";
  }

  @Override
  public WorkEffectKind effectKind() {
    return WorkEffectKind.STRUCTURE_CHANGE;
  }

  @Override
  public WorkExecutionResult execute(WorkEffectContext context, WorkEffectCommand command) {
    var target = context.target();
    if (target == null || target.referenceType() != WorkTargetReferenceType.INBOUND_RECORD) {
      throw new IllegalArgumentException("포트 작업에는 입고 기록 대상이 필요합니다.");
    }
    InboundPottingCommand request =
        command.payload() == null
            ? commandCodec.decode(target.inboundRecordId(), command.resultDetails())
            : command.payloadAs(InboundPottingCommand.class);
    if (!target.inboundRecordId().equals(request.inboundRecordId())) {
      throw new IllegalArgumentException("포트 작업 명령의 입고 기록이 대상과 일치해야 합니다.");
    }
    var result =
        inboundPottingService.potting(
            target.inboundRecordId(), request, context.operationId(), command.effectKey());
    var details =
        new WorkEffectResults.Potted(
            target.inboundRecordId(), result.createdOrchidGroupIds(), result.actualQuantity());
    return new WorkExecutionResult(
        "POTTING", details, result.createdOrchidGroupIds(), result.mutationLink());
  }
}
