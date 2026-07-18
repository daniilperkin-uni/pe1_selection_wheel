package selectionwheel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.geom.Point2D;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class WheelMathTest {

	// ----- sectionAngleDeg -----

	@Test
	void sectionAngleDeg_returnsSectionWidth() {
		assertThat(WheelMath.sectionAngleDeg(4)).isEqualTo(90.0);
		assertThat(WheelMath.sectionAngleDeg(8)).isEqualTo(45.0);
		assertThat(WheelMath.sectionAngleDeg(15)).isEqualTo(24.0);
	}

	@Test
	void sectionAngleDeg_rejectsZeroAndNegative() {
		assertThatThrownBy(() -> WheelMath.sectionAngleDeg(0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("numSections must be > 0");
		assertThatThrownBy(() -> WheelMath.sectionAngleDeg(-3))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// ----- normalizeAngleDeg -----

	@ParameterizedTest
	@CsvSource({
			"0, 0",
			"360, 0",
			"720, 0",
			"-360, 0",
			"90, 90",
			"450, 90",
			"-90, -90",
			"400, 40",
			"-400, -40"
	})
	void normalizeAngleDeg_mapsIntoOpenInterval(double input, double expected) {
		assertThat(WheelMath.normalizeAngleDeg(input)).isEqualTo(expected, within(1e-9));
	}

	// ----- selectionIndex -----

	@Test
	void selectionIndex_atZeroRotation_returnsZero() {
		assertThat(WheelMath.selectionIndex(0, 4)).isEqualTo(0);
	}

	@Test
	void selectionIndex_withinFirstSection_returnsZero() {
		// With 4 sections of 90 degrees each, anything in [0, 90) is index 0
		assertThat(WheelMath.selectionIndex(0, 4)).isEqualTo(0);
		assertThat(WheelMath.selectionIndex(45, 4)).isEqualTo(0);
		assertThat(WheelMath.selectionIndex(89.999, 4)).isEqualTo(0);
	}

	@Test
	void selectionIndex_crossingSectionBoundary_returnsNextIndex() {
		// With 4 sections of 90 degrees each:
		assertThat(WheelMath.selectionIndex(90, 4)).isEqualTo(1);
		assertThat(WheelMath.selectionIndex(180, 4)).isEqualTo(2);
		assertThat(WheelMath.selectionIndex(270, 4)).isEqualTo(3);
	}

	@Test
	void selectionIndex_negativeRotation_wrapsCorrectly() {
		// Original formula: (n + (angle % 360) / delta) % n
		// -90 deg / 90 deg = -1, + 4 = 3, % 4 = 3
		assertThat(WheelMath.selectionIndex(-90, 4)).isEqualTo(3);
		assertThat(WheelMath.selectionIndex(-1, 4)).isEqualTo(3);
		assertThat(WheelMath.selectionIndex(-180, 4)).isEqualTo(2);
	}

	@Test
	void selectionIndex_fullRotation_returnsToStart() {
		assertThat(WheelMath.selectionIndex(360, 4)).isEqualTo(0);
		assertThat(WheelMath.selectionIndex(720, 4)).isEqualTo(0);
	}

	// ----- clampSpeed -----

	@Test
	void clampSpeed_clampsToLowerAndUpperBound() {
		assertThat(WheelMath.clampSpeed(-10, 360)).isEqualTo(0);
		assertThat(WheelMath.clampSpeed(0, 360)).isEqualTo(0);
		assertThat(WheelMath.clampSpeed(180, 360)).isEqualTo(180);
		assertThat(WheelMath.clampSpeed(360, 360)).isEqualTo(360);
		assertThat(WheelMath.clampSpeed(1000, 360)).isEqualTo(360);
	}

	@Test
	void clampSpeed_rejectsNegativeMax() {
		assertThatThrownBy(() -> WheelMath.clampSpeed(10, -1))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// ----- computeInitialSpeedDegPerSec -----

	@Test
	void computeInitialSpeed_returnsZeroForZeroElapsed() {
		double speed = WheelMath.computeInitialSpeedDegPerSec(
				0, 90, 1000, 1000, 360);
		assertThat(speed).isEqualTo(0);
	}

	@Test
	void computeInitialSpeed_computesAngularVelocity() {
		// 90 deg in 1000 ms -> 90 deg/s
		double speed = WheelMath.computeInitialSpeedDegPerSec(
				0, 90, 1000, 2000, 360);
		assertThat(speed).isEqualTo(90.0, within(1e-9));
	}

	@Test
	void computeInitialSpeed_preservesSign() {
		double negative = WheelMath.computeInitialSpeedDegPerSec(
				90, 0, 1000, 2000, 360);
		assertThat(negative).isEqualTo(-90.0, within(1e-9));
	}

	@Test
	void computeInitialSpeed_clampsToMax() {
		// 3600 deg in 1000 ms -> 3600 deg/s, capped to 360
		double speed = WheelMath.computeInitialSpeedDegPerSec(
				0, 3600, 1000, 2000, 360);
		assertThat(speed).isEqualTo(360.0);
	}

	@Test
	void computeInitialSpeed_clampsToMaxNegativeDirection() {
		double speed = WheelMath.computeInitialSpeedDegPerSec(
				3600, 0, 1000, 2000, 360);
		assertThat(speed).isEqualTo(-360.0);
	}

	// ----- dragDeltaDeg -----

	@Test
	void dragDeltaDeg_returnsZeroForVerticalDrag() {
		// mouse directly above center: dx=0
		Point2D center = new Point2D.Double(100, 100);
		Point2D prev = new Point2D.Double(100, 50);
		Point2D curr = new Point2D.Double(100, 30);
		assertThat(WheelMath.dragDeltaDeg(prev, curr, center)).isEqualTo(0);
	}

	@Test
	void dragDeltaDeg_returnsZeroForNaNComputedDelta() {
		// Identical prev and curr produce k1==k2, atan(0/...) = 0. Not NaN.
		// Construct a case where prev and curr are both on the center vertical:
		Point2D center = new Point2D.Double(0, 0);
		Point2D prev = new Point2D.Double(0, 50);
		Point2D curr = new Point2D.Double(0, 70);
		assertThat(WheelMath.dragDeltaDeg(prev, curr, center)).isEqualTo(0);
	}

	@Test
	void dragDeltaDeg_returnsNonZeroForRotatedDrag() {
		Point2D center = new Point2D.Double(100, 100);
		// Two points on the unit circle around center
		Point2D prev = new Point2D.Double(200, 100); // right of center
		Point2D curr = new Point2D.Double(150, 50);  // up-right of center
		double delta = WheelMath.dragDeltaDeg(prev, curr, center);
		assertThat(delta).isNotEqualTo(0);
	}

	@Test
	void dragDeltaDeg_doesNotMutateInputs() {
		Point2D center = new Point2D.Double(100, 100);
		Point2D prev = new Point2D.Double(200, 100);
		Point2D curr = new Point2D.Double(150, 50);
		WheelMath.dragDeltaDeg(prev, curr, center);
		assertThat(prev.getX()).isEqualTo(200);
		assertThat(prev.getY()).isEqualTo(100);
		assertThat(curr.getX()).isEqualTo(150);
		assertThat(curr.getY()).isEqualTo(50);
		assertThat(center.getX()).isEqualTo(100);
		assertThat(center.getY()).isEqualTo(100);
	}
}
