package kr.co.slip.opencv;

import java.util.List;
import kr.co.slip.api.ProcessingOptions;
import kr.co.slip.api.ProcessingResult.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TableSpacingTest {
    private Table table(int y, int height, List<Integer> lines) {
        return new Table(1, new Box(0, y, 400, height), lines, List.of(), List.of(), 0);
    }

    @Test void thresholdIsInclusiveAndConfigurable() {
        var upper = table(0, 100, List.of(0, 20, 40, 60, 80, 100));
        var exact = table(210, 100, List.of(210, 230, 250, 270, 290, 310));
        var shortGap = table(209, 100, List.of(209, 229, 249, 269, 289, 309));
        assertTrue(TableSpacing.separate(upper, exact, 5.5));
        assertFalse(TableSpacing.separate(upper, shortGap, 5.5));
        assertFalse(TableSpacing.separate(upper, exact, 6));
    }

    @Test void averageUsesBothTablesAndIgnoresHeaderAndDuplicatePeaks() {
        var upper = table(0, 182, List.of(0, 60, 100, 120, 122, 142, 162, 182));
        var lower = table(347, 150, List.of(347, 377, 407, 437, 467, 497));
        assertTrue(TableSpacing.separate(upper, lower, 6.7));
        assertFalse(TableSpacing.separate(upper, lower, 6.8));
    }

    @Test void absentGridAndOverlappingTablesDoNotInventSeparation() {
        var upper = table(0, 100, List.of(0, 20, 40, 60, 80, 100));
        assertFalse(TableSpacing.separate(upper, table(400, 100, List.of()), 5.5));
        assertFalse(TableSpacing.separate(upper, table(80, 100, List.of(80, 100, 120, 140)), 5.5));
    }

    @Test void optionsPreserveCustomMultiplierAndRejectInvalidValues() {
        assertEquals(5.5, ProcessingOptions.defaults().documentSeparationRowMultiplier());
        var options = ProcessingOptions.defaults().withDocumentSeparationRowMultiplier(6).withDocumentHeaderRowMultiplier(8)
                .withRotation(90).withMaxDimension(1024).withMinimumInkRatio(.01)
                .withOutputMode(kr.co.slip.api.OutputMode.PRODUCTION);
        assertEquals(6, options.documentSeparationRowMultiplier());
        assertEquals(8, options.documentHeaderRowMultiplier());
        assertEquals(7, ProcessingOptions.defaults().documentHeaderRowMultiplier());
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> ProcessingOptions.defaults().withDocumentSeparationRowMultiplier(invalid));
            assertThrows(IllegalArgumentException.class,
                    () -> ProcessingOptions.defaults().withDocumentHeaderRowMultiplier(invalid));
        }
    }
}
