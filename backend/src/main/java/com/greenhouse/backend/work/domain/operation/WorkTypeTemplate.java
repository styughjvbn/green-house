package com.greenhouse.backend.work.domain.operation;

import com.greenhouse.backend.work.api.effect.WorkEffectKind;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum WorkTypeTemplate {
  PESTICIDE("RECORD_ONLY", WorkEffectKind.RECORD_ONLY, true),
  FERTILIZER("RECORD_ONLY", WorkEffectKind.RECORD_ONLY, true),
  REPOT("REPOT", WorkEffectKind.STRUCTURE_CHANGE, false),
  CLEANUP("RECORD_ONLY", WorkEffectKind.RECORD_ONLY, true),
  DISCARD("DISCARD", WorkEffectKind.ATTRIBUTE_CHANGE, false),
  STATUS("RECORD_ONLY", WorkEffectKind.RECORD_ONLY, true),
  MEMO("RECORD_ONLY", WorkEffectKind.RECORD_ONLY, true),
  MOVEMENT("MOVE", WorkEffectKind.ATTRIBUTE_CHANGE, false),
  RECONCILIATION("RECONCILIATION", WorkEffectKind.ATTRIBUTE_CHANGE, false);

  private final String handlerCode;

  private final WorkEffectKind effectKind;

  private final boolean customTypeAllowed;

  public String handlerCode() {
    return handlerCode;
  }

  public WorkEffectKind effectKind() {
    return effectKind;
  }

  public boolean isCustomTypeAllowed() {
    return customTypeAllowed;
  }
}
