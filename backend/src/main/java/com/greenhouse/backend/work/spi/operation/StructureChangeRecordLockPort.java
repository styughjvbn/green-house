package com.greenhouse.backend.work.spi.operation;

import java.util.Collection;

public interface StructureChangeRecordLockPort {
  void lock(Collection<Long> sourceOrchidGroupIds, Collection<Long> resultBedZoneIds);
}
