package com.greenhouse.backend.work.api.correction;

import java.util.Map;

public record WorkQuantityBalance(
    Long executionId,
    Map<Long, Integer> sourceInputQuantities,
    Map<Long, Integer> resultQuantities,
    int inputQuantity,
    int resultQuantity,
    int lossQuantity,
    int increaseQuantity,
    boolean inputEditable,
    boolean increaseAllowed,
    boolean lossEditable) {}
