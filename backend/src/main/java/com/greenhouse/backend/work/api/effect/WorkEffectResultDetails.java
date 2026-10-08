package com.greenhouse.backend.work.api.effect;

import java.util.Map;

/** Execution values converted to the existing JSON only when a historical fact is stored. */
public sealed interface WorkEffectResultDetails
    permits WorkEffectResults.Transformation,
        WorkEffectResults.Merged,
        WorkEffectResults.Created,
        WorkEffectResults.Potted,
        WorkEffectResults.Moved,
        WorkEffectResults.Discarded,
        WorkEffectResults.Corrected,
        WorkEffectResults.Json {

  Map<String, Object> toMap();
}
