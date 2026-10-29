package selectionwheel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
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
	void paintComponent_withNonPositiveDimensions_doesNotThrow() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			Wheel wheel = new Wheel(List.of("A", "B"));
			for (Dimension size : List.of(new Dimension(0, 0), new Dimension(-20, 50))) {
				wheel.setBounds(0, 0, size.width, size.height);
				Graphics2D g = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB).createGraphics();
				assertThatCode(() -> wheel.paintComponent(g))
						.as("painting a %dx%d component must be a no-op, not an EDT crash",
								size.width, size.height)
						.doesNotThrowAnyException();
				g.dispose();
			}
		});
	}

	@Test
	void paintComponent_doesNotMutateTheCallersGraphics() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			Wheel wheel = new Wheel(List.of("A", "B", "C", "D"));
			wheel.setBounds(0, 0, 400, 400);
			wheel.setRotationAngle(45);

			Graphics2D g = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB).createGraphics();
			AffineTransform before = g.getTransform();
			wheel.paintComponent(g);

			// Rotating the caller's Graphics in place left the 45 degree
			// transform behind, so everything painted afterwards with the
			// same context - including a second paint pass - was rotated too.
			assertThat(g.getTransform())
					.as("paintComponent must rotate a copy, not the caller's Graphics")
					.isEqualTo(before);
			g.dispose();
		});
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

	// ----- painted section vs. selected item -----

	/** One distinct colour per item; index i is the colour of item i. */
	private static final List<Color> ITEM_COLORS = List.of(
			new Color(255, 0, 0), new Color(0, 160, 0), new Color(0, 0, 255),
			new Color(255, 255, 0), new Color(255, 0, 255));

	/**
	 * Ties the painted geometry to {@link Wheel#getSelectedItem()}: the
	 * section the tick points at must be the one the model reports. The
	 * draw loop paints sections in reverse order while the selection
	 * formula indexes forward, so an inversion between the two would paint
	 * a different label under the tick than the one returned - and every
	 * other test in the suite would still pass.
	 */
	@Test
	void paintedSectionUnderTheTickIsTheSelectedItem() throws Exception {
		final List<String> mismatches = new ArrayList<>();

		SwingUtilities.invokeAndWait(() -> {
			for (int n : new int[] {3, 4, 5}) {
				List<String> items = List.of("A", "B", "C", "D", "E").subList(0, n);
				for (double rotation : new double[] {0, 37, 90, -55, 179}) {
					Wheel wheel = new Wheel(items);
					wheel.setBounds(0, 0, 400, 400);
					wheel.setColorScheme(new ArrayList<>(ITEM_COLORS.subList(0, n)));
					wheel.setRotationAngle(rotation);
					BufferedImage image = paint(wheel);

					int selected = items.indexOf(wheel.getSelectedItem());
					// WheelRenderer hands out the colours in paint order, so
					// section i is painted with colors[(n - 1 - i) % n].
					Color expected = ITEM_COLORS.get((n - 1 - selected) % n);

					// Middle of the section the tick points at: section s
					// spans [s * delta - rotation, (s + 1) * delta - rotation]
					// in the painted image.
					double delta = 360.0 / n;
					double angleDeg = (selected + 0.5) * delta - rotation;
					int radius = (int) (wheel.getRadius() * 0.5);
					int x = 200 + (int) Math.round(radius * Math.cos(Math.toRadians(angleDeg)));
					int y = 200 - (int) Math.round(radius * Math.sin(Math.toRadians(angleDeg)));
					Color actual = new Color(image.getRGB(x, y), true);

					if (!actual.equals(expected)) {
						mismatches.add("n=" + n + " rotation=" + rotation
								+ " selected=" + wheel.getSelectedItem()
								+ " expected=" + expected + " actual=" + actual);
					}
				}
			}
		});

		assertThat(mismatches)
				.as("the section under the tick must belong to the selected item")
				.isEmpty();
	}
}
