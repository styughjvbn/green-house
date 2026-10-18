package kr.co.slip.opencv;

import java.util.List;
import kr.co.slip.api.ProcessingResult.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceCoordinatesTest {
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void inversesResizeRotationAffineAndPerspectiveWithoutLosingSourceCoordinates(int turns) {
        var coordinates = new SourceCoordinates(2000, 1000, 1000, 500);
        coordinates.quarterTurn(turns, 1000, 500);
        double[] affine = {1,.03,12,-.02,1,19,0,0,1};
        double[] perspective = {1,0,7,0,1,11,.0001,.0002,1};
        coordinates.append(affine);
        coordinates.append(perspective);
        double[] p = {640*.5, 380*.5};
        p = switch (turns) {
            case 1 -> new double[]{499-p[1],p[0]};
            case 2 -> new double[]{999-p[0],499-p[1]};
            case 3 -> new double[]{p[1],999-p[0]};
            default -> p;
        };
        p = apply(perspective, apply(affine, p));
        double[] restored = apply(coordinates.inverse().stream().mapToDouble(Double::doubleValue).toArray(), p);
        assertArrayEquals(new double[]{640,380}, restored, 1e-8);
    }

    @Test void sourceBoundsClipCorrectedPaddingAndIncludeAllCorners() {
        var coordinates = new SourceCoordinates(400, 500, 200, 250);
        var report = new Report("TEST",400,500,200,250,List.of(),List.of(),List.of(),null,List.of(),coordinates.inverse());
        assertEquals(new Box(20,40,100,160), report.sourceBounds(new Box(10,20,50,80)));
        assertEquals(new Box(0,0,400,500), report.sourceBounds(new Box(-10,-10,240,300)));
    }

    private static double[] apply(double[] m, double[] p) {
        double w = m[6]*p[0]+m[7]*p[1]+m[8];
        return new double[]{(m[0]*p[0]+m[1]*p[1]+m[2])/w,(m[3]*p[0]+m[4]*p[1]+m[5])/w};
    }
}
