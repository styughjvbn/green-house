package com.greenhouse.backend.work.application.effect;

import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroupRelationType;

public record HistoricalWorkEffectLink(
		Long orchidGroupId,
		WorkEffectOrchidGroupRelationType relationType) {
}
