package com.greenhouse.backend.work.api.effect;

import java.util.List;

/** Saved Work effect facts used by Farm to assemble structure-change lineage. */
public interface StructureChangeLineageQueryApi {

  List<StructureChangeLineageEffectView> findByOrchidGroupId(Long orchidGroupId);
}
