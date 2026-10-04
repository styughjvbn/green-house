package com.greenhouse.backend.work.application.effect;

/** Application inputs accepted by work execution; persistence JSON is a separate contract. */
public sealed interface WorkEffectPayload
    permits StructureChangeCommand,
        InboundPottingCommand,
        LegacyRepotCommand,
        WorkReconciliationCommand {}
