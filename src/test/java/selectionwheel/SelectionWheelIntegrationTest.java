package selectionwheel;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration smoke test for {@link SelectionWheel}.
 *
 * <p>Verifies that the composed component (Wheel + Tick + WheelModel)
 * is wired correctly: bounds propagation, tick visibility, listener
 * delivery, and spin lifecycle. All assertions run on the EDT via
 * {@link SwingUtilities#invokeAndWait} to match the threading contract.
 *
 * <p>The tests construct components but never pack or show a window.
 * They do need a display all the same: the surefire argLine forces
 * {@code java.awt.headless=false}, so a headless Linux runner must wrap
 * the build in xvfb (see the CI workflow).
 */
class SelectionWheelIntegrationTest {

	private SelectionWheel createWheel(List<String> items) throws Exception {
		ArrayList<String> copy = new ArrayList<>(items);
		final SelectionWheel[] holder = new SelectionWheel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new SelectionWheel(copy));
		return holder[0];
	}

	@Test
	void constructor_initializesWithItems() throws Exception {
		SelectionWheel wheel = createWheel(List.of("A", "B", "C", "D"));
		SwingUtilities.invokeAndWait(() -> {
			assertThat(wheel.getItems()).containsExactly("A", "B", "C", "D");
			assertThat(wheel.isSpinning()).isFalse();
			assertThat(wheel.getSpinSpeed()).isEqualTo(0.0);
			assertThat(wheel.getSelectedItem()).isEqualTo("A");
		});
	}

	@Test
	void constructor_acceptsAnyListImplementation() throws Exception {
		// The public API used to demand ArrayList, so List.of(...) - the
		// natural way to build a fixed item list - did not compile, even
		// though Wheel and WheelModel accept any List.
		final SelectionWheel[] holder = new SelectionWheel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new SelectionWheel(List.of("A", "B")));
		assertThat(holder[0].getItems()).containsExactly("A", "B");

		SwingUtilities.invokeAndWait(() -> {
			holder[0].setColorScheme(List.of(Color.RED, Color.BLUE));
			assertThat(holder[0].getColorScheme()).containsExactly(Color.RED, Color.BLUE);
			// addColor() must survive an immutable list being passed in.
			holder[0].addColor(Color.GREEN);
			assertThat(holder[0].getColorScheme())
					.containsExactly(Color.RED, Color.BLUE, Color.GREEN);
		});
	}

	@Test
	@SuppressWarnings("deprecation")
	void borders_canBeSetAndQueried() throws Exception {
		SelectionWheel wheel = createWheel(List.of("A", "B"));
		SwingUtilities.invokeAndWait(() -> {
			assertThat(wheel.hasBorders()).isFalse();
			wheel.setHasBorders(true);
			assertThat(wheel.hasBorders()).isTrue();
			assertThat(wheel.getWheel().hasBorders()).isTrue();
			// The deprecated mutator must keep delegating for one release.
			wheel.hasBorders(false);
			assertThat(wheel.hasBorders()).isFalse();
		});
	}

	@Test
	void preferredAndMinimumSize_areNonZeroAndRespectAnExplicitValue() throws Exception {
		SelectionWheel wheel = createWheel(List.of("A", "B"));
		SwingUtilities.invokeAndWait(() -> {
			// The composite uses a null layout, so the inherited metrics are
			// 0x0 and a FlowLayout/GridBagLayout or Window.pack() collapsed it.
			assertThat(wheel.getPreferredSize().width).isPositive();
			assertThat(wheel.getPreferredSize().height).isPositive();
			assertThat(wheel.getMinimumSize().width).isPositive();
			assertThat(wheel.getMinimumSize().height).isPositive();

			Dimension explicit = new Dimension(250, 260);
			wheel.setPreferredSize(explicit);
			assertThat(wheel.getPreferredSize())
					.as("an explicitly set preferred size must win over the default")
					.isEqualTo(explicit);
		});
	}

	@Test
	void setBounds_withVisibleTick_narrowsWheelByTickWidth() throws Exception {
		SelectionWheel wheel = createWheel(List.of("A", "B"));
		SwingUtilities.invokeAndWait(() -> {
			wheel.setBounds(0, 0, 200, 200);
			int tickWidth = (int) wheel.getTickWidth();
			assertThat(wheel.getWheel().getBounds())
					.isEqualTo(new Rectangle(0, 0, 200 - tickWidth, 200));
			assertThat(wheel.getTick().getBounds())
					.isEqualTo(new Rectangle(200 - tickWidth, 0, tickWidth, 200));
		});
	}

	@Test
	void setBounds_withHiddenTick_givesWheelFullWidth() throws Exception {
		SelectionWheel wheel = createWheel(List.of("A", "B"));
		SwingUtilities.invokeAndWait(() -> {
			wheel.setTickVisible(false);
			wheel.setBounds(0, 0, 200, 200);
			assertThat(wheel.getWheel().getBounds())
					.isEqualTo(new Rectangle(0, 0, 200, 200));
			assertThat(wheel.getTick().getBounds())
					.isEqualTo(new Rectangle(0, 0, 0, 0));
			assertThat(wheel.isTickVisible()).isFalse();
		});
	}

	@Test
	void setTickVisible_true_reinstatesTickWidthOffset() throws Exception {
		SelectionWheel wheel = createWheel(List.of("A", "B"));
		SwingUtilities.invokeAndWait(() -> {
			wheel.setBounds(0, 0, 200, 200);
			wheel.setTickVisible(false);
			assertThat(wheel.getWheel().getWidth()).isEqualTo(200);
			wheel.setTickVisible(true);
			int tickWidth = (int) wheel.getTickWidth();
			assertThat(wheel.getWheel().getWidth()).isEqualTo(200 - tickWidth);
			assertThat(wheel.isTickVisible()).isTrue();
		});
	}

	@Test
	void setRotationAngle_updatesSelectionAndFiresListener() throws Exception {
		SelectionWheel wheel = createWheel(List.of("A", "B", "C", "D"));
		AtomicReference<String> lastSelection = new AtomicReference<>("(none)");
		AtomicInteger selectionChangeCount = new AtomicInteger(0);
		AtomicReference<Double> lastRotation = new AtomicReference<>(-999.0);

		wheel.addWheelListener(new WheelListener() {
			@Override
			public void selectionChanged(String selectedItem) {
				lastSelection.set(selectedItem);
				selectionChangeCount.incrementAndGet();
			}

			@Override
			public void rotationChanged(double angleDegrees) {
				lastRotation.set(angleDegrees);
			}
		});

		SwingUtilities.invokeAndWait(() -> {
			// Two angles inside the same section (A spans 0..90 deg on a
			// 4-item wheel): the listener must fire exactly once.
			wheel.setRotationAngle(10);
			assertThat(selectionChangeCount.get())
					.as("first change into a new section must be reported")
					.isEqualTo(1);
			wheel.setRotationAngle(20);
			assertThat(selectionChangeCount.get())
					.as("moving inside the same section must not report again")
					.isEqualTo(1);
			// Crossing into section B must report exactly once more.
			wheel.setRotationAngle(100);
			assertThat(selectionChangeCount.get()).isEqualTo(2);
		});
		assertThat(lastSelection.get()).isEqualTo("B");
		assertThat(lastRotation.get()).isEqualTo(100.0);
	}

	@Test
	void spinStartAsync_thenStop_firesSpinStartedAndStopped() throws Exception {
		SelectionWheel wheel = createWheel(List.of("A", "B"));
		AtomicInteger startCount = new AtomicInteger(0);
		AtomicInteger stopCount = new AtomicInteger(0);
		// Replace the previous Thread.sleep(50) drain with a CountDownLatch
		// that is decremented by the spinStopped callback. The test then
		// awaits the latch with a timeout instead of sleeping blindly.
		CountDownLatch stopLatch = new CountDownLatch(1);
		wheel.addWheelListener(new WheelListener() {
			@Override
			public void spinStarted() { startCount.incrementAndGet(); }
			@Override
			public void spinStopped() {
				stopCount.incrementAndGet();
				stopLatch.countDown();
			}
		});

		SwingUtilities.invokeAndWait(() -> {
			wheel.spinStartAsync(360, 1, -20);
			assertThat(wheel.isSpinning()).isTrue();
			assertThat(startCount.get()).isEqualTo(1);
		});

		// Stop and verify the stopped event fires exactly once.
		SwingUtilities.invokeAndWait(() -> {
			wheel.spinStop();
			assertThat(wheel.isSpinning()).isFalse();
		});
		// Wait for the spinStopped callback to fire (delivered on the EDT).
		assertThat(stopLatch.await(2, TimeUnit.SECONDS))
				.as("spinStopped should have fired within the timeout")
				.isTrue();
		assertThat(stopCount.get()).isGreaterThanOrEqualTo(1);
	}

	@Test
	void removingAListenerInsideItsCallback_isAllowed() throws Exception {
		// Idiomatic Swing: a one-shot listener unregisters itself in the
		// callback. Iterating the backing ArrayList threw
		// ConcurrentModificationException and aborted the EDT callback.
		SelectionWheel wheel = createWheel(List.of("A", "B"));
		AtomicInteger selfRemovingCalls = new AtomicInteger(0);
		AtomicInteger otherCalls = new AtomicInteger(0);

		WheelListener selfRemoving = new WheelListener() {
			@Override
			public void spinStopped() {
				selfRemovingCalls.incrementAndGet();
				wheel.removeWheelListener(this);
			}
		};
		wheel.addWheelListener(selfRemoving);
		wheel.addWheelListener(new WheelListener() {
			@Override
			public void spinStopped() { otherCalls.incrementAndGet(); }
		});
		wheel.addWheelListener(new WheelListener() {
			@Override
			public void spinStopped() { otherCalls.incrementAndGet(); }
		});

		SwingUtilities.invokeAndWait(() -> {
			wheel.spinStartAsync(100, 1, -20);
			assertThatCode(() -> wheel.spinStop()).doesNotThrowAnyException();
			assertThat(selfRemovingCalls.get()).isEqualTo(1);
			assertThat(otherCalls.get())
					.as("the remaining listeners must still receive the event")
					.isEqualTo(2);
		});
	}

	@Test
	void removeNotify_stopsAnInFlightSpin() throws Exception {
		SelectionWheel wheel = createWheel(List.of("A", "B"));
		SwingUtilities.invokeAndWait(() -> {
			wheel.spinStartAsync(360, 1, -20);
			assertThat(wheel.isSpinning()).isTrue();
		});

		// Removing the component must stop its EDT timer; without the
		// override a detached wheel kept ticking and repainting until the
		// spin ended on its own. removeNotify() is invoked directly here
		// because a component in a never-displayed hierarchy does not
		// receive the callback from the toolkit.
		SwingUtilities.invokeAndWait(() -> wheel.getWheel().removeNotify());

		assertThat(wheel.isSpinning()).isFalse();
	}

	@Test
	void setItems_replacesItemsAndResetsSelection() throws Exception {
		SelectionWheel wheel = createWheel(List.of("A", "B", "C"));
		SwingUtilities.invokeAndWait(() -> {
			wheel.setRotationAngle(120);
			assertThat(wheel.getSelectedItem()).isEqualTo("B");
			wheel.setItems(new ArrayList<>(List.of("X", "Y")));
			assertThat(wheel.getItems()).containsExactly("X", "Y");
			assertThat(wheel.getSelectedItem()).isEqualTo("X");
			assertThat(wheel.getRotationAngle()).isEqualTo(0.0);
		});
	}

	@Test
	void setItems_duringSpin_deliversSpinStopped() throws Exception {
		// WheelModel.setItems() clears the spinning flag itself, so the old
		// ordering (model.setItems() before spinStop()) made spinStop() see
		// an already stopped model and deliver nothing. A listener that
		// disabled its UI on spinStarted() stayed disabled for good.
		SelectionWheel wheel = createWheel(List.of("A", "B", "C"));
		AtomicInteger startCount = new AtomicInteger(0);
		AtomicInteger stopCount = new AtomicInteger(0);
		wheel.addWheelListener(new WheelListener() {
			@Override
			public void spinStarted() { startCount.incrementAndGet(); }
			@Override
			public void spinStopped() { stopCount.incrementAndGet(); }
		});

		SwingUtilities.invokeAndWait(() -> {
			wheel.spinStartAsync(360, 1, -20);
			assertThat(wheel.isSpinning()).isTrue();
			wheel.setItems(new ArrayList<>(List.of("X", "Y")));
			// All of this runs on the EDT, so the events have been delivered
			// synchronously before the assertions.
			assertThat(wheel.isSpinning()).isFalse();
			assertThat(startCount.get()).isEqualTo(1);
			assertThat(stopCount.get())
					.as("replacing the items must stop the spin and report it")
					.isEqualTo(1);
		});
	}

	@Test
	void restartingASpin_deliversSpinStoppedForTheInterruptedSpin() throws Exception {
		// doStartSpin() used to restart via stopSpinTimer(), which stopped
		// the old timer without reporting the stop of the spin it killed.
		SelectionWheel wheel = createWheel(List.of("A", "B"));
		AtomicInteger startCount = new AtomicInteger(0);
		AtomicInteger stopCount = new AtomicInteger(0);
		wheel.addWheelListener(new WheelListener() {
			@Override
			public void spinStarted() { startCount.incrementAndGet(); }
			@Override
			public void spinStopped() { stopCount.incrementAndGet(); }
		});

		SwingUtilities.invokeAndWait(() -> {
			wheel.spinStartAsync(360, 1, -20);
			assertThat(wheel.isSpinning()).isTrue();
			wheel.spinStartAsync(180, 1, -20);
			assertThat(wheel.isSpinning()).isTrue();
			assertThat(startCount.get()).isEqualTo(2);
			assertThat(stopCount.get())
					.as("the interrupted spin must report its stop")
					.isEqualTo(1);
			wheel.spinStop();
			assertThat(stopCount.get()).isEqualTo(2);
		});
	}

	@Test
	void spinStartAsync_rejectsNonFiniteSpeedAndDeceleration() throws Exception {
		SelectionWheel wheel = createWheel(List.of("A", "B"));
		SwingUtilities.invokeAndWait(() -> {
			assertThatThrownBy(() -> wheel.spinStartAsync(Double.NaN, 1, -20))
					.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> wheel.spinStartAsync(360, 1, Double.NaN))
					.isInstanceOf(IllegalArgumentException.class);
			assertThat(wheel.isSpinning()).isFalse();
		});
	}
}
