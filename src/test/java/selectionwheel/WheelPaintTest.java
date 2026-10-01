package selectionwheel;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

/**
 * Paint-level regression tests for the {@link Wheel} image cache.
 *
 * <p>The component rasterizes the wheel once and only re-renders when the
 * cached image is invalidated (resize, item change, ...). The image must
 * therefore be rotation-independent: the model's rotation is applied by
 * {@link Wheel#paintComponent} at paint time. Before the fix,
 * {@link WheelRenderer} baked the rotation into the image as well, so a
 * resize while the wheel was rotated made the wheel jump by twice the
 * angle and the item under the tick no longer matched the selection.
 *
 * <p>Like the other component tests, these run on the EDT and need a
 * display (see the surefire {@code java.awt.headless=false} setting).
 */
class WheelPaintTest {

	/** Paints the wheel offscreen and returns the resulting image. */
	private static BufferedImage paint(Wheel wheel) {
		BufferedImage image = new BufferedImage(
				wheel.getWidth(), wheel.getHeight(), BufferedImage.TYPE_INT_ARGB);
		wheel.paintComponent(image.getGraphics());
		return image;
	}

	/** Counts the pixels that differ between two images of the same size. */
	private static int differingPixels(BufferedImage first, BufferedImage second) {
		int differing = 0;
		for (int y = 0; y < first.getHeight(); y++) {
			for (int x = 0; x < first.getWidth(); x++) {
				if (first.getRGB(x, y) != second.getRGB(x, y)) {
					differing++;
				}
			}
		}
		return differing;
	}

	@Test
	void repaintAfterInvalidation_rendersTheSameRotation() throws Exception {
		final BufferedImage[] before = new BufferedImage[1];
		final BufferedImage[] after = new BufferedImage[1];

		SwingUtilities.invokeAndWait(() -> {
			Wheel wheel = new Wheel(List.of("A", "B", "C", "D"));
			wheel.setBounds(0, 0, 400, 400);
			paint(wheel); // warm the image cache at rotation 0
			wheel.setRotationAngle(90);
			before[0] = paint(wheel);
			wheel.setBounds(0, 0, 400, 400); // invalidates the cached image
			after[0] = paint(wheel);
		});

		assertThat(differingPixels(before[0], after[0]))
				.as("the same rotation must paint the same image regardless of the cache state")
				.isZero();
	}

	@Test
	void rotationChangesThePaintedImage() throws Exception {
		final BufferedImage[] images = new BufferedImage[2];

		SwingUtilities.invokeAndWait(() -> {
			Wheel wheel = new Wheel(List.of("A", "B", "C", "D"));
			wheel.setBounds(0, 0, 400, 400);
			images[0] = paint(wheel);
			wheel.setRotationAngle(90);
			images[1] = paint(wheel);
		});

		assertThat(differingPixels(images[0], images[1]))
				.as("rotating the wheel must change the painted image")
				.isPositive();
	}

	@Test
	void emptyColorScheme_fallsBackToTheDefaultPalette() throws Exception {
		final BufferedImage[] image = new BufferedImage[1];

		SwingUtilities.invokeAndWait(() -> {
			Wheel wheel = new Wheel(List.of("A", "B"));
			wheel.setBounds(0, 0, 200, 200);
			paint(wheel); // warm the image cache
			wheel.setColorScheme(new ArrayList<>());
			image[0] = paint(wheel); // used to throw ArithmeticException
		});

		assertThat(image[0].getRGB(100, 100) >>> 24)
				.as("the wheel must still be painted with the default palette")
				.isNotZero();
	}
}
