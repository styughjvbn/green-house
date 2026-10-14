package com.greenhouse.backend.farm.orchid.integration;

import com.greenhouse.backend.work.api.BoundaryWorkApiProbe;
import com.greenhouse.backend.work.spi.BoundaryWorkSpiProbe;

public class BoundaryWorkAdapterProbe implements BoundaryWorkSpiProbe {
  public String read() {
    var value = new BoundaryWorkApiProbe("fixture");
    value.approvedOperation();
    return value.value();
  }

  public Runnable reference(BoundaryWorkApiProbe value) {
    return value::approvedOperation;
  }
}
