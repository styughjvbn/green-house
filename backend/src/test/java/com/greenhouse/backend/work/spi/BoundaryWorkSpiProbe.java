package com.greenhouse.backend.work.spi;

/** Bytecode-only fixture for an SPI implemented by another module. */
public interface BoundaryWorkSpiProbe {
  String read();
}
