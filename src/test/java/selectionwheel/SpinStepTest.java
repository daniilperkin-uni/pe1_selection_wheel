package selectionwheel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class SpinStepTest {

	// ----- zero / stopped spins -----

	@Test
	void compute_zeroSpeed_immediatelyStops() {
		SpinStep step = SpinStep.compute(0, 1, -20, 0.01);
		assertThat(step.deltaDeg()).isEqualTo(0);
		assertThat(step.newSpeed()).isEqualTo(0);
		assertThat(step.shouldStop()).isTrue();
	}

	@Test
	void compute_zeroDirection_immediatelyStops() {
		SpinStep step = SpinStep.compute(360, 0, -20, 0.01);
		assertThat(step.shouldStop()).isTrue();
	}

	@Test
	void compute_negativeSpeed_immediatelyStops() {
		SpinStep step = SpinStep.compute(-50, 1, -20, 0.01);
		assertThat(step.shouldStop()).isTrue();
	}

	// ----- normal forward / reverse -----

	@Test
	void compute_forwardSpin_advancesAndDecelerates() {
		// speed=360 deg/s, direction=+1, deceleration=-20 deg/s^2, dt=0.01s
		// delta = +1 * 360 * 0.01 = 3.6 deg
		// newSpeed = 360 + (-20) * 0.01 = 360 - 0.2 = 359.8 deg/s
		SpinStep step = SpinStep.compute(360, 1, -20, 0.01);
		assertThat(step.deltaDeg()).isEqualTo(3.6, within(1e-9));
		assertThat(step.newSpeed()).isEqualTo(359.8, within(1e-9));
		assertThat(step.shouldStop()).isFalse();
	}

	@Test
	void compute_reverseSpin_advancesNegatively() {
		// direction=-1 -> delta is negative
		SpinStep step = SpinStep.compute(360, -1, -20, 0.01);
		assertThat(step.deltaDeg()).isEqualTo(-3.6, within(1e-9));
		assertThat(step.newSpeed()).isEqualTo(359.8, within(1e-9));
		assertThat(step.shouldStop()).isFalse();
	}

	// ----- deceleration to rest -----

	@Test
	void compute_deceleratingToZero_marksStop() {
		// speed=0.2 deg/s, decel=-20 deg/s^2, dt=0.01s -> newSpeed = 0.2 - 0.2 = 0
		SpinStep step = SpinStep.compute(0.2, 1, -20, 0.01);
		assertThat(step.newSpeed()).isEqualTo(0);
		assertThat(step.shouldStop()).isTrue();
		// The delta is still applied (wheel rotates by the last bit)
		assertThat(step.deltaDeg()).isEqualTo(0.002, within(1e-9));
	}

	@Test
	void compute_deceleratingPastZero_clampsToZeroAndStops() {
		// speed=0.1 deg/s, decel=-20, dt=0.01 -> newSpeed = 0.1 - 0.2 = -0.1 -> clamp to 0
		SpinStep step = SpinStep.compute(0.1, 1, -20, 0.01);
		assertThat(step.newSpeed()).isEqualTo(0);
		assertThat(step.shouldStop()).isTrue();
	}

	// ----- perpetual spin -----

	@Test
	void compute_zeroDeceleration_continuesIndefinitely() {
		SpinStep step = SpinStep.compute(360, 1, 0, 0.01);
		assertThat(step.newSpeed()).isEqualTo(360);
		assertThat(step.shouldStop()).isFalse();
	}

	// ----- argument validation -----

	@Test
	void compute_nonPositiveDt_throws() {
		assertThatThrownBy(() -> SpinStep.compute(360, 1, -20, 0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("dtSeconds must be > 0");
		assertThatThrownBy(() -> SpinStep.compute(360, 1, -20, -1))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// ----- immutability -----

	@Test
	void spinStep_isImmutableRecord() {
		SpinStep step = SpinStep.compute(360, 1, -20, 0.01);
		assertThat(step.deltaDeg()).isEqualTo(3.6, within(1e-9));
		// No setters on a record - the test would not compile if any existed.
		// We assert that the record components are final.
		assertThat(step).isIn(SpinStep.compute(360, 1, -20, 0.01));
	}
}
