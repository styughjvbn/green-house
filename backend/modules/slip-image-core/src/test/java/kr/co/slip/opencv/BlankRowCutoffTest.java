package kr.co.slip.opencv;

import java.util.List;
import org.junit.jupiter.api.Test;
import static kr.co.slip.api.ProcessingResult.*;
import static org.junit.jupiter.api.Assertions.*;

class BlankRowCutoffTest {
    @Test void isolatedBlankRowsDoNotChangeLayoutOrRemoveLaterContent() {
        var input = List.of(table(1, row(0, 100, "PROTECTED_HEADER"), blank(1, 120), row(2, 140, "CONTENT_PRESENT"), blank(3, 160)));
        assertEquals(input, BlankRowCutoff.apply(input));
    }

    @Test void twoBlankRowsKeepTheFirstAndCutFromTheSecondIncludingLaterWrittenRowsAndTables() {
        var output = BlankRowCutoff.apply(List.of(table(1, row(0, 100, "PROTECTED_HEADER"),
                blank(1, 120), blank(2, 140), row(3, 160, "CONTENT_PRESENT")),
                table(2, row(0, 300, "UNRECOGNIZED_LAYOUT"))));
        assertFalse(output.getFirst().rows().getFirst().removed());
        assertFalse(output.getFirst().rows().get(1).removed());
        assertTrue(output.getFirst().rows().subList(2, 4).stream().allMatch(Row::removed));
        assertTrue(output.getLast().rows().getFirst().removed());
        assertEquals(BlankRowCutoff.CUTOFF, output.getLast().rows().getFirst().reason());
    }

    @Test void headersUnknownRowsGapsAndTableBoundariesBreakAStreak() {
        for (String separator : List.of("PROTECTED_HEADER", "UNRECOGNIZED_LAYOUT", "CONTENT_PRESENT")) {
            var input = List.of(table(1, blank(0, 100), row(1, 120, separator), blank(2, 140)));
            assertEquals(input, BlankRowCutoff.apply(input));
        }
        var gap = List.of(table(1, blank(0, 100), blank(1, 180)));
        assertEquals(gap, BlankRowCutoff.apply(gap));
        var twoTables = List.of(table(1, blank(0, 100)), table(2, blank(0, 120)));
        assertEquals(twoTables, BlankRowCutoff.apply(twoTables));
    }

    @Test void aCutInOneDocumentDoesNotApplyToAnotherDocument() {
        var upper = List.of(table(1, blank(0, 100), blank(1, 120), blank(2, 140)));
        var lower = List.of(table(2, row(0, 300, "CONTENT_PRESENT"), blank(1, 320)));
        assertEquals(2, BlankRowCutoff.apply(upper).getFirst().removedRows());
        assertEquals(lower, BlankRowCutoff.apply(lower));
    }

    private static Row blank(int index, int top) { return row(index, top, BlankRowCutoff.EMPTY); }
    private static Row row(int index, int top, String reason) {
        return new Row(index, top, top + 20, reason.equals(BlankRowCutoff.EMPTY) ? 0 : .1, 0, false, reason);
    }
    private static Table table(int index, Row... rows) {
        return new Table(index, new Box(0, rows[0].top(), 100, rows[rows.length - 1].bottom() - rows[0].top()),
                List.of(), List.of(), List.of(rows), 0);
    }
}
