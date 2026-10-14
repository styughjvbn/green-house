package com.greenhouse.backend.farm.structure.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PlacementIntervalIndexTest {
  @Test
  void indexedGapsAndOverlapAgreeWithFullScanAcrossFragmentedAndOverlappingRanges() {
    var random = new Random(33);
    for (int scenario = 0; scenario < 100; scenario++) {
      var ranges = new ArrayList<OrchidPlacementPolicy.PlacementRange>();
      for (int i = 0; i < 20; i++) {
        var start = BigDecimal.valueOf(random.nextInt(170) - 10, 1);
        ranges.add(
            new OrchidPlacementPolicy.PlacementRange(
                start, start.add(BigDecimal.valueOf(random.nextInt(20) + 1, 1))));
      }
      var index = new PlacementIntervalIndex(ranges, BigDecimal.valueOf(20));
      for (int i = 0; i < 20; i++) {
        BigDecimal cursor = BigDecimal.ZERO.setScale(2);
        BigDecimal expected = null;
        for (var range :
            ranges.stream()
                .sorted(Comparator.comparing(OrchidPlacementPolicy.PlacementRange::startPosition))
                .toList()) {
          if (range.startPosition().subtract(cursor).compareTo(BigDecimal.ONE) >= 0) {
            expected = cursor;
            break;
          }
          cursor = cursor.max(range.endPosition());
        }
        if (expected == null
            && BigDecimal.valueOf(20).subtract(cursor).compareTo(BigDecimal.ONE) >= 0)
          expected = cursor;
        assertThat(index.firstSingleSlot()).isEqualTo(expected);
        var start = BigDecimal.valueOf(random.nextInt(200), 1);
        var end = start.add(BigDecimal.ONE);
        assertThat(index.overlaps(start, end))
            .isEqualTo(
                ranges.stream()
                    .anyMatch(
                        r ->
                            start.compareTo(r.endPosition()) < 0
                                && end.compareTo(r.startPosition()) > 0));
        if (expected != null) {
          var allocated =
              new OrchidPlacementPolicy.PlacementRange(expected, expected.add(BigDecimal.ONE));
          assertThat(index.overlaps(allocated.startPosition(), allocated.endPosition())).isFalse();
          index.add(allocated.startPosition(), allocated.endPosition());
          ranges.add(allocated);
        }
      }
    }
  }
}
