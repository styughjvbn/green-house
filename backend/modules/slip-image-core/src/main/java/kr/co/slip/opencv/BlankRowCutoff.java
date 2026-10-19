package kr.co.slip.opencv;

import java.util.Comparator;
import java.util.List;
import static kr.co.slip.api.ProcessingResult.*;

/** Finds one tail boundary per document; isolated blank rows do not change its layout. */
final class BlankRowCutoff {
    static final String EMPTY = "NO_SIGNIFICANT_INK";
    static final String CUTOFF = "CONSECUTIVE_EMPTY_ROWS_CUTOFF";
    private BlankRowCutoff() {}

    static List<Table> apply(List<Table> tables) {
        int cutoff = tables.stream().mapToInt(BlankRowCutoff::firstRun).min().orElse(Integer.MAX_VALUE);
        if (cutoff == Integer.MAX_VALUE) return tables;
        return tables.stream().map(table -> {
            List<Row> rows = table.rows().stream().map(row -> row.top() >= cutoff
                    ? new Row(row.index(), row.top(), row.bottom(), row.inkRatio(), row.inkPixels(), true, CUTOFF)
                    : row).toList();
            return new Table(table.index(), table.bounds(), table.horizontalLines(), table.verticalLines(),
                    rows, (int) rows.stream().filter(Row::removed).count(), table.form());
        }).toList();
    }

    private static int firstRun(Table table) {
        int count = 0, previousBottom = -1, previousIndex = -1;
        for (Row row : table.rows().stream().sorted(Comparator.comparingInt(Row::top)).toList()) {
            if (!EMPTY.equals(row.reason())) {
                count = 0;
            } else {
                if (row.top() != previousBottom || row.index() != previousIndex + 1) count = 0;
                if (++count == 2) return row.top();
            }
            previousBottom = row.bottom();
            previousIndex = row.index();
        }
        return Integer.MAX_VALUE;
    }
}
