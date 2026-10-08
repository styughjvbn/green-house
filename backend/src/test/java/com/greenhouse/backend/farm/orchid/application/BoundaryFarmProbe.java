package com.greenhouse.backend.farm.orchid.application;

import com.greenhouse.backend.work.operation.application.BoundaryWorkProbe;

public class BoundaryFarmProbe {
  public void bypass(BoundaryWorkProbe work) {
    work.internalHelper();
  }

  public Runnable reference(BoundaryWorkProbe work) {
    return work::internalHelper;
  }

  public static class ReferenceOnly {
    public Runnable reference(BoundaryWorkProbe work) {
      return work::internalHelper;
    }
  }
}
