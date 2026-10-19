package kr.co.slip.opencv;

import kr.co.slip.api.ProcessingResult;

import kr.co.slip.api.ProcessingOptions;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import kr.co.slip.api.SlipImageProcessor;

import java.util.*;
import static kr.co.slip.api.ProcessingResult.*;

/** Low-level synchronous engine. Use SlipProcessors.bounded() for shared execution limits. */
public final class SlipProcessor implements SlipImageProcessor {
    public SlipProcessor() { NativeOpenCv.load(); }

    private static final class Mats extends MatScope {
        private PixelScale scale = PixelScale.IDENTITY;
        Mats() {}
        Mats(PixelScale scale) { this.scale = scale; }
        int px(double value) { return scale.length(value); }
        Size window(int value) { int side = scale.odd(value); return new Size(side, side); }
        Mat zeros(int h, int w, int type) { return own(Mat.zeros(h, w, type)); }
        Mat kernel(int w, int h) { return own(Imgproc.getStructuringElement(Imgproc.MORPH_RECT,
                new Size(scale.kernel(w), scale.kernel(h)))); }
        Mat lineKernel(int w, int h) { return own(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(w, h))); }
    }

    @Override public ProcessingResult process(byte[] input, ProcessingOptions options) {
        Attempt first = attempt(input, options, 1024);
        return first.retry() ? attempt(input, options, options.maxDimension()).result() : first.result();
    }

    private record Attempt(ProcessingResult result, boolean retry) {}

    private Attempt attempt(byte[] input, ProcessingOptions options, int analysisLimit) {
        if (input == null || input.length == 0 || input.length > 20 * 1024 * 1024)
            throw new IllegalArgumentException("Image must be between 1 byte and 20 MiB");
        Objects.requireNonNull(options, "options");
        try (Mats m = new Mats()) {
            Mat source;
            MatOfByte encoded = new MatOfByte(input);
            try { source = m.own(Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_COLOR)); }
            finally { encoded.release(); }
            if (source.empty()) throw new IllegalArgumentException("Cannot decode image");
            if ((long) source.rows() * source.cols() > 60_000_000L)
                throw new IllegalArgumentException("Image exceeds 60 megapixels");
            int sw = source.cols(), sh = source.rows();
            m.scale = PixelScale.forSource(Math.max(sw, sh), options.maxDimension());
            double scale = Math.min(1, (double) options.maxDimension() / Math.max(sw, sh));
            Mat work = m.mat();
            Imgproc.resize(source, work, new Size(Math.round(sw * scale), Math.round(sh * scale)), 0, 0, Imgproc.INTER_AREA);
            SourceCoordinates coordinates = new SourceCoordinates(sw, sh, work.cols(), work.rows());
            m.release(source);
            List<String> warnings = new ArrayList<>();
            if (analysisLimit > 1024) warnings.add("축소 분석의 표 구조가 불확실해 처리 해상도로 다시 분석했습니다.");
            Correction correction = correct(work, options, warnings, coordinates, m, analysisLimit);
            if (correction.image() != work) m.release(work);
            work = correction.image();
            Mat gray = m.mat(), binary = m.mat();
            Imgproc.cvtColor(work, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.adaptiveThreshold(gray, binary, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY_INV, m.scale.odd(35), 12);
            Mat horizontal = m.mat(), vertical = m.mat();
            Imgproc.morphologyEx(binary, horizontal, Imgproc.MORPH_OPEN, m.lineKernel(Math.max(m.px(30), work.cols() / 22), 1));
            Imgproc.morphologyEx(binary, vertical, Imgproc.MORPH_OPEN, m.lineKernel(1, Math.max(m.px(25), work.rows() / 45)));
            m.release(gray);
            clearBorder(horizontal, m.px(12));
            clearBorder(vertical, m.px(12));
            Mat grid = m.mat();
            Core.bitwise_or(horizontal, vertical, grid);
            List<Rect> regions = tableRegions(horizontal, work.size(), m);
            Mat rowOverlay = options.outputMode().review() ? m.own(work.clone()) : null;
            Map<String, byte[]> images = new LinkedHashMap<>();
            images.put("01-corrected" + ImageEncoding.extension(options.outputMode()), ImageEncoding.encode(work, options.outputMode()));
            if (options.outputMode().diagnostics()) {
                try (Mats drawing = new Mats(m.scale)) {
                    Mat overlay = drawing.own(work.clone());
                    drawing.own(overlay.setTo(new Scalar(0, 0, 255), horizontal));
                    drawing.own(overlay.setTo(new Scalar(0, 180, 0), vertical));
                    images.put("02-lines.png", png(overlay, drawing));
                }
                images.put("03-horizontal.png", png(horizontal, m));
                images.put("04-vertical.png", png(vertical, m));
            }
            List<Table> tables = new ArrayList<>();
            int tableIndex = 0;
            for (Rect region : regions) {
                try (Mats tableScope = new Mats(m.scale)) {
                    Mat hm = tableScope.own(horizontal.submat(region)), vm = tableScope.own(vertical.submat(region));
                    Mat tolerantHorizontal = tableScope.mat(), tolerantVertical = tableScope.mat();
                    Imgproc.dilate(hm, tolerantHorizontal, tableScope.kernel(1, 9));
                    Imgproc.dilate(vm, tolerantVertical, tableScope.kernel(7, 1));
                    List<Integer> ys = projections(tolerantHorizontal, true, .50);
                    List<Integer> xs = projections(tolerantVertical, false, .30);
                    if (ys.size() < 3 || xs.size() < 2) continue;
                    tableIndex++;
                    // Geometry only: paper separation must finish before any row decisions.
                    tables.add(new Table(tableIndex, new Box(region.x, region.y, region.width, region.height),
                            ys.stream().map(y -> y + region.y).toList(), xs.stream().map(x -> x + region.x).toList(),
                            List.of(), 0));

                }
            }
            if (tables.isEmpty()) warnings.add("안정적인 표 격자를 찾지 못해 빈 행을 제거하지 않았습니다.");
            Map<Integer, Mat> cutouts = new HashMap<>();
            PaperSeparator.Result separated = PaperSeparator.separate(work, tables, !correction.corners().isEmpty(), m.scale, options.outputMode(),
                    (index, cutout) -> cutouts.put(index, m.own(cutout.clone())), options.documentSeparationRowMultiplier(), options.documentHeaderRowMultiplier());
            images.putAll(separated.images());
            warnings.addAll(separated.warnings());
            tables = analyzeDocuments(binary, grid, tables, separated, cutouts, options, images, warnings, m);
            boolean uncertain = tables.isEmpty() || separated.papers().isEmpty()
                    || tables.stream().anyMatch(table -> table.rows().isEmpty()
                        || table.rows().stream().anyMatch(row -> row.reason().equals("UNRECOGNIZED_LAYOUT")));
            if (correction.reduced() && uncertain) {
                // Returning closes native buffers before the single full-resolution retry.
                return new Attempt(null, true);
            }
            for (Table table : tables) {
                try (Mats tableScope = new Mats(m.scale)) {
                    Box b = table.bounds();
                    Rect region = new Rect(b.x(), b.y(), b.width(), b.height());
                    if (table.rows().isEmpty() || !table.rows().stream().allMatch(Row::removed)) {
                        Mat compact = RowCompactor.compact(tableScope.own(work.submat(region)), b.y(), table.rows(), tableScope);
                        if (compact != null) images.put(String.format("table-%02d-compacted%s", table.index(), ImageEncoding.extension(options.outputMode())), ImageEncoding.encode(compact, options.outputMode()));
                    }
                    if (rowOverlay != null) for (Row row : table.rows()) if (row.removed())
                        Imgproc.rectangle(rowOverlay, new Point(b.x(), row.top()),
                                new Point(b.x() + b.width() - 1, row.bottom()), new Scalar(0, 0, 255), 2);
                }
            }
            if (rowOverlay != null) images.put("05-row-decisions.png", png(rowOverlay, m));
            // Report coordinates continue to describe the unshortened corrected image.
            return new Attempt(new ProcessingResult(new Report(correction.method(), sw, sh, work.cols(), work.rows(),
                    correction.corners(), List.copyOf(warnings), List.copyOf(tables),
                    correction.rotation(), separated.papers(), coordinates.inverse()), Collections.unmodifiableMap(images)), false);
        }
    }

    private static List<Table> analyzeDocuments(Mat binary, Mat grid, List<Table> geometry,
                                                PaperSeparator.Result separated, Map<Integer, Mat> cutouts, ProcessingOptions options,
                                                Map<String, byte[]> images, List<String> warnings, Mats parent) {
        if (separated.papers().isEmpty()) {
            warnings.add("문서 분리 결과가 없어 전체 사진을 하나의 분석 범위로 사용합니다.");
            return BlankRowCutoff.apply(geometry.stream().map(table -> analyzeTable(binary, grid, table, 0, 0,
                    options, warnings, parent)).toList());
        }
        Map<Integer, Table> analyzed = new HashMap<>();
        Map<Integer, Long> owners = separated.papers().stream().flatMap(p -> p.tableIndices().stream())
                .collect(java.util.stream.Collectors.groupingBy(i -> i, java.util.stream.Collectors.counting()));
        for (Paper paper : separated.papers()) {
            try (Mats scope = new Mats(parent.scale)) {
                Mat cutout = cutouts.get(paper.index());
                Mat alpha = scope.mat(), invisible = scope.mat();
                Core.extractChannel(cutout, alpha, 3);
                Core.compare(alpha, Scalar.all(0), invisible, Core.CMP_EQ);
                Box box = paper.bounds();
                Rect crop = new Rect(box.x(), box.y(), box.width(), box.height());
                Mat localBinary = scope.own(scope.own(binary.submat(crop)).clone());
                Mat localGrid = scope.own(scope.own(grid.submat(crop)).clone());
                scope.own(localBinary.setTo(Scalar.all(0), invisible));
                scope.own(localGrid.setTo(Scalar.all(0), invisible));
                List<Table> documentTables = geometry.stream().filter(t -> paper.tableIndices().contains(t.index())).toList();
                boolean complete = true;
                for (Table table : documentTables) {
                    Box b = table.bounds();
                    Rect local = new Rect(b.x() - crop.x, b.y() - crop.y, b.width(), b.height());
                    boolean contained = local.x >= 0 && local.y >= 0
                            && local.x + local.width <= cutout.cols() && local.y + local.height <= cutout.rows();
                    if (owners.get(table.index()) != 1 || !contained
                            || Core.countNonZero(scope.own(alpha.submat(local))) < local.area()) {
                        complete = false;
                        warnings.add("문서 " + paper.index() + ": 표 " + table.index()
                                + "의 소속 또는 전체 영역을 확인하지 못해 해당 표 분석과 문서 축약을 생략했습니다.");
                        continue;
                    }
                    analyzed.put(table.index(), analyzeTable(localBinary, localGrid, table,
                            crop.x, crop.y, options, warnings, scope));
                }
                if (complete) {
                    List<Table> decided = BlankRowCutoff.apply(documentTables.stream()
                            .map(t -> analyzed.get(t.index())).toList());
                    decided.forEach(table -> analyzed.put(table.index(), table));
                    PaperCompactor.compact(cutout, crop, decided, paper.index(), images, warnings, scope, options.outputMode());
                }
            }
            parent.release(cutouts.get(paper.index()));
        }
        for (Table table : geometry) if (!owners.containsKey(table.index()))
            warnings.add("표 " + table.index() + ": 분리 문서에 속하지 않아 행 제거를 생략했습니다.");
        return geometry.stream().map(t -> analyzed.getOrDefault(t.index(), t)).toList();
    }

    private static Table analyzeTable(Mat binary, Mat grid, Table geometry, int originX, int originY,
                                      ProcessingOptions options, List<String> warnings, Mats parent) {
        Box b = geometry.bounds();
        Rect region = new Rect(b.x() - originX, b.y() - originY, b.width(), b.height());
        List<Integer> ys = geometry.horizontalLines().stream().map(y -> y - b.y()).toList();
        List<Integer> xs = geometry.verticalLines().stream().map(x -> x - b.x()).toList();
        TableLayout layout = TableLayout.detect(grid, region, xs, ys, parent.scale);
        if (layout.kind() == TableLayout.Kind.UNKNOWN)
            warnings.add("표 " + geometry.index() + ": 수탁증·상장의뢰서 양식을 확인하지 못해 행 제거를 생략했습니다.");
        List<Row> rows = classifyRows(binary, grid, region, ys, layout, options, parent).stream()
                .map(r -> new Row(r.index(), r.top() + originY, r.bottom() + originY,
                        r.inkRatio(), r.inkPixels(), r.removed(), r.reason())).toList();
        return new Table(geometry.index(), b, geometry.horizontalLines(), geometry.verticalLines(), rows,
                (int) rows.stream().filter(Row::removed).count());
    }

    private static void clearBorder(Mat mask, int border) {
        Imgproc.rectangle(mask, new Point(0, 0), new Point(mask.cols() - 1, mask.rows() - 1), Scalar.all(0), border * 2);
    }

    private record Correction(Mat image, String method, List<XY> corners, Rotation rotation, boolean reduced) {}

    /** Estimate on a capped probe, then resample the processing image only once. */
    private static Correction correct(Mat image, ProcessingOptions options, List<String> warnings,
                                      SourceCoordinates coordinates, Mats parent, int analysisLimit) {
        int longest = Math.max(image.cols(), image.rows());
        double reduction = Math.min(1, (double) analysisLimit / longest);
        try (Mats m = new Mats(parent.scale.downsampled(longest, analysisLimit))) {
            Mat probe = m.mat();
            Imgproc.resize(image, probe, new Size(Math.round(image.cols() * reduction), Math.round(image.rows() * reduction)),
                    0, 0, Imgproc.INTER_AREA);
            List<double[]> lines = detectLines(probe, m);
            // Retry sparse detections at processing resolution; never enlarge a small source.
            if (lines.size() < 3 && reduction < 1) {
                m.release(probe);
                probe = m.own(image.clone());
                m.scale = parent.scale;
                lines = detectLines(probe, m);
            }
            int initialWidth = probe.cols(), initialHeight = probe.rows();
            SourceCoordinates plan = new SourceCoordinates(initialWidth, initialHeight, initialWidth, initialHeight);
            Orientation orientation = orient(probe, options.rotationDegrees(), warnings, m, lines, initialWidth < image.cols() || initialHeight < image.rows());
            if (probe != orientation.image()) m.release(probe);
            plan.quarterTurn(orientation.quarterTurns(), initialWidth, initialHeight);
            probe = orientation.image();
            Point[] quad = documentQuad(probe, m);
            String method = "FRAME_DESKEW";
            List<XY> corners = new ArrayList<>();
            if (quad != null) {
                List<Double> inverse = plan.inverse();
                for (Point point : quad) {
                    double x = inverse.get(0) * point.x + inverse.get(1) * point.y + inverse.get(2);
                    double y = inverse.get(3) * point.x + inverse.get(4) * point.y + inverse.get(5);
                    // The source-to-processing resize can round the two axes differently.
                    List<Double> original = coordinates.inverse();
                    Point processing = new Point(x * image.cols() / initialWidth, y * image.rows() / initialHeight);
                    corners.add(new XY(original.get(0) * processing.x + original.get(1) * processing.y + original.get(2),
                            original.get(3) * processing.x + original.get(4) * processing.y + original.get(5)));
                }
                Mat warped = warp(probe, quad, m, plan);
                m.release(probe);
                probe = warped;
                method = "DOCUMENT_PERSPECTIVE";
            } else {
                warnings.add("문서 네 모서리를 신뢰할 수 없어 전체 사진을 유지합니다. 겹침·잘림 및 표 기반 보정 여부를 검토하세요.");
            }
            double angle = skewAngle(transformLines(lines, plan.matrix(m)));
            Mat straightened = rotateBound(probe, angle, m, plan);
            if (straightened != probe) m.release(probe);
            probe = straightened;
            if (quad == null) {
                List<double[]> perspectiveLines = reduction < 1 && initialWidth < image.cols()
                        ? transformLines(lines, plan.matrix(m)) : detectLines(probe, m, LinePurpose.PERSPECTIVE);
                Mat corrected = correctGridPerspective(probe, m, plan, perspectiveLines);
                if (corrected != probe) {
                    m.release(probe);
                    probe = corrected;
                    method = "GRID_PERSPECTIVE";
                    warnings.add("문서 외곽 대신 표의 선 방향에서 원근 변환을 추정했습니다. 사진 전체에 동일한 평면을 가정합니다.");
                }
            }
            Rotation rotation = new Rotation(options.rotationDegrees() == -1 ? "AUTO" : "MANUAL",
                    orientation.quarterTurns() * 90, angle, options.rotationDegrees() == -1, orientation.method());
            if (quad == null && angle == 0 && method.equals("FRAME_DESKEW")) {
                Mat result = quarterTurn(image, orientation.quarterTurns(), parent);
                coordinates.quarterTurn(orientation.quarterTurns(), image.cols(), image.rows());
                return new Correction(result, method, List.copyOf(corners), rotation, initialWidth < image.cols() || initialHeight < image.rows());
            }
            double boost = (double) longest / Math.max(initialWidth, initialHeight);
            Mat inputScale = m.own(Mat.eye(3, 3, CvType.CV_64F)), outputScale = m.own(Mat.eye(3, 3, CvType.CV_64F));
            inputScale.put(0, 0, (double) initialWidth / image.cols());
            inputScale.put(1, 1, (double) initialHeight / image.rows());
            outputScale.put(0, 0, boost); outputScale.put(1, 1, boost);
            Mat scaled = m.mat(), transform = m.mat();
            Core.gemm(plan.matrix(m), inputScale, 1, m.mat(), 0, scaled);
            Core.gemm(outputScale, scaled, 1, m.mat(), 0, transform);
            Mat result = parent.mat();
            coordinates.append(transform);
            Imgproc.warpPerspective(image, result, transform,
                    new Size(Math.round(probe.cols() * boost), Math.round(probe.rows() * boost)),
                    Imgproc.INTER_CUBIC, quad == null ? Core.BORDER_CONSTANT : Core.BORDER_REPLICATE, Scalar.all(255));
            return new Correction(result, method, List.copyOf(corners), rotation, initialWidth < image.cols() || initialHeight < image.rows());
        }
    }

    private static List<double[]> detectLines(Mat image, Mats parent) {
        return detectLines(image, parent, LinePurpose.SKEW);
    }

    private enum LinePurpose { SKEW, ORIENTATION, PERSPECTIVE }

    private static List<double[]> detectLines(Mat image, Mats parent, LinePurpose purpose) {
        try (Mats m = new Mats(parent.scale)) {
            Mat gray = m.mat(), edges = m.mat(), lines = m.mat();
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.adaptiveThreshold(gray, edges, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, m.scale.odd(35), 12);
            boolean perspective = purpose == LinePurpose.PERSPECTIVE;
            double theta = Math.PI / switch (purpose) { case SKEW -> 1800; case ORIENTATION -> 180; case PERSPECTIVE -> 3600; };
            Imgproc.HoughLinesP(edges, lines, 1, theta, m.px(perspective ? 90 : 80),
                    (perspective ? image.cols() : Math.min(image.cols(), image.rows())) * .22,
                    m.px(purpose == LinePurpose.ORIENTATION ? 20 : 25));
            List<double[]> result = new ArrayList<>();
            for (int i = 0; i < lines.rows(); i++) result.add(lines.get(i, 0));
            return List.copyOf(result);
        }
    }

    /** Transform segment endpoints instead of repeating Hough detection after every correction. */
    private static List<double[]> transformLines(List<double[]> lines, Mat transform) {
        double[] h = new double[9]; transform.get(0, 0, h);
        List<double[]> result = new ArrayList<>();
        for (double[] segment : lines) {
            double[] mapped = new double[4];
            for (int i = 0; i < 4; i += 2) {
                double x = segment[i], y = segment[i + 1], z = h[6] * x + h[7] * y + h[8];
                mapped[i] = (h[0] * x + h[1] * y + h[2]) / z;
                mapped[i + 1] = (h[3] * x + h[4] * y + h[5]) / z;
            }
            if (Arrays.stream(mapped).allMatch(Double::isFinite)) result.add(mapped);
        }
        return result;
    }

    /** Infer the two families of a planar grid, including when the sheet is clipped. */
    private static Mat correctGridPerspective(Mat image, Mats parent, SourceCoordinates coordinates, List<double[]> lines) {
        try (Mats m = new Mats(parent.scale)) {
            List<double[]> horizontal = new ArrayList<>(), vertical = new ArrayList<>();
            double cx = image.cols() / 2., cy = image.rows() / 2.;
            for (double[] p : lines) {
                double dx = p[2] - p[0], dy = p[3] - p[1];
                if (Math.abs(dx) > image.cols() * .45 && Math.abs(dy / dx) < .10) {
                    double slope = dy / dx;
                    double y = p[1] + slope * (cx - p[0]);
                    if (y > image.rows() * .1 && y < image.rows() * .95) horizontal.add(new double[]{y, slope});
                }
                if (Math.abs(dy) > image.rows() * .18 && Math.abs(dx / dy) < .10) {
                    double slope = dx / dy;
                    double x = p[0] + slope * (cy - p[1]);
                    if (x > image.cols() * .08 && x < image.cols() * .92) vertical.add(new double[]{x, slope});
                }
            }
            double[] h = fitSlope(horizontal, image.rows() * .25, 6);
            double[] v = fitSlope(vertical, image.cols() * .30, 4);
            if (h == null || v == null) return image;
            double ht = h[1], hb = h[0] * (image.rows() - 1) + h[1];
            double vl = v[1], vr = v[0] * (image.cols() - 1) + v[1];
            if (Math.max(Math.max(Math.abs(ht), Math.abs(hb)), Math.max(Math.abs(vl), Math.abs(vr))) > .12) return image;
            if (Math.abs(ht - hb) + Math.abs(vl - vr) + Math.abs(vl + vr) < .002) return image;
            Point[] q = new Point[]{intersection(cx, 0, ht, 0, cy, vl),
                    intersection(cx, 0, ht, image.cols() - 1, cy, vr),
                    intersection(cx, image.rows() - 1, hb, image.cols() - 1, cy, vr),
                    intersection(cx, image.rows() - 1, hb, 0, cy, vl)};
            Mat transform = m.own(Imgproc.getPerspectiveTransform(m.own(new MatOfPoint2f(q)), m.own(new MatOfPoint2f(
                    new Point(0, 0), new Point(image.cols() - 1, 0), new Point(image.cols() - 1, image.rows() - 1), new Point(0, image.rows() - 1)))));
            MatOfPoint2f frame = m.own(new MatOfPoint2f(new Point(0, 0), new Point(image.cols() - 1, 0),
                    new Point(image.cols() - 1, image.rows() - 1), new Point(0, image.rows() - 1)));
            MatOfPoint2f mapped = m.own(new MatOfPoint2f());
            Core.perspectiveTransform(frame, mapped, transform);
            Point[] points = mapped.toArray();
            double minX = Arrays.stream(points).mapToDouble(p -> p.x).min().orElse(0);
            double minY = Arrays.stream(points).mapToDouble(p -> p.y).min().orElse(0);
            double maxX = Arrays.stream(points).mapToDouble(p -> p.x).max().orElse(image.cols());
            double maxY = Arrays.stream(points).mapToDouble(p -> p.y).max().orElse(image.rows());
            if (!Double.isFinite(maxX + maxY + minX + minY) || maxX - minX > image.cols() * 1.5 || maxY - minY > image.rows() * 1.5) return image;
            Mat translation = m.own(Mat.eye(3, 3, CvType.CV_64F)), shifted = m.mat();
            translation.put(0, 2, -minX); translation.put(1, 2, -minY);
            Core.gemm(translation, transform, 1, m.mat(), 0, shifted);
            Mat result = parent.mat();
            coordinates.append(shifted);
            Imgproc.warpPerspective(image, result, shifted, new Size(Math.ceil(maxX - minX + 1), Math.ceil(maxY - minY + 1)),
                    Imgproc.INTER_CUBIC, Core.BORDER_CONSTANT, Scalar.all(255));
            return result;

        }
    }

    private static Point intersection(double hx, double hy, double hs, double vx, double vy, double vs) {
        double y = (hy + hs * (vx - vs * vy - hx)) / (1 - hs * vs);
        return new Point(vx + vs * (y - vy), y);
    }

    private static double[] fitSlope(List<double[]> data, double minimumSpan, int minimumCount) {
        if (data.size() < minimumCount) return null;
        // Theil-Sen fit resists text strokes and duplicate Hough segments.
        List<Double> slopes = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) for (int j = i + 1; j < data.size(); j++) {
            double dx = data.get(j)[0] - data.get(i)[0];
            if (Math.abs(dx) >= minimumSpan) slopes.add((data.get(j)[1] - data.get(i)[1]) / dx);
        }
        if (slopes.isEmpty()) return null;
        Collections.sort(slopes);
        double slope = slopes.get(slopes.size() / 2);
        List<Double> intercepts = data.stream().map(p -> p[1] - slope * p[0]).sorted().toList();
        double intercept = intercepts.get(intercepts.size() / 2);
        long inliers = data.stream().filter(p -> Math.abs(p[1] - (slope * p[0] + intercept)) < .008).count();
        return inliers >= minimumCount && inliers >= data.size() * .65 ? new double[]{slope, intercept} : null;
    }

    private record Orientation(Mat image, int quarterTurns, String method) {}

    private static Orientation orient(Mat image, int requestedDegrees, List<String> warnings, Mats parent, List<double[]> lines, boolean reduced) {
        if (requestedDegrees != -1) return new Orientation(quarterTurn(image, requestedDegrees / 90, parent),
                requestedDegrees / 90, "USER_SPECIFIED");
        try (Mats m = new Mats(parent.scale)) {
            // Use a straightened probe for direction analysis. Source coordinates remain unaffected.
            double angle = skewAngle(lines);
            Mat probe = rotateBound(image, angle, m);
            List<double[]> directions = reduced ? lines : detectLines(probe, m, LinePurpose.ORIENTATION);
            double horizontal = 0, vertical = 0;
            for (double[] p : directions) {
                double radians = Math.toRadians(reduced ? angle : 0), c = Math.cos(radians), sine = Math.sin(radians);
                double dx = Math.abs(c * (p[2] - p[0]) + sine * (p[3] - p[1]));
                double dy = Math.abs(-sine * (p[2] - p[0]) + c * (p[3] - p[1]));
                if (dx > dy * 10) horizontal += dx;
                if (dy > dx * 10) vertical += dy;
            }
            int turns = vertical > horizontal * 1.05 ? 3 : 0;
            Mat aligned = quarterTurn(probe, turns, m);
            double[] scores = headerInkScores(aligned, m);
            boolean flip = scores[1] > scores[0] * 1.4 && scores[1] - scores[0] > .005;
            if (flip) turns = (turns + 2) % 4;
            warnings.add("표 선과 헤더의 인쇄 분포로 시계 방향 " + turns * 90
                    + "도 회전을 추정했습니다. OCR 없이 상하 방향은 확정할 수 없어 검토가 필요합니다. rotationDegrees로 직접 지정할 수 있습니다.");
            return new Orientation(quarterTurn(image, turns, parent), turns,
                    scores[0] + scores[1] > .005 ? "GRID_AND_HEADER_INK" : "GRID_ONLY");
        }
    }

    private static Mat quarterTurn(Mat image, int turns, Mats m) {
        if (turns == 0) return image;
        Mat result = m.mat();
        Core.rotate(image, result, switch (turns) {
            case 1 -> Core.ROTATE_90_CLOCKWISE;
            case 2 -> Core.ROTATE_180;
            case 3 -> Core.ROTATE_90_COUNTERCLOCKWISE;
            default -> throw new IllegalArgumentException("Invalid quarter turn");
        });
        return result;
    }

    private static double[] headerInkScores(Mat image, Mats parent) {
        try (Mats m = new Mats(parent.scale)) {
            Mat gray = m.mat(), binary = m.mat(), horizontal = m.mat(), vertical = m.mat(), grid = m.mat(), ink = m.mat();
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.adaptiveThreshold(gray, binary, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, m.scale.odd(35), 12);
            Imgproc.morphologyEx(binary, horizontal, Imgproc.MORPH_OPEN, m.lineKernel(Math.max(m.px(30), image.cols() / 22), 1));
            Imgproc.morphologyEx(binary, vertical, Imgproc.MORPH_OPEN, m.lineKernel(1, Math.max(m.px(25), image.rows() / 45)));
            clearBorder(horizontal, m.px(12)); clearBorder(vertical, m.px(12));
            Core.bitwise_or(horizontal, vertical, grid);
            Imgproc.dilate(grid, grid, m.kernel(5, 5));
            // A fixed darkness threshold counts a hand's shadow as dense bottom ink.
            // Compare locally thresholded strokes instead of absolute paper brightness.
            Core.subtract(binary, grid, ink);
            Mat hsv = m.mat(), blue = m.mat();
            Imgproc.cvtColor(image, hsv, Imgproc.COLOR_BGR2HSV);
            Core.inRange(hsv, new Scalar(90, 20, 30), new Scalar(140, 255, 255), blue);
            // Handwritten account numbers in the footer are not a printed header.
            Mat headingInk = m.mat();
            Core.subtract(ink, blue, headingInk);
            double top = 0, bottom = 0;
            int count = 0;
            List<Rect> regions = tableRegions(horizontal, image.size(), m);
            double largest = regions.stream().mapToDouble(r -> r.area()).max().orElse(0);
            Rect main = regions.stream().max(Comparator.comparingDouble(r -> r.area())).orElse(null);
            if (main != null) {
                double[] heading = externalHeadingScores(headingInk, main, m);
                // A visible upright title vetoes a flip caused by dense footer rows.
                // Footer text alone cannot introduce an otherwise unsupported inversion.
                if (heading[0] > image.total() * .0003 && heading[0] > heading[1] * 1.8)
                    return new double[]{1, 0};
            }
            for (Rect region : regions) {
                // Small footer grids must not outweigh the main table header.
                if (region.area() < largest * .6) continue;
                Mat tolerant = m.mat();
                Imgproc.dilate(m.own(horizontal.submat(region)), tolerant, m.kernel(1, 9));
                List<Integer> ys = projections(tolerant, true, .50);
                if (ys.size() < 5) continue;
                int width = Math.max(1, region.width - 2 * m.px(6));
                int topA = region.y + ys.get(0) + m.px(5), topB = region.y + ys.get(2) - m.px(5);
                int bottomA = region.y + ys.get(ys.size() - 3) + m.px(5), bottomB = region.y + ys.getLast() - m.px(5);
                if (topB <= topA || bottomB <= bottomA) continue;
                Mat topInk = m.own(ink.submat(new Rect(region.x + m.px(6), topA, width, topB - topA)));
                Mat bottomInk = m.own(ink.submat(new Rect(region.x + m.px(6), bottomA, width, bottomB - bottomA)));
                top += Core.countNonZero(topInk) / (double) topInk.total();
                bottom += Core.countNonZero(bottomInk) / (double) bottomInk.total();
                count++;
            }
            return count == 0 ? new double[]{0, 0} : new double[]{top / count, bottom / count};

        }
    }

    private static double[] externalHeadingScores(Mat ink, Rect table, Mats m) {
        Mat labels = m.mat(), stats = m.mat(), centers = m.mat();
        int count = Imgproc.connectedComponentsWithStats(ink, labels, stats, centers);
        double[] scores = new double[2];
        for (int i = 1; i < count; i++) {
            int x = (int) stats.get(i, Imgproc.CC_STAT_LEFT)[0], y = (int) stats.get(i, Imgproc.CC_STAT_TOP)[0];
            int width = (int) stats.get(i, Imgproc.CC_STAT_WIDTH)[0], height = (int) stats.get(i, Imgproc.CC_STAT_HEIGHT)[0];
            if (height < ink.rows() * .008 || height > ink.rows() * .06 || width > ink.cols() * .15
                    || x + width < table.x || x > table.x + table.width) continue;
            double area = stats.get(i, Imgproc.CC_STAT_AREA)[0];
            if (y + height < table.y) scores[0] += area;
            else if (y > table.y + table.height) scores[1] += area;
        }
        return scores;
    }

    private static Point[] documentQuad(Mat image, Mats parent) {
        try (Mats m = new Mats(parent.scale)) {
            Mat gray = m.mat(), blur = m.mat(), edges = m.mat();
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(gray, blur, m.window(7), 0);
            Imgproc.Canny(blur, edges, 35, 100);
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, m.kernel(7, 7));
            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(edges, contours, m.mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
            Point[] best = null;
            double bestArea = image.total() * .45;
            for (MatOfPoint contour : contours) {
                m.own(contour);
                double area = Math.abs(Imgproc.contourArea(contour));
                if (area < bestArea) continue;
                MatOfPoint2f curve = m.own(new MatOfPoint2f(contour.toArray())), approx = m.own(new MatOfPoint2f());
                Imgproc.approxPolyDP(curve, approx, Imgproc.arcLength(curve, true) * .02, true);
                if (approx.total() != 4) continue;
                Point[] points = approx.toArray();
                MatOfPoint polygon = m.own(new MatOfPoint(points));
                if (!Imgproc.isContourConvex(polygon)) continue;
                // A contour touching the frame may be the photo boundary or a clipped table.
                boolean touches = Arrays.stream(points).anyMatch(p -> p.x < m.px(5) || p.y < m.px(5) || p.x > image.cols() - m.px(6) || p.y > image.rows() - m.px(6));
                if (touches) continue;
                if (!hasPaperContrast(image, points, m)) continue;
                Point[] ordered = order(points, m.px(80));
                if (ordered == null) continue;
                best = ordered;
                bestArea = area;
            }
            return best;

        }
    }

    private static boolean hasPaperContrast(Mat image, Point[] points, Mats parent) {
        try (Mats m = new Mats(parent.scale)) {
            Mat mask = m.zeros(image.rows(), image.cols(), CvType.CV_8UC1);
            Imgproc.fillConvexPoly(mask, m.own(new MatOfPoint(points)), Scalar.all(255));
            Mat innerNear = m.mat(), innerFar = m.mat(), outerNear = m.mat(), outerFar = m.mat();
            Imgproc.erode(mask, innerNear, m.kernel(17, 17));
            Imgproc.erode(mask, innerFar, m.kernel(35, 35));
            Imgproc.dilate(mask, outerNear, m.kernel(17, 17));
            Imgproc.dilate(mask, outerFar, m.kernel(35, 35));
            Core.subtract(innerNear, innerFar, innerNear);
            Core.subtract(outerFar, outerNear, outerFar);
            // Thin table strokes can dominate a small ring after downsampling. Median color
            // prevents mistaking a printed table border on white paper for the paper outline.
            double[] inside = medianColor(image, innerNear), outside = medianColor(image, outerFar);
            double contrast = 0;
            for (int i = 0; i < 3; i++) contrast += Math.pow(inside[i] - outside[i], 2);
            if (Math.sqrt(contrast) <= 20) return false;
            // Paper must end at individual edges too; a table may have dark surroundings
            // on its sides while the actual header continues above its top border.
            Point center = new Point(Arrays.stream(points).mapToDouble(p -> p.x).average().orElseThrow(),
                    Arrays.stream(points).mapToDouble(p -> p.y).average().orElseThrow());
            int supported = 0;
            for (int edge = 0; edge < points.length; edge++) {
                Point a = points[edge], b = points[(edge + 1) % points.length];
                double dx = b.x - a.x, dy = b.y - a.y, length = Math.hypot(dx, dy);
                if (length == 0) continue;
                double nx = -dy / length, ny = dx / length;
                if (nx * (center.x - (a.x + b.x) / 2) + ny * (center.y - (a.y + b.y) / 2) < 0) {
                    nx = -nx; ny = -ny;
                }
                List<Double> differences = new ArrayList<>();
                for (int sample = 1; sample <= 9; sample++) {
                    double x = a.x + dx * sample / 10., y = a.y + dy * sample / 10.;
                    int ix = (int) Math.round(x + nx * m.px(14)), iy = (int) Math.round(y + ny * m.px(14));
                    int ox = (int) Math.round(x - nx * m.px(14)), oy = (int) Math.round(y - ny * m.px(14));
                    if (ix < 0 || iy < 0 || ox < 0 || oy < 0 || ix >= image.cols() || ox >= image.cols()
                            || iy >= image.rows() || oy >= image.rows()) continue;
                    double[] ic = image.get(iy, ix), oc = image.get(oy, ox);
                    double distance = 0;
                    for (int c = 0; c < 3; c++) distance += Math.pow(ic[c] - oc[c], 2);
                    differences.add(Math.sqrt(distance));
                }
                differences.sort(Double::compare);
                if (differences.size() >= 5 && differences.get(differences.size() / 2) > 25) supported++;
            }
            return supported == 4;

        }
    }

    private static double[] medianColor(Mat image, Mat mask) {
        int[][] histogram = new int[3][256];
        byte[] pixels = new byte[image.cols() * 3], selected = new byte[image.cols()];
        int count = 0;
        for (int y = 0; y < image.rows(); y++) {
            image.get(y, 0, pixels); mask.get(y, 0, selected);
            for (int x = 0; x < image.cols(); x++) if (selected[x] != 0) {
                count++;
                for (int c = 0; c < 3; c++) histogram[c][pixels[x * 3 + c] & 255]++;
            }
        }
        double[] result = new double[3];
        for (int c = 0; c < 3; c++) {
            int cumulative = 0;
            for (int value = 0; value < 256; value++) {
                cumulative += histogram[c][value];
                if (cumulative > count / 2) { result[c] = value; break; }
            }
        }
        return result;
    }

    private static Point[] order(Point[] points, int minimumSide) {
        Point center = new Point(Arrays.stream(points).mapToDouble(p -> p.x).average().orElseThrow(),
                Arrays.stream(points).mapToDouble(p -> p.y).average().orElseThrow());
        Point[] sorted = points.clone();
        Arrays.sort(sorted, Comparator.comparingDouble(p -> Math.atan2(p.y - center.y, p.x - center.x)));
        int start = 0;
        for (int i = 1; i < 4; i++) if (sorted[i].x + sorted[i].y < sorted[start].x + sorted[start].y) start = i;
        Point[] result = new Point[4];
        for (int i = 0; i < 4; i++) result[i] = sorted[(start + i) % 4];
        for (int i = 0; i < 4; i++) if (distance(result[i], result[(i + 1) % 4]) < minimumSide) return null;
        return result;
    }

    private static double distance(Point a, Point b) { return Math.hypot(a.x - b.x, a.y - b.y); }

    private static Mat warp(Mat src, Point[] q, Mats parent, SourceCoordinates coordinates) {
        try (Mats m = new Mats(parent.scale)) {
            int width = (int) Math.round(Math.max(distance(q[0], q[1]), distance(q[3], q[2])));
            int height = (int) Math.round(Math.max(distance(q[0], q[3]), distance(q[1], q[2])));
            Mat transform = m.own(Imgproc.getPerspectiveTransform(m.own(new MatOfPoint2f(q)),
                    m.own(new MatOfPoint2f(new Point(0, 0), new Point(width - 1, 0), new Point(width - 1, height - 1), new Point(0, height - 1)))));
            Mat dst = parent.mat();
            coordinates.append(transform);
            Imgproc.warpPerspective(src, dst, transform, new Size(width, height), Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE);
            return dst;

        }
    }

    private static double skewAngle(List<double[]> lines) {
        List<double[]> angles = new ArrayList<>();
        double[] histogram = new double[90];
        double total = 0;
        for (double[] p : lines) {
            double angle = Math.toDegrees(Math.atan2(p[3] - p[1], p[2] - p[0]));
            angle = ((angle + 225) % 90) - 45;
            double weight = Math.hypot(p[2] - p[0], p[3] - p[1]);
            angles.add(new double[]{angle, weight});
            histogram[Math.min(89, (int) Math.floor(angle + 45))] += weight;
            total += weight;
        }
        if (angles.size() < 3) return 0;
        int peak = 0;
        double best = 0;
        for (int i = 0; i < 90; i++) {
            double weight = 0;
            for (int j = -2; j <= 2; j++) weight += histogram[(i + j + 90) % 90];
            if (weight > best) { best = weight; peak = i; }
        }
        if (best < total * .4) return 0;
        double center = peak - 44.5;
        List<double[]> inliers = new ArrayList<>();
        for (double[] sample : angles) {
            double delta = sample[0] - center;
            if (delta > 45) delta -= 90;
            if (delta < -45) delta += 90;
            if (Math.abs(delta) <= 2.5) inliers.add(new double[]{center + delta, sample[1]});
        }
        if (inliers.size() < 3) return 0;
        inliers.sort(Comparator.comparingDouble(p -> p[0]));
        double half = inliers.stream().mapToDouble(p -> p[1]).sum() / 2, sum = 0;
        for (double[] sample : inliers) {
            sum += sample[1];
            if (sum >= half) return Math.abs(sample[0]) < .08 ? 0 : sample[0];
        }
        return 0;

    }

    private static Mat rotateBound(Mat image, double angle, Mats parent) {
        return rotateBound(image, angle, parent, null);
    }

    private static Mat rotateBound(Mat image, double angle, Mats parent, SourceCoordinates coordinates) {
        try (Mats m = new Mats(parent.scale)) {
            if (angle == 0) return image;
            Mat rotation = m.own(Imgproc.getRotationMatrix2D(new Point(image.cols() / 2., image.rows() / 2.), angle, 1));
            double c = Math.abs(rotation.get(0, 0)[0]), s = Math.abs(rotation.get(0, 1)[0]);
            int width = (int) Math.ceil(image.rows() * s + image.cols() * c);
            int height = (int) Math.ceil(image.rows() * c + image.cols() * s);
            rotation.put(0, 2, rotation.get(0, 2)[0] + (width - image.cols()) / 2.);
            rotation.put(1, 2, rotation.get(1, 2)[0] + (height - image.rows()) / 2.);
            Mat dst = parent.mat();
            if (coordinates != null) coordinates.append(rotation);
            Imgproc.warpAffine(image, dst, rotation, new Size(width, height), Imgproc.INTER_CUBIC, Core.BORDER_CONSTANT, Scalar.all(255));
            return dst;

        }
    }

    private static List<Rect> tableRegions(Mat mask, Size size, Mats parent) {
        try (Mats m = new Mats(parent.scale)) {
            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(m.own(mask.clone()), contours, m.mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            List<Rect> segments = new ArrayList<>();
            for (MatOfPoint contour : contours) {
                m.own(contour);
                Rect r = Imgproc.boundingRect(contour);
                if (r.width > size.width * .30 && r.height < size.height * .04
                        && r.y > size.height * .035 && r.y + r.height < size.height * .985) segments.add(r);
            }
            segments.sort(Comparator.comparingInt(r -> r.y));
            List<List<Rect>> groups = new ArrayList<>();
            for (Rect r : segments) {
                List<Rect> match = null;
                for (List<Rect> group : groups) {
                    Rect last = group.getLast();
                    if (r.y - last.y < size.height * .085 && Math.abs(r.x - last.x) < size.width * .055
                            && Math.abs(r.x + r.width - last.x - last.width) < size.width * .055) {
                        match = group; break;
                    }
                }
                if (match == null) { match = new ArrayList<>(); groups.add(match); }
                match.add(r);
            }
            List<Rect> result = new ArrayList<>();
            for (List<Rect> group : groups) {
                if (group.size() < 3) continue;
                int left = group.stream().mapToInt(r -> r.x).min().orElseThrow();
                int right = group.stream().mapToInt(r -> r.x + r.width).max().orElseThrow();
                int top = group.getFirst().y, bottom = group.getLast().y + group.getLast().height;
                if (bottom - top < size.height * .045) continue;
                result.add(new Rect(left, top, right - left, bottom - top));
            }
            result.sort(Comparator.comparingInt(r -> r.y));
            return result;

        }
    }

    private static List<Integer> projections(Mat mask, boolean horizontal, double minimumCoverage) {
        int length = horizontal ? mask.rows() : mask.cols(), cross = horizontal ? mask.cols() : mask.rows();
        byte[] pixels = new byte[(int) mask.total()];
        mask.get(0, 0, pixels);
        List<Integer> peaks = new ArrayList<>();
        int start = -1, weight = 0, weighted = 0;
        for (int i = 0; i <= length; i++) {
            int count = 0;
            if (i < length) for (int j = 0; j < cross; j++) {
                int index = horizontal ? i * mask.cols() + j : j * mask.cols() + i;
                if (pixels[index] != 0) count++;
            }
            if (count >= cross * minimumCoverage) {
                if (start < 0) start = i;
                weight += count; weighted += i * count;
            } else if (start >= 0) {
                peaks.add((int) Math.round((double) weighted / weight));
                start = -1; weight = 0; weighted = 0;
            }
        }
        return peaks;
    }

    private static List<Row> classifyRows(Mat binary, Mat grid, Rect region,
                                           List<Integer> ys, TableLayout layout, ProcessingOptions options, Mats parent) {
        try (Mats m = new Mats(parent.scale)) {
            Mat expandedGrid = m.mat(), content = m.mat();
            Imgproc.dilate(grid, expandedGrid, m.kernel(5, 5));
            Core.subtract(binary, expandedGrid, content);
            int protectedRows = Math.max(options.protectedTopRows(), layout.headerRows());
            List<Row> result = new ArrayList<>();
            for (int i = 0; i < ys.size() - 1; i++) {
                int height = ys.get(i + 1) - ys.get(i);
                int inset = Math.min(m.px(5), Math.max(m.px(2), height / 8));
                Rect r = new Rect(region.x + m.px(4), region.y + ys.get(i) + inset,
                        Math.max(1, region.width - 2 * m.px(4)), Math.max(1, height - 2 * inset));
                int ink = Core.countNonZero(m.own(content.submat(r)));
                int area = r.width * r.height;
                if (layout.kind() == TableLayout.Kind.CONSIGNMENT_RECEIPT) {
                    int left = Math.max(r.x, region.x + layout.printedLeft() - m.px(3));
                    int right = Math.min(r.x + r.width, region.x + layout.printedRight() + m.px(3));
                    Rect printed = new Rect(left, r.y, right - left, r.height);
                    ink -= Core.countNonZero(m.own(content.submat(printed)));
                    area -= printed.width * printed.height;
                }
                double ratio = ink / (double) area;
                boolean unknown = layout.kind() == TableLayout.Kind.UNKNOWN;
                boolean protectedRow = i < protectedRows;
                boolean empty = !unknown && !protectedRow && ratio <= options.minimumInkRatio();
                result.add(new Row(i, region.y + ys.get(i), region.y + ys.get(i + 1), ratio, ink,
                        false, unknown ? "UNRECOGNIZED_LAYOUT" : protectedRow ? "PROTECTED_HEADER"
                                : empty ? BlankRowCutoff.EMPTY : "CONTENT_PRESENT"));
            }
            return List.copyOf(result);
        }
    }

    private static byte[] png(Mat image, Mats m) {
        MatOfByte bytes = new MatOfByte();
        try {
            if (!Imgcodecs.imencode(".png", image, bytes)) throw new IllegalStateException("PNG encode failed");
            return bytes.toArray();
        } finally { bytes.release(); }
    }
}
