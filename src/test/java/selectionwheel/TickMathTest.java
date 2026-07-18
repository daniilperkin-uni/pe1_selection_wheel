package selectionwheel;

import org.junit.jupiter.api.Test;

import java.awt.Polygon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class TickMathTest {

	// ----- computeDefaultTriangle -----

	@Test
	void computeDefaultTriangle_returnsThreePointPolygon() {
		Polygon p = TickMath.computeDefaultTriangle(20, 100);
		assertThat(p.npoints).isEqualTo(3);
	}

	@Test
	void computeDefaultTriangle_apexPointsLeft_centerIsApexY() {
		Polygon p = TickMath.computeDefaultTriangle(20, 100);
		// Apex at (0, height/2)
		assertThat(p.xpoints[0]).isEqualTo(0);
		assertThat(p.ypoints[0]).isEqualTo(50);
	}

	@Test
	void computeDefaultTriangle_basePointsAtRightEdge() {
		Polygon p = TickMath.computeDefaultTriangle(20, 100);
		// Base at x = width = 20, with offsets of ±width*tan(30).
		// Note: the source casts to int AFTER the subtraction, so we
		// compute the expected values the same way (not by casting the
		// offset separately, which would give a different result due to
		// truncation order).
		assertThat(p.xpoints[1]).isEqualTo(20);
		assertThat(p.xpoints[2]).isEqualTo(20);
		int expectedY1 = (int) (50 - 20 * Math.tan(Math.toRadians(30)));
		int expectedY2 = (int) (50 + 20 * Math.tan(Math.toRadians(30)));
		assertThat(p.ypoints[1]).isEqualTo(expectedY1);
		assertThat(p.ypoints[2]).isEqualTo(expectedY2);
	}

	// ----- adjustPolygon: idempotency / non-mutation -----

	@Test
	void adjustPolygon_doesNotMutateInput() {
		Polygon source = new Polygon(new int[]{0, 100, 50}, new int[]{0, 0, 100}, 3);
		int[] xsBefore = source.xpoints.clone();
		int[] ysBefore = source.ypoints.clone();

		TickMath.adjustPolygon(source, 200, 200);

		assertThat(source.xpoints).containsExactly(xsBefore);
		assertThat(source.ypoints).containsExactly(ysBefore);
	}

	@Test
	void adjustPolygon_calledTwice_isIdempotent() {
		// This is the regression test for the original Tick.adjustPolygon bug,
		// which multiplied the scaling factor on each call.
		Polygon source = new Polygon(new int[]{0, 100, 50}, new int[]{0, 0, 100}, 3);
		Polygon first = TickMath.adjustPolygon(source, 200, 200);
		Polygon second = TickMath.adjustPolygon(source, 200, 200);
		assertThat(second.xpoints).containsExactly(first.xpoints);
		assertThat(second.ypoints).containsExactly(first.ypoints);
	}

	@Test
	void adjustPolygon_calledWithResult_isAlsoIdempotent() {
		// Even if we feed our own output back in (which the original Tick did
		// because it stored its scaled result back into the source field),
		// scaling must remain stable.
		Polygon source = new Polygon(new int[]{0, 100, 50}, new int[]{0, 0, 100}, 3);
		Polygon first = TickMath.adjustPolygon(source, 200, 200);
		Polygon second = TickMath.adjustPolygon(first, 200, 200);
		// Same width -> same scale factor (1.0) -> same points
		assertThat(second.xpoints).containsExactly(first.xpoints);
	}

	// ----- adjustPolygon: scaling -----

	@Test
	void adjustPolygon_scalesPolygonToPanelWidth() {
		// Polygon spans [0, 100] in x. Scaling to panel width 200 doubles x.
		Polygon source = new Polygon(new int[]{0, 100, 50}, new int[]{0, 0, 100}, 3);
		Polygon scaled = TickMath.adjustPolygon(source, 200, 200);
		int widthAfter = max(scaled.xpoints) - min(scaled.xpoints);
		assertThat(widthAfter).isEqualTo(200);
	}

	// ----- adjustPolygon: centering -----

	@Test
	void adjustPolygon_centersPolygonAtPanelCenter() {
		// NOTE: the original Tick.adjustPolygon (faithfully preserved here)
		// computes the polygon centroid using integer division
		// (centerY /= n), which introduces a small rounding error. For the
		// input (0,0),(100,0),(50,100) scaled to a 200x200 panel, the
		// centroid Y lands at 100.667 instead of exactly 100. This test
		// asserts the actual (rounded) behavior; a future fix would
		// compute the centroid in floating point.
		Polygon source = new Polygon(new int[]{0, 100, 50}, new int[]{0, 0, 100}, 3);
		Polygon scaled = TickMath.adjustPolygon(source, 200, 200);
		double cx = (scaled.xpoints[0] + scaled.xpoints[1] + scaled.xpoints[2]) / 3.0;
		double cy = (scaled.ypoints[0] + scaled.ypoints[1] + scaled.ypoints[2]) / 3.0;
		assertThat(cx).isEqualTo(100.0, within(1e-9));
		// Integer-division rounding in centroid computation -> ~100.667
		assertThat(cy).isEqualTo(100.0 + 2.0 / 3.0, within(1e-9));
	}

	// ----- adjustPolygon: argument validation -----

	@Test
	void adjustPolygon_rejectsNullSource() {
		assertThatThrownBy(() -> TickMath.adjustPolygon(null, 100, 100))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("source must not be null");
	}

	@Test
	void adjustPolygon_rejectsEmptyPolygon() {
		Polygon empty = new Polygon();
		assertThatThrownBy(() -> TickMath.adjustPolygon(empty, 100, 100))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("at least one point");
	}

	@Test
	void adjustPolygon_rejectsNonPositivePanelWidth() {
		Polygon source = new Polygon(new int[]{0, 100, 50}, new int[]{0, 0, 100}, 3);
		assertThatThrownBy(() -> TickMath.adjustPolygon(source, 0, 100))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("panelWidth must be > 0");
		assertThatThrownBy(() -> TickMath.adjustPolygon(source, -10, 100))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private static int max(int[] arr) {
		int m = Integer.MIN_VALUE;
		for (int v : arr) if (v > m) m = v;
		return m;
	}

	private static int min(int[] arr) {
		int m = Integer.MAX_VALUE;
		for (int v : arr) if (v < m) m = v;
		return m;
	}
}
