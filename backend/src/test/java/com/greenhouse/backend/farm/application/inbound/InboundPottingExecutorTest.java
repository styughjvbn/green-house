package com.greenhouse.backend.farm.application.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.work.application.effect.InboundPottingCommand;
import com.greenhouse.backend.work.application.effect.InboundPottingCommandCodec;
import com.greenhouse.backend.work.application.effect.InboundPottingResultInput;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectContext;
import com.greenhouse.backend.work.application.effect.WorkEffectResults;
import com.greenhouse.backend.work.application.effect.WorkMutationLink;
import com.greenhouse.backend.work.domain.target.WorkTargetReferenceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InboundPottingExecutorTest {

  private final InboundPottingService service = mock(InboundPottingService.class);
  private final InboundPottingExecutor executor =
      new InboundPottingExecutor(service, new InboundPottingCommandCodec());

  @Test
  void usesTheTypedPayloadWithoutDecodingThePersistenceMap() {
    var request = request(51L);
    var mutation = new WorkMutationLink(91L, UUID.randomUUID());
    when(service.potting(51L, request, 11L, "POTTING:51:key"))
        .thenReturn(new InboundPottingResult(null, List.of(71L), 13, mutation));

    var result = executor.execute(context(), command(request));

    verify(service).potting(51L, request, 11L, "POTTING:51:key");
    assertThat(result.details()).isInstanceOf(WorkEffectResults.Potted.class);
    assertThat(result.resultOrchidGroupIds()).containsExactly(71L);
    assertThat(result.mutationLink()).isEqualTo(mutation);
    assertThat(result.storedDetails())
        .containsEntry("inboundRecordId", 51L)
        .containsEntry("actualQuantity", 13)
        .containsEntry("createdOrchidGroupIds", List.of(71L));
  }

  @Test
  void rejectsAMismatchedTypedInboundBeforeApplyingAnyMutation() {
    assertThatThrownBy(() -> executor.execute(context(), command(request(52L))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("일치");
    verifyNoInteractions(service);
  }

  private WorkEffectContext context() {
    return new WorkEffectContext(
        11L,
        "POTTING",
        LocalDate.of(2026, 7, 18),
        null,
        new WorkEffectContext.Target(WorkTargetReferenceType.INBOUND_RECORD, null, 51L, null));
  }

  private InboundPottingCommand request(Long inboundId) {
    return new InboundPottingCommand(
        "key",
        inboundId,
        LocalDate.of(2026, 7, 18),
        List.of(
            new InboundPottingResultInput(
                7L,
                13,
                "2인치",
                2,
                "트레이",
                3,
                true,
                new BigDecimal("12.2500"),
                new BigDecimal("14.7500"),
                "결과 메모")),
        "포트 담당",
        "포트 완료");
  }

  private WorkEffectCommand command(InboundPottingCommand request) {
    return new WorkEffectCommand(
            LocalDateTime.of(2026, 7, 18, 0, 0),
            "포트 담당",
            Map.of("unknownPersistenceField", "must not be decoded for typed execution"),
            request)
        .withEffectKey("POTTING:51:key");
  }
}
