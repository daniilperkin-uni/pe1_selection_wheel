package selectionwheel;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration smoke test for {@link SelectionWheel}.
 *
 * <p>Verifies that the composed component (Wheel + Tick + WheelModel)
 * is wired correctly: bounds propagation, tick visibility, listener
 * delivery, and spin lifecycle. All assertions run on the EDT via
 * {@link SwingUtilities#invokeAndWait} to match the threading contract.
 *
 * <p>Unlike AssertJ-Swing fixture tests, this does not require a
 * display: it constructs components but never packs or shows a window.
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
			wheel.setRotationAngle(90);
		});
		assertThat(lastSelection.get()).isEqualTo("B");
		assertThat(selectionChangeCount.get()).isGreaterThanOrEqualTo(1);
		assertThat(lastRotation.get()).isEqualTo(90.0);
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
}
