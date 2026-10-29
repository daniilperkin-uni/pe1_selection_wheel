package selectionwheel;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class WheelModelTest {

	// ----- construction -----

	@Test
	void constructor_acceptsNonEmptyListUnderLimit() {
		List<String> items = List.of("Cat", "Dog", "Rabbit");
		WheelModel model = new WheelModel(items);
		assertThat(model.getItems()).containsExactly("Cat", "Dog", "Rabbit");
		assertThat(model.getNumSections()).isEqualTo(3);
		assertThat(model.getSectionAngleDeg()).isEqualTo(120.0);
	}

	@Test
	void constructor_defensiveCopy_doesNotLeakCallerMutation() {
		List<String> mutable = new java.util.ArrayList<>(List.of("A", "B"));
		WheelModel model = new WheelModel(mutable);
		mutable.add("C");
		assertThat(model.getItems()).containsExactly("A", "B");
		assertThat(model.getItems()).hasSize(2);
	}

	@Test
	void constructor_rejectsNullList() {
		assertThatThrownBy(() -> new WheelModel(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("items must not be null");
	}

	@Test
	void constructor_rejectsEmptyList() {
		assertThatThrownBy(() -> new WheelModel(Collections.emptyList()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("items must not be empty");
	}

	@Test
	void constructor_rejectsListOverLimit() {
		String[] big = new String[WheelModel.ITEM_LIMIT + 1];
		Arrays.fill(big, "x");
		assertThatThrownBy(() -> new WheelModel(Arrays.asList(big)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("exceeds limit");
	}

	// ----- setItems -----

	@Test
	void setItems_replacesListAndResetsRotation() {
		WheelModel model = new WheelModel(List.of("A", "B", "C"));
		model.setRotationAngleDeg(180);
		model.setItems(List.of("X", "Y"));
		assertThat(model.getItems()).containsExactly("X", "Y");
		assertThat(model.getNumSections()).isEqualTo(2);
		assertThat(model.getRotationAngleDeg()).isEqualTo(0);
	}

	@Test
	void setItems_stopsInFlightSpin() {
		WheelModel model = new WheelModel(List.of("A", "B"));
		model.startSpin(360, 1, -20);
		assertThat(model.isSpinning()).isTrue();
		model.setItems(List.of("X", "Y"));
		assertThat(model.isSpinning()).isFalse();
	}

	@Test
	void setItems_rejectsNullElement() {
		// A null label used to survive the size checks and then NPE inside
		// WheelRenderer on the EDT during the first paint.
		WheelModel model = new WheelModel(List.of("A", "B"));
		List<String> withNull = java.util.Arrays.asList("A", null, "C");
		assertThatThrownBy(() -> model.setItems(withNull))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("items[1]");
		assertThat(model.getItems()).containsExactly("A", "B");
	}

	// ----- rotation / selection -----

	@Test
	void setRotationAngle_normalizesToPlusMinus360() {
		WheelModel model = new WheelModel(List.of("A", "B", "C", "D"));
		model.setRotationAngleDeg(450);
		assertThat(model.getRotationAngleDeg()).isEqualTo(90.0, within(1e-9));
		model.setRotationAngleDeg(-450);
		assertThat(model.getRotationAngleDeg()).isEqualTo(-90.0, within(1e-9));
	}

	@Test
	void getSelectedSectionIndex_returnsIndexUnderTick() {
		// 4 sections of 90 degrees each: [A=0, B=90, C=180, D=270]
		WheelModel model = new WheelModel(List.of("A", "B", "C", "D"));
		assertThat(model.getSelectedItem()).isEqualTo("A");
		model.setRotationAngleDeg(90);
		assertThat(model.getSelectedItem()).isEqualTo("B");
		model.setRotationAngleDeg(180);
		assertThat(model.getSelectedItem()).isEqualTo("C");
		model.setRotationAngleDeg(270);
		assertThat(model.getSelectedItem()).isEqualTo("D");
	}

	@Test
	void getSelectedItem_wrapsForNegativeRotation() {
		// Selection formula: (int)Math.floor(n + (angle % 360) / delta) % n
		// For -1 deg on a 4-section wheel (delta=90):
		//   -1 / 90.0 = -0.0111, Math.floor(-0.0111) = -1, 4 + (-1) = 3, %4 = 3 -> "D"
		// For -90 deg: -90 / 90.0 = -1.0, floor = -1, 4-1=3, %4=3 -> "D"
		// For -91 deg: -91 / 90.0 = -1.0111, floor = -2, 4-2=2, %4=2 -> "C"
		WheelModel model = new WheelModel(List.of("A", "B", "C", "D"));
		model.setRotationAngleDeg(-1);
		assertThat(model.getSelectedItem()).isEqualTo("D");
		model.setRotationAngleDeg(-90);
		assertThat(model.getSelectedItem()).isEqualTo("D");
		model.setRotationAngleDeg(-91);
		assertThat(model.getSelectedItem()).isEqualTo("C");
	}

	// ----- spin defaults / deceleration validation -----

	@Test
	void defaultMaxSpinSpeed_is360() {
		WheelModel model = new WheelModel(List.of("A"));
		assertThat(model.getMaxSpinSpeedDegPerSec()).isEqualTo(360);
	}

	@Test
	void setMaxSpinSpeed_acceptsPositiveValue() {
		WheelModel model = new WheelModel(List.of("A"));
		model.setMaxSpinSpeedDegPerSec(720);
		assertThat(model.getMaxSpinSpeedDegPerSec()).isEqualTo(720);
	}

	@Test
	void setMaxSpinSpeed_rejectsNonPositiveValues() {
		// A negative limit used to be stored as-is; WheelMath.clampSpeed then
		// threw on every drag release inside the EDT handler, which killed
		// the spin feature silently for the rest of the session.
		WheelModel model = new WheelModel(List.of("A"));
		assertThatThrownBy(() -> model.setMaxSpinSpeedDegPerSec(0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("maxSpinSpeed must be > 0");
		assertThatThrownBy(() -> model.setMaxSpinSpeedDegPerSec(-360))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("maxSpinSpeed must be > 0");
		assertThat(model.getMaxSpinSpeedDegPerSec()).isEqualTo(360);
	}

	@Test
	void setMaxSpinSpeed_rejectsNonFiniteValues() {
		WheelModel model = new WheelModel(List.of("A"));
		assertThatThrownBy(() -> model.setMaxSpinSpeedDegPerSec(Double.NaN))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("maxSpinSpeed must be > 0");
		assertThatThrownBy(() -> model.setMaxSpinSpeedDegPerSec(Double.POSITIVE_INFINITY))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> model.setMaxSpinSpeedDegPerSec(Double.NEGATIVE_INFINITY))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(model.getMaxSpinSpeedDegPerSec()).isEqualTo(360);
	}

	@Test
	void defaultDeceleration_isMinus20() {
		WheelModel model = new WheelModel(List.of("A"));
		assertThat(model.getSpinDeceleration()).isEqualTo(-20);
	}

	@Test
	void setSpinDeceleration_rejectsPositiveValue() {
		WheelModel model = new WheelModel(List.of("A"));
		assertThatThrownBy(() -> model.setSpinDeceleration(10))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("deceleration must be <= 0");
	}

	@Test
	void setSpinDeceleration_acceptsZero() {
		// zero = perpetual spin
		WheelModel model = new WheelModel(List.of("A"));
		model.setSpinDeceleration(0);
		assertThat(model.getSpinDeceleration()).isEqualTo(0);
	}

	@Test
	void setSpinDeceleration_rejectsNonFiniteValues() {
		// NaN passes a plain "> 0" test, so it used to be stored and then
		// made every tick produce NaN angles.
		WheelModel model = new WheelModel(List.of("A"));
		assertThatThrownBy(() -> model.setSpinDeceleration(Double.NaN))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("deceleration must be <= 0");
		assertThatThrownBy(() -> model.setSpinDeceleration(Double.NEGATIVE_INFINITY))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(model.getSpinDeceleration()).isEqualTo(-20);
	}

	// ----- startSpin -----

	@Test
	void startSpin_setsSpinningState() {
		WheelModel model = new WheelModel(List.of("A", "B"));
		model.startSpin(360, 1, -20);
		assertThat(model.isSpinning()).isTrue();
		assertThat(model.getSpinSpeedDegPerSec()).isEqualTo(360);
	}

	@Test
	void startSpin_rejectsPositiveDeceleration() {
		WheelModel model = new WheelModel(List.of("A", "B"));
		assertThatThrownBy(() -> model.startSpin(360, 1, 10))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void startSpin_rejectsNonFiniteSpeed() {
		// NaN passes the "speed <= 0" no-op check, so the model used to end
		// up spinning forever with a NaN rotation angle.
		WheelModel model = new WheelModel(List.of("A", "B"));
		assertThatThrownBy(() -> model.startSpin(Double.NaN, 1, -20))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("speedDegPerSec must be finite");
		assertThatThrownBy(() -> model.startSpin(Double.POSITIVE_INFINITY, 1, -20))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("speedDegPerSec must be finite");
		assertThat(model.isSpinning()).isFalse();
		assertThat(model.getRotationAngleDeg()).isEqualTo(0);
	}

	@Test
	void startSpin_rejectsNonFiniteDeceleration() {
		WheelModel model = new WheelModel(List.of("A", "B"));
		assertThatThrownBy(() -> model.startSpin(360, 1, Double.NaN))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("deceleration must be <= 0");
		assertThatThrownBy(() -> model.startSpin(360, 1, Double.NEGATIVE_INFINITY))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(model.isSpinning()).isFalse();
	}

	@Test
	void startSpin_isNoOpForZeroSpeed() {
		WheelModel model = new WheelModel(List.of("A", "B"));
		model.startSpin(0, 1, -20);
		assertThat(model.isSpinning()).isFalse();
	}

	@Test
	void startSpin_isNoOpForZeroDirection() {
		WheelModel model = new WheelModel(List.of("A", "B"));
		model.startSpin(360, 0, -20);
		assertThat(model.isSpinning()).isFalse();
	}

	// ----- tickSpin -----

	@Test
	void tickSpin_advancesRotationByDeltaAndDecelerates() {
		// dt = 0.01s, speed = 360 deg/s, dir = +1, decel = -20 deg/s^2
		// delta = +1 * 360 * 0.01 = 3.6 deg
		// newSpeed = 360 - 0.2 = 359.8 deg/s
		WheelModel model = new WheelModel(List.of("A", "B", "C", "D"));
		model.startSpin(360, 1, -20);
		SpinStep step = model.tickSpin(0.01);
		assertThat(step.deltaDeg()).isEqualTo(3.6, within(1e-9));
		assertThat(step.newSpeed()).isEqualTo(359.8, within(1e-9));
		assertThat(step.shouldStop()).isFalse();
		assertThat(model.getRotationAngleDeg()).isEqualTo(3.6, within(1e-9));
		assertThat(model.getSpinSpeedDegPerSec()).isEqualTo(359.8, within(1e-9));
	}

	@Test
	void tickSpin_stopsModelWhenDeceleratedToRest() {
		WheelModel model = new WheelModel(List.of("A", "B"));
		model.startSpin(0.1, 1, -20);
		SpinStep step = model.tickSpin(0.01);
		assertThat(step.shouldStop()).isTrue();
		assertThat(model.isSpinning()).isFalse();
		assertThat(model.getSpinSpeedDegPerSec()).isEqualTo(0);
	}

	@Test
	void tickSpin_afterStop_isIdempotent() {
		WheelModel model = new WheelModel(List.of("A", "B"));
		model.startSpin(360, 1, -20);
		model.stopSpin();
		SpinStep step = model.tickSpin(0.01);
		assertThat(step.deltaDeg()).isEqualTo(0);
		assertThat(step.shouldStop()).isTrue();
	}

	@Test
	void tickSpin_normalizesRotationAcrossFullCircle() {
		// Run many ticks: rotation must stay in (-360, 360)
		WheelModel model = new WheelModel(List.of("A", "B", "C", "D"));
		model.startSpin(3600, 1, 0); // perpetual spin, no deceleration
		for (int i = 0; i < 1000; i++) {
			model.tickSpin(0.01);
		}
		assertThat(model.getRotationAngleDeg()).isBetween(-360.0, 360.0);
	}

	@Test
	void tickSpin_rejectsNonPositiveDt() {
		WheelModel model = new WheelModel(List.of("A", "B"));
		assertThatThrownBy(() -> model.tickSpin(0))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> model.tickSpin(-1))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void tickSpin_rejectsNonFiniteDtWithoutPoisoningTheState() {
		WheelModel model = new WheelModel(List.of("A", "B"));
		model.startSpin(360, 1, -20);

		// "dtSeconds <= 0" is false for NaN, so the guard let it through and
		// the model ended up spinning forever with a NaN rotation angle.
		assertThatThrownBy(() -> model.tickSpin(Double.NaN))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("dtSeconds must be > 0 and finite");
		assertThatThrownBy(() -> model.tickSpin(Double.POSITIVE_INFINITY))
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(model.getRotationAngleDeg()).isEqualTo(0.0);
		assertThat(model.isSpinning()).isTrue();
		// The rejected tick must not have advanced the spin either.
		assertThat(model.tickSpin(0.01).deltaDeg()).isEqualTo(3.6, within(1e-9));
	}

	// ----- stopSpin -----

	@Test
	void stopSpin_clearsSpinningState() {
		WheelModel model = new WheelModel(List.of("A", "B"));
		model.startSpin(360, 1, -20);
		model.stopSpin();
		assertThat(model.isSpinning()).isFalse();
		assertThat(model.getSpinSpeedDegPerSec()).isEqualTo(0);
	}

	@Test
	void stopSpin_isIdempotent() {
		WheelModel model = new WheelModel(List.of("A", "B"));
		model.stopSpin();
		model.stopSpin();
		assertThat(model.isSpinning()).isFalse();
	}

	// ----- integration: full spin from start to rest -----

	@Test
	void fullSpin_comesToRestAtTheExactTickCountAndSweep() {
		// speed=360, decel=-20, dt=0.01s: the speed drops by 0.2 per tick.
		// After 1800 ticks the accumulated speed is a tiny positive residue
		// (~1.5e-13 deg/s) instead of exactly 0, so the loop needs one more
		// tick to observe the stop: 1801 ticks in total.
		// Swept angle = sum over k=0..1800 of 0.01 * (360 - 0.2k) = 3241.8 deg
		// (not 3240: the final tick still rotates by 0.002 deg).
		WheelModel model = new WheelModel(List.of("A", "B", "C", "D"));
		model.startSpin(360, 1, -20);
		int ticks = 0;
		double sweptDeg = 0;
		while (model.isSpinning() && ticks < 10000) {
			sweptDeg += model.tickSpin(0.01).deltaDeg();
			ticks++;
		}
		assertThat(model.isSpinning()).isFalse();
		assertThat(ticks).isEqualTo(1801);
		assertThat(sweptDeg).isEqualTo(3241.8, within(1e-6));
		// The model stores the normalized angle: 3241.8 % 360 = 1.8 deg.
		assertThat(model.getRotationAngleDeg()).isEqualTo(1.8, within(1e-6));
		// 1.8 deg is inside the first section, which is why "A" wins. The
		// angle assertions above are what actually pin the result down.
		assertThat(model.getSelectedItem()).isEqualTo("A");
	}
}
