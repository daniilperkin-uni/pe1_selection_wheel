package selectionwheel;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mouse-interaction tests for {@link Wheel}.
 *
 * <p>Covers click-to-stop hit testing. The component rasterizes into an
 * image that can be larger than itself (the renderer zooms in when the
 * labels get too small), so the wheel is drawn around
 * {@code rotationCenter} in component coordinates while the renderer's
 * {@code center} stays relative to that offscreen image. A hit test that
 * mixes the two coordinate spaces misses the visible wheel.
 *
 * <p>Like the other component tests, these run on the EDT and need a
 * display (see the surefire {@code java.awt.headless=false} setting).
 */
class WheelMouseTest {

	/** 100 long labels force the renderer into its zoom-in path. */
	private static List<String> zoomedItems() {
		List<String> items = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			items.add("ITEM" + String.format("%02d", i) + "XXXXXXXXXXXXXXXX");
		}
		return items;
	}

	private static void press(Wheel wheel, int x, int y) {
		wheel.dispatchEvent(new MouseEvent(wheel, MouseEvent.MOUSE_PRESSED,
				System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1));
	}

	@Test
	void clickOnTheVisibleWheel_stopsTheSpinWhenTheImageIsLargerThanTheComponent() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			Wheel wheel = new Wheel(zoomedItems());
			wheel.setBounds(0, 0, 100, 100);
			// Paint once so the image cache and rotationCenter exist.
			Graphics2D g = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB).createGraphics();
			wheel.paintComponent(g);
			g.dispose();

			// The wheel is far larger than the 100x100 component here, so the
			// left edge of the component is inside the painted circle but
			// 310 px away from the image-local center.
			assertThat(wheel.getRadius()).isGreaterThan(100);

			wheel.spinStartAsync(100, 1, -20);
			assertThat(wheel.isSpinning()).isTrue();

			press(wheel, 0, 50);

			assertThat(wheel.isSpinning())
					.as("a click inside the visible wheel must stop the spin")
					.isFalse();
		});
	}

	@Test
	void clickOutsideTheWheel_leavesTheSpinRunning() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			Wheel wheel = new Wheel(List.of("A", "B", "C", "D"));
			wheel.setBounds(0, 0, 400, 400);
			Graphics2D g = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB).createGraphics();
			wheel.paintComponent(g);
			g.dispose();

			wheel.spinStartAsync(100, 1, -20);
			// The painted wheel is centred at (200,200) with a radius below
			// 200, so the corner is well outside it.
			press(wheel, 2, 2);

			assertThat(wheel.isSpinning())
					.as("a click outside the wheel must not stop the spin")
					.isTrue();
			wheel.spinStop();
		});
	}
}
