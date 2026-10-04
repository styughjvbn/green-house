package com.greenhouse.backend.work.application.operation;

import java.util.Collection;

public interface StructureChangeRecordLockPort {
  void lock(Collection<Long> sourceOrchidGroupIds, Collection<Long> resultBedZoneIds);
}
