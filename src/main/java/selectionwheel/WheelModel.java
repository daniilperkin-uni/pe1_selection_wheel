package selectionwheel;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, testable state holder for a wheel of fortune.
 *
 * <p>Holds the list of selectable items, the current rotation angle,
 * and the spin animation state (speed, direction, deceleration).
 * Provides methods to mutate state and to advance the spin one tick
 * at a time via {@link #tickSpin(double)}.
 *
 * <p>This class is intentionally Swing-free: it does not extend any
 * JComponent, does not call repaint, and does not touch the EDT.
 * All state mutations are synchronous and deterministic, which makes
 * the model fully unit-testable without a graphics environment.
 *
 * <p>The {@link Wheel} view holds a {@code WheelModel} instance and
 * delegates state queries to it, while performing rendering and
 * event delivery itself.
 */
public final class WheelModel {

	/** Maximum number of items the wheel can display. Matches {@code Wheel.LIMIT}. */
	public static final int ITEM_LIMIT = 100;

	private List<String> items;
	private int numSections;
	private double rotationAngleDeg;

	private double maxSpinSpeedDegPerSec = 360;
	private double spinDeceleration = -20;

	private boolean spinning;
	private double activeSpinSpeed;
	private int activeSpinDirection;
	private double activeSpinDeceleration;

	/**
	 * @param items the initial list of selectable items
	 * @throws IllegalArgumentException if {@code items} is null, empty,
	 *                                  or larger than {@link #ITEM_LIMIT}
	 */
	public WheelModel(List<String> items) {
		setItems(items);
	}

	/**
	 * Replaces the list of selectable items. Resets the rotation angle
	 * to {@code 0} and stops any in-flight spin.
	 *
	 * @param items new list of items
	 * @throws IllegalArgumentException if {@code items} is null, empty,
	 *                                  or larger than {@link #ITEM_LIMIT}
	 */
	public void setItems(List<String> items) {
		if (items == null) {
			throw new IllegalArgumentException("items must not be null");
		}
		int n = items.size();
		if (n == 0) {
			throw new IllegalArgumentException("items must not be empty (would produce NaN delta)");
		}
		if (n > ITEM_LIMIT) {
			throw new IllegalArgumentException(
					"items size " + n + " exceeds limit " + ITEM_LIMIT);
		}
		this.items = new ArrayList<>(items);
		this.numSections = n;
		this.rotationAngleDeg = 0;
		this.spinning = false;
		this.activeSpinSpeed = 0;
	}

	/** @return a defensive copy of the items list */
	public List<String> getItems() {
		return new ArrayList<>(items);
	}

	/** @return the number of sections (same as {@code getItems().size()}) */
	public int getNumSections() {
		return numSections;
	}

	/** @return the angular width (degrees) of a single section */
	public double getSectionAngleDeg() {
		return WheelMath.sectionAngleDeg(numSections);
	}

	/** @return the current rotation angle in degrees, in {@code (-360, 360)} */
	public double getRotationAngleDeg() {
		return rotationAngleDeg;
	}

	/**
	 * Sets the rotation angle, normalizing it to {@code (-360, 360)}.
	 *
	 * @param angleDeg any angle, positive or negative
	 */
	public void setRotationAngleDeg(double angleDeg) {
		this.rotationAngleDeg = WheelMath.normalizeAngleDeg(angleDeg);
	}

	/** @return the index of the section currently under the tick */
	public int getSelectedSectionIndex() {
		return WheelMath.selectionIndex(rotationAngleDeg, numSections);
	}

	/** @return the label of the section currently under the tick */
	public String getSelectedItem() {
		return items.get(getSelectedSectionIndex());
	}

	public boolean isSpinning() {
		return spinning;
	}

	/**
	 * @return current spin speed in deg/s, or {@code 0} if not spinning
	 */
	public double getSpinSpeedDegPerSec() {
		return spinning ? activeSpinSpeed : 0;
	}

	public double getMaxSpinSpeedDegPerSec() {
		return maxSpinSpeedDegPerSec;
	}

	public void setMaxSpinSpeedDegPerSec(double v) {
		this.maxSpinSpeedDegPerSec = v;
	}

	public double getSpinDeceleration() {
		return spinDeceleration;
	}

	/**
	 * @param deceleration deg/s^2; must be {@code <= 0}
	 * @throws IllegalArgumentException if {@code deceleration > 0}
	 */
	public void setSpinDeceleration(double deceleration) {
		if (deceleration > 0) {
			throw new IllegalArgumentException(
					"deceleration must be <= 0 (was " + deceleration + ")");
		}
		this.spinDeceleration = deceleration;
	}

	/**
	 * Starts a decelerating spin. No-op if {@code speedDegPerSec <= 0}
	 * or {@code direction == 0}.
	 *
	 * @param speedDegPerSec initial speed magnitude (deg/s)
	 * @param direction      {@code +1} or {@code -1}
	 * @param deceleration   deg/s^2, must be {@code <= 0}
	 * @throws IllegalArgumentException if {@code deceleration > 0}
	 */
	public void startSpin(double speedDegPerSec, int direction, double deceleration) {
		if (deceleration > 0) {
			throw new IllegalArgumentException(
					"deceleration must be <= 0 (was " + deceleration + ")");
		}
		if (speedDegPerSec <= 0 || direction == 0) {
			return;
		}
		this.spinning = true;
		this.activeSpinSpeed = speedDegPerSec;
		this.activeSpinDirection = direction;
		this.activeSpinDeceleration = deceleration;
	}

	/**
	 * Advances the spin by one tick of {@code dtSeconds}.
	 *
	 * @param dtSeconds tick duration in seconds ({@code > 0})
	 * @return the {@link SpinStep} that describes this tick
	 * @throws IllegalArgumentException if {@code dtSeconds <= 0}
	 */
	public SpinStep tickSpin(double dtSeconds) {
		if (dtSeconds <= 0) {
			throw new IllegalArgumentException(
					"dtSeconds must be > 0 (was " + dtSeconds + ")");
		}
		if (!spinning) {
			return new SpinStep(0, 0, true);
		}
		SpinStep step = SpinStep.compute(
				activeSpinSpeed, activeSpinDirection, activeSpinDeceleration, dtSeconds);
		rotationAngleDeg = WheelMath.normalizeAngleDeg(rotationAngleDeg + step.deltaDeg());
		activeSpinSpeed = step.newSpeed();
		if (step.shouldStop()) {
			spinning = false;
			activeSpinSpeed = 0;
		}
		return step;
	}

	/** Stops any in-flight spin. Idempotent. */
	public void stopSpin() {
		spinning = false;
		activeSpinSpeed = 0;
	}
}
