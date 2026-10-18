package kr.co.slip.opencv;

import java.util.List;
import java.util.Map;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import static kr.co.slip.api.ProcessingResult.*;

/** Compacts a separated document after its tables have been analyzed. */
final class PaperCompactor {
    private PaperCompactor() {}

    /** Preserve the complete separated document above its consecutive-blank-row cutoff. */
    static void compact(Mat cutout, Rect crop, List<Table> tables, int index,
                                     Map<String, byte[]> images, List<String> warnings, MatScope scope) {
        if (tables.isEmpty()) return;
        int previousBottom = crop.y;
        for (Table table : tables) {
            Box box = table.bounds();
            if (box.x() < crop.x || box.y() < crop.y || box.x() + box.width() > crop.x + crop.width
                    || box.y() + box.height() > crop.y + crop.height || box.y() < previousBottom) {
                warnings.add("문서 " + index + ": 표가 잘렸거나 세로 영역이 겹쳐 문서 빈 행 축약을 생략했습니다.");
                return;
            }
            previousBottom = box.y() + box.height();
        }
        List<Row> rows = tables.stream().flatMap(table -> table.rows().stream()).toList();
        Mat compact = RowCompactor.compact(cutout, crop.y, rows, scope);
        if (compact != null) images.put(String.format("paper-%02d-compacted.png", index), png(compact, scope));
    }

    private static byte[] png(Mat image, MatScope scope) {
        MatOfByte bytes = scope.own(new MatOfByte());
        if (!Imgcodecs.imencode(".png", image, bytes)) throw new IllegalStateException("PNG encode failed");
        return bytes.toArray();
    }
}
