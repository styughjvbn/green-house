package com.greenhouse.backend.farm.application.structure;

import java.math.BigDecimal;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

/** Union of occupied intervals and the remaining gaps; touching endpoints do not overlap. */
final class PlacementIntervalIndex {
  private final TreeMap<BigDecimal, BigDecimal> occupied = new TreeMap<>();
  private final TreeMap<BigDecimal, BigDecimal> gaps = new TreeMap<>();
  private final TreeSet<BigDecimal> singleSlotStarts = new TreeSet<>();

  PlacementIntervalIndex(List<OrchidPlacementPolicy.PlacementRange> ranges, BigDecimal maximum) {
    for (var range : ranges) merge(range.startPosition(), range.endPosition());
    if (maximum != null) {
      BigDecimal cursor = BigDecimal.ZERO.setScale(2);
      for (var range : occupied.entrySet()) {
        if (range.getKey().compareTo(maximum) >= 0) break;
        if (range.getKey().compareTo(cursor) > 0) addGap(cursor, range.getKey());
        cursor = cursor.max(range.getValue());
      }
      if (cursor.compareTo(maximum) < 0) addGap(cursor, maximum);
    }
  }

  boolean overlaps(BigDecimal start, BigDecimal end) {
    var left = occupied.floorEntry(start);
    if (left != null && left.getValue().compareTo(start) > 0) return true;
    var right = occupied.ceilingEntry(start);
    return right != null && right.getKey().compareTo(end) < 0;
  }

  BigDecimal firstSingleSlot() {
    return singleSlotStarts.isEmpty() ? null : singleSlotStarts.first();
  }

  void add(BigDecimal start, BigDecimal end) {
    var gap = gaps.floorEntry(start);
    if (gap == null || gap.getValue().compareTo(start) <= 0) gap = gaps.ceilingEntry(start);
    while (gap != null && gap.getKey().compareTo(end) < 0) {
      BigDecimal gapStart = gap.getKey();
      BigDecimal gapEnd = gap.getValue();
      gaps.remove(gapStart);
      singleSlotStarts.remove(gapStart);
      if (gapStart.compareTo(start) < 0) addGap(gapStart, start);
      if (end.compareTo(gapEnd) < 0) addGap(end, gapEnd);
      gap = gaps.ceilingEntry(start);
    }
    merge(start, end);
  }

  private void merge(BigDecimal start, BigDecimal end) {
    var left = occupied.floorEntry(start);
    if (left != null && left.getValue().compareTo(start) >= 0) {
      start = left.getKey();
      end = end.max(left.getValue());
      occupied.remove(left.getKey());
    }
    var next = occupied.ceilingEntry(start);
    while (next != null && next.getKey().compareTo(end) <= 0) {
      end = end.max(next.getValue());
      occupied.remove(next.getKey());
      next = occupied.ceilingEntry(start);
    }
    occupied.put(start, end);
  }

  private void addGap(BigDecimal start, BigDecimal end) {
    if (start.compareTo(end) >= 0) return;
    gaps.put(start, end);
    if (end.subtract(start).compareTo(BigDecimal.ONE) >= 0) singleSlotStarts.add(start);
  }
}
