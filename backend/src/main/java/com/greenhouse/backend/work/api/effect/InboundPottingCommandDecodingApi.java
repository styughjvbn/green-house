package com.greenhouse.backend.work.api.effect;

import java.util.Map;

/** Work-owned compatibility decoding of saved potting details for Farm effect execution. */
public interface InboundPottingCommandDecodingApi {

  InboundPottingCommand decode(Long inboundRecordId, Map<String, Object> details);
}
