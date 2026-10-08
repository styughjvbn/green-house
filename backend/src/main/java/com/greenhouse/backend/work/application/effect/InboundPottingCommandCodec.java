package com.greenhouse.backend.work.application.effect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.work.api.effect.InboundPottingCommand;
import com.greenhouse.backend.work.api.effect.InboundPottingResultInput;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Preserves the existing effect JSON independently of the typed execution payload. */
@Component
public class InboundPottingCommandCodec {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  public Map<String, Object> encode(InboundPottingCommand command) {
    Map<String, Object> details =
        new LinkedHashMap<>(
            objectMapper.convertValue(command, new TypeReference<Map<String, Object>>() {}));
    details.remove("idempotencyKey");
    details.remove("inboundRecordId");
    return details;
  }

  public InboundPottingCommand decode(Long inboundRecordId, Map<String, Object> details) {
    StoredDetails stored = objectMapper.convertValue(details, StoredDetails.class);
    return new InboundPottingCommand(
        null,
        inboundRecordId,
        stored.pottingDate(),
        stored.results(),
        stored.worker(),
        stored.memo());
  }

  private record StoredDetails(
      LocalDate pottingDate, List<InboundPottingResultInput> results, String worker, String memo) {}
}
