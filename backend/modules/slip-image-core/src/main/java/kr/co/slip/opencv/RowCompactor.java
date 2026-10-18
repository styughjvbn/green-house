package kr.co.slip.opencv;

import java.util.List;
import kr.co.slip.api.ProcessingResult.Row;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

/** Crops a tail only; retained rows are never compressed or moved. */
final class RowCompactor {
    private RowCompactor() {}

    /** originY uses corrected-image coordinates. Null means the whole area was discarded. */
    static Mat compact(Mat input, int originY, List<Row> rows, MatScope scope) {
        int cutoff = rows.stream().filter(row -> row.removed() && BlankRowCutoff.CUTOFF.equals(row.reason()))
                .mapToInt(Row::top).min().orElse(originY + input.rows());
        int end = Math.min(input.rows(), cutoff - originY);
        if (end <= 0) return null;
        return scope.own(scope.own(input.submat(new Rect(0, 0, input.cols(), end))).clone());
    }
}
