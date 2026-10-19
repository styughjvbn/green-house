package kr.co.slip.opencv;

import kr.co.slip.api.ProcessingResult;
import kr.co.slip.api.OutputMode;
import kr.co.slip.api.ProcessingOptions;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.util.*;
import static kr.co.slip.api.ProcessingResult.*;

/** Separates observed paper surfaces, never reconstructing an occluded part of a sheet. */
final class PaperSeparator {
    record Result(List<Paper> papers, Map<String, byte[]> images, List<String> warnings) {}
    private record ColorModel(double[] center, double[] sigma) {
        double distance(byte[] pixels, int offset) {
            double result = 0;
            for (int i = 0; i < 3; i++) result += Math.pow(((pixels[offset + i] & 255) - center[i]) / sigma[i], 2);
            return Math.sqrt(result);
        }
    }
    private record Seam(int position) {}
    private static final class Scope extends MatScope {
        Mat kernel(int n) { return own(Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(n, n))); }
    }

    static Result separate(Mat image, List<Table> tables, boolean knownDocumentQuad) {
        return separate(image, tables, knownDocumentQuad, PixelScale.IDENTITY);
    }

    static Result separate(Mat image, List<Table> tables, boolean knownDocumentQuad, PixelScale pixels) {
        return separate(image, tables, knownDocumentQuad, pixels, OutputMode.ALL);
    }

    static Result separate(Mat image, List<Table> tables, boolean knownDocumentQuad, PixelScale pixels, OutputMode mode) {
        return separate(image, tables, knownDocumentQuad, pixels, mode, null);
    }

    static Result separate(Mat image, List<Table> tables, boolean knownDocumentQuad, PixelScale pixels, OutputMode mode,
                           java.util.function.BiConsumer<Integer, Mat> cutoutConsumer) {
        return separate(image, tables, knownDocumentQuad, pixels, mode, cutoutConsumer,
                ProcessingOptions.defaults().documentSeparationRowMultiplier());
    }

    static Result separate(Mat image, List<Table> tables, boolean knownDocumentQuad, PixelScale pixels, OutputMode mode,
                           java.util.function.BiConsumer<Integer, Mat> cutoutConsumer, double rowMultiplier) {
        return separate(image, tables, knownDocumentQuad, pixels, mode, cutoutConsumer,
                rowMultiplier, ProcessingOptions.defaults().documentHeaderRowMultiplier());
    }

    static Result separate(Mat image, List<Table> tables, boolean knownDocumentQuad, PixelScale pixels, OutputMode mode,
                           java.util.function.BiConsumer<Integer, Mat> cutoutConsumer, double rowMultiplier,
                           double headerMultiplier) {
        NativeOpenCv.load();
        try (Scope m = new Scope()) {
            Mat mask = knownDocumentQuad ? m.own(new Mat(image.size(), CvType.CV_8UC1, Scalar.all(255)))
                    : foreground(image, tables, pixels, m);
            Mat overlay = mode.review() ? m.own(image.clone()) : null;
            Map<String, byte[]> images = new LinkedHashMap<>();
            List<Paper> papers = new ArrayList<>();
            Mat usedMask = mode.diagnostics() ? m.own(Mat.zeros(image.size(), CvType.CV_8UC1)) : null;
            List<String> warnings = new ArrayList<>();
            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(m.own(mask.clone()), contours, m.mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            List<MatOfPoint> candidates = new ArrayList<>();
            for (MatOfPoint contour : contours) {
                m.own(contour);
                Rect bounds = Imgproc.boundingRect(contour);
                if (Imgproc.contourArea(contour) >= image.total() * .04 && bounds.width >= image.cols() * .18
                        && bounds.height >= image.rows() * .08) candidates.add(contour);
            }
            candidates.sort(Comparator.comparingInt(c -> Imgproc.boundingRect(c).y));
            for (MatOfPoint contour : candidates) {
                try (Scope componentScope = new Scope()) {
                    Mat component = componentScope.own(Mat.zeros(image.size(), CvType.CV_8UC1));
                    Imgproc.drawContours(component, List.of(contour), 0, Scalar.all(255), -1);
                    Rect bounds = Imgproc.boundingRect(contour);
                    List<Table> supported = tables.stream().filter(t -> inside(component, t.bounds())).sorted(
                            Comparator.comparingInt(t -> t.bounds().y())).toList();
                    List<Seam> seams = knownDocumentQuad ? List.of() : seams(supported, pixels, rowMultiplier, headerMultiplier);
                    for (int layer = 0; layer <= seams.size(); layer++) {
                        try (Scope layerScope = new Scope()) {
                            Mat regionMask = band(component, seams, layer, layerScope);
                            if (Core.countNonZero(regionMask) < image.total() * .025) continue;
                            // Convexify each sheet after splitting. Convexifying the whole stack first
                            // would introduce desk triangles between staggered sheets of different widths.
                            convexify(regionMask);
                            List<Table> paperTables = supported.stream().filter(t -> inside(regionMask, t.bounds())).toList();
                            protectTableContent(regionMask, paperTables, seams, layer, pixels);
                            if (usedMask != null) Core.bitwise_or(usedMask, regionMask, usedMask);
                            Rect crop = Imgproc.boundingRect(regionMask);
                            int index = papers.size() + 1;
                            Mat cutout = layerScope.mat();
                            Imgproc.cvtColor(layerScope.own(image.submat(crop)), cutout, Imgproc.COLOR_BGR2BGRA);
                            Core.insertChannel(layerScope.own(regionMask.submat(crop)), cutout, 3);
                            if (cutoutConsumer != null) cutoutConsumer.accept(index, cutout);
                            images.put(String.format("paper-%02d%s", index, ImageEncoding.extension(mode)), ImageEncoding.encode(cutout, mode));
                            List<XY> outline = outline(regionMask);
                            if (overlay != null) {
                                MatOfPoint polygon = layerScope.own(new MatOfPoint(outline.stream().map(p -> new Point(p.x(), p.y())).toArray(Point[]::new)));
                                Imgproc.polylines(overlay, List.of(polygon), true, new Scalar(220, 40, 200), 3);
                                Imgproc.putText(overlay, "PAPER " + index, new Point(crop.x + 12, crop.y + 35),
                                        Imgproc.FONT_HERSHEY_SIMPLEX, .9, new Scalar(220, 40, 200), 2);
                            }
                            papers.add(new Paper(index, new Box(crop.x, crop.y, crop.width, crop.height), outline,
                                    knownDocumentQuad ? "DOCUMENT_QUAD" : seams.isEmpty() ? "CONNECTED_PAPER_REGION" : "TABLE_SPACING_SPLIT",
                                    !knownDocumentQuad, paperTables.stream().map(Table::index).toList()));

                        }
                    }
                    if (overlay != null) for (Seam seam : seams) Imgproc.line(overlay, new Point(bounds.x, seam.position()),
                            new Point(bounds.x + bounds.width - 1, seam.position()), new Scalar(255, 180, 0), 3);

                }
            }
            if (overlay != null) images.put("06-paper-contours.png", png(overlay));
            if (usedMask != null) images.put("07-paper-mask.png", png(usedMask));
            if (papers.isEmpty()) warnings.add("종이와 배경을 안정적으로 분리하지 못했습니다. 종이 이미지를 임의로 잘라내지 않았습니다.");
            else if (!knownDocumentQuad) warnings.add("종이 분리는 색상·외곽의 볼록 포락선·표 간격에 대한 추정입니다. 가려진 내용을 복원하지 않으며 작은 가림 물체나 배경이 포함될 수 있어 분리 문서 이미지를 검토하세요.");
            return new Result(List.copyOf(papers), Collections.unmodifiableMap(images), List.copyOf(warnings));
        }
    }

    private static boolean inside(Mat mask, Box box) {
        int x = Math.min(mask.cols() - 1, Math.max(0, box.x() + box.width() / 2));
        int y = Math.min(mask.rows() - 1, Math.max(0, box.y() + box.height() / 2));
        return mask.get(y, x)[0] != 0;
    }

    /** Grid evidence protects writing even when a shadow or hand confuses the paper mask. */
    private static void protectTableContent(Mat mask, List<Table> tables, List<Seam> seams, int layer, PixelScale pixels) {
        try (Scope scope = new Scope()) {
            Mat protectedMask = scope.own(Mat.zeros(mask.size(), CvType.CV_8UC1));
            for (Table table : tables) {
                Box b = table.bounds();
                int margin = Math.max(pixels.length(12), (int) Math.round(b.width() * .04));
                int left = Math.max(0, b.x() - margin), top = Math.max(0, b.y() - pixels.length(12));
                int right = Math.min(mask.cols(), b.x() + b.width() + margin);
                int bottom = Math.min(mask.rows(), b.y() + b.height() + pixels.length(40));
                if (right > left && bottom > top)
                    Imgproc.rectangle(protectedMask, new Point(left, top), new Point(right - 1, bottom - 1), Scalar.all(255), -1);
            }
            Core.bitwise_or(mask, band(protectedMask, seams, layer, scope), mask);
        }
    }

    private static Mat foreground(Mat image, List<Table> tables, PixelScale pixels, Scope parent) {
        try (Scope m = new Scope()) {
            PixelScale analysis = pixels.capped(Math.max(image.rows(), image.cols()), 900);
            double scale = Math.min(1, 900. / Math.max(image.rows(), image.cols()));
            Mat small = m.mat(), smooth = m.mat(), lab = m.mat();
            Imgproc.resize(image, small, new Size(Math.round(image.cols() * scale), Math.round(image.rows() * scale)), 0, 0, Imgproc.INTER_AREA);
            Imgproc.GaussianBlur(small, smooth, new Size(analysis.odd(9), analysis.odd(9)), 0);
            Imgproc.cvtColor(smooth, lab, Imgproc.COLOR_BGR2Lab);
            List<Rect> seeds = new ArrayList<>();
            for (Table table : tables) {
                Box b = table.bounds();
                int x = Math.max(0, (int) ((b.x() + b.width() * .12) * scale));
                int y = Math.max(0, (int) ((b.y() + b.height() * .12) * scale));
                int right = Math.min(lab.cols(), (int) ((b.x() + b.width() * .88) * scale));
                int bottom = Math.min(lab.rows(), (int) ((b.y() + b.height() * .88) * scale));
                if (right > x + analysis.length(5) && bottom > y + analysis.length(5)) seeds.add(new Rect(x, y, right - x, bottom - y));
            }
            if (seeds.isEmpty()) seeds.add(new Rect(lab.cols() / 3, lab.rows() / 3, lab.cols() / 3, lab.rows() / 3));
            byte[] labPixels = new byte[(int) (lab.total() * 3)];
            lab.get(0, 0, labPixels);
            List<ColorModel> models = seeds.stream().map(r -> model(labPixels, lab.cols(), r)).toList();
            Mat labels = m.own(new Mat(lab.size(), CvType.CV_8UC1, Scalar.all(Imgproc.GC_PR_BGD)));
            Mat sureSeed = m.own(Mat.zeros(lab.size(), CvType.CV_8UC1));
            for (Rect seed : seeds) Imgproc.rectangle(sureSeed, seed.tl(), seed.br(), Scalar.all(255), -1);
            byte[] seedPixels = new byte[(int) sureSeed.total()]; sureSeed.get(0, 0, seedPixels);
            byte[] labelPixels = new byte[(int) labels.total()];
            int foreground = 0, background = 0, borderBackground = 0;
            for (int y = 0; y < lab.rows(); y++) for (int x = 0; x < lab.cols(); x++) {
                int pixel = y * lab.cols() + x;
                double distance = 100;
                for (ColorModel model : models) distance = Math.min(distance, model.distance(labPixels, pixel * 3));
                int label = distance < 2.3 ? Imgproc.GC_PR_FGD : Imgproc.GC_PR_BGD;
                if (distance > 4 || ((x < analysis.length(3) || y < analysis.length(3)
                        || x >= lab.cols() - analysis.length(3) || y >= lab.rows() - analysis.length(3)) && distance > 1.8)) {
                    label = Imgproc.GC_BGD; background++;
                    if (x < analysis.length(8) || y < analysis.length(8)
                            || x >= lab.cols() - analysis.length(8) || y >= lab.rows() - analysis.length(8)) borderBackground++;
                } else if (distance < 1.7 && seedPixels[pixel] != 0) {
                    label = Imgproc.GC_FGD; foreground++;
                }
                labelPixels[pixel] = (byte) label;
            }
            labels.put(0, 0, labelPixels);
            Mat result = parent.own(Mat.zeros(image.size(), CvType.CV_8UC1));
            if (foreground < analysis.area(50) || background < analysis.area(50) || borderBackground < analysis.area(30)) return result;
            Imgproc.grabCut(small, labels, new Rect(), m.mat(), m.mat(), 3, Imgproc.GC_INIT_WITH_MASK);
            Mat sure = m.mat(), probable = m.mat();
            Core.compare(labels, Scalar.all(Imgproc.GC_FGD), sure, Core.CMP_EQ);
            Core.compare(labels, Scalar.all(Imgproc.GC_PR_FGD), probable, Core.CMP_EQ);
            Core.bitwise_or(sure, probable, sure);
            Imgproc.morphologyEx(sure, sure, Imgproc.MORPH_CLOSE, m.kernel(analysis.odd(7)));
            Imgproc.resize(sure, result, image.size(), 0, 0, Imgproc.INTER_NEAREST);
            return result;

        }
    }

    private static ColorModel model(byte[] lab, int cols, Rect region) {
        List<double[]> samples = new ArrayList<>();
        int step = Math.max(1, Math.min(region.width, region.height) / 80);
        for (int y = region.y; y < region.y + region.height; y += step)
            for (int x = region.x; x < region.x + region.width; x += step) {
                int offset = (y * cols + x) * 3;
                if ((lab[offset] & 255) > 60) samples.add(new double[]{lab[offset] & 255, lab[offset + 1] & 255, lab[offset + 2] & 255});
            }
        if (samples.isEmpty()) {
            int offset = ((region.y + region.height / 2) * cols + region.x + region.width / 2) * 3;
            samples.add(new double[]{lab[offset] & 255, lab[offset + 1] & 255, lab[offset + 2] & 255});
        }
        double[] center = new double[3], sigma = new double[3];
        for (int channel = 0; channel < 3; channel++) {
            int c = channel;
            double[] values = samples.stream().mapToDouble(p -> p[c]).sorted().toArray();
            center[c] = values[values.length / 2];
            double spread = (values[values.length * 3 / 4] - values[values.length / 4]) / 1.349;
            sigma[c] = Math.max(c == 0 ? 12 : 3, Math.min(c == 0 ? 35 : 8, spread));
        }
        return new ColorModel(center, sigma);
    }

    private static List<Seam> seams(List<Table> tables, PixelScale pixels,
                                    double rowMultiplier, double headerMultiplier) {
        List<Seam> result = new ArrayList<>();
        for (int i = 0; i + 1 < tables.size(); i++) {
            Table above = tables.get(i), below = tables.get(i + 1);
            if (!TableSpacing.separate(above, below, rowMultiplier)) continue;
            int proposed = (int) Math.round(below.bounds().y()
                    - TableSpacing.averageRowHeight(above, below) * headerMultiplier);
            // Preserve the upper table and its last grid line even when the requested header is taller than the gap.
            int minimum = above.bounds().y() + above.bounds().height() + pixels.length(12);
            int cut = Math.min(below.bounds().y() - 1, Math.max(minimum, proposed));
            result.add(new Seam(cut));
        }
        return List.copyOf(result);
    }

    private static Mat band(Mat component, List<Seam> seams, int index, Scope m) {
        Mat result = m.own(component.clone());
        byte[] row = new byte[component.cols()];
        for (int y = 0; y < component.rows(); y++) {
            component.get(y, 0, row);
            for (int x = 0; x < row.length; x++) if ((index > 0 && y < seams.get(index - 1).position())
                    || (index < seams.size() && y >= seams.get(index).position())) row[x] = 0;
            result.put(y, 0, row);
        }
        return result;
    }

    private static void convexify(Mat mask) {
        try (Scope m = new Scope()) {
            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(m.own(mask.clone()), contours, m.mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            List<Point> all = new ArrayList<>();
            for (MatOfPoint contour : contours) { m.own(contour); all.addAll(Arrays.asList(contour.toArray())); }
            if (all.size() < 3) return;
            MatOfPoint points = m.own(new MatOfPoint(all.toArray(Point[]::new)));
            MatOfInt indices = m.own(new MatOfInt());
            Imgproc.convexHull(points, indices);
            Point[] array = points.toArray();
            MatOfPoint hull = m.own(new MatOfPoint(Arrays.stream(indices.toArray()).mapToObj(i -> array[i]).toArray(Point[]::new)));
            m.own(mask.setTo(Scalar.all(0)));
            Imgproc.fillConvexPoly(mask, hull, Scalar.all(255));

        }
    }

    private static List<XY> outline(Mat mask) {
        try (Scope m = new Scope()) {
            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(m.own(mask.clone()), contours, m.mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            MatOfPoint largest = null;
            for (MatOfPoint c : contours) { m.own(c); if (largest == null || Imgproc.contourArea(c) > Imgproc.contourArea(largest)) largest = c; }
            if (largest == null) return List.of();
            MatOfPoint2f curve = m.own(new MatOfPoint2f(largest.toArray())), approx = m.own(new MatOfPoint2f());
            Imgproc.approxPolyDP(curve, approx, Imgproc.arcLength(curve, true) * .001, true);
            return Arrays.stream(approx.toArray()).map(p -> new XY(p.x, p.y)).toList();

        }
    }
    private static byte[] png(Mat image) {
        MatOfByte bytes = new MatOfByte();
        try {
            if (!Imgcodecs.imencode(".png", image, bytes)) throw new IllegalStateException("PNG encode failed");
            return bytes.toArray();
        } finally { bytes.release(); }
    }
}
