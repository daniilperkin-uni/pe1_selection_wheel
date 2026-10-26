package selectionwheel;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.List;

import org.junit.jupiter.api.Test;

class WheelRendererTest {

	@Test
	void rendersSectionsIntoImageHeadless() {
		WheelRenderer r = new WheelRenderer();
		r.font = new Font("TimesRoman", Font.PLAIN, 12);
		r.colors = List.of(Color.RED, Color.BLUE);
		WheelModel model = new WheelModel(List.of("alpha", "beta", "gamma", "delta"));

		BufferedImage img = r.render(model, 200, 200);

		assertEquals(200, img.getWidth());
		assertEquals(90, r.radius);
		assertEquals(100.0, r.center.getX(), 1e-9);
		// pixel just right of centre lies inside the first drawn section
		int argb = img.getRGB(150, 110);
		assertNotEquals(0, argb >>> 24, "section pixel should be opaque");
		assertEquals(0, img.getRGB(1, 1) >>> 24, "corner should be transparent");
	}

	// ----- UMBRELLA geometry with one or two items -----

	private static WheelRenderer umbrellaRenderer() {
		WheelRenderer r = new WheelRenderer();
		r.font = new Font("TimesRoman", Font.PLAIN, 12);
		r.colors = List.of(Color.RED, Color.BLUE);
		r.shape = Wheel.Shape.UMBRELLA;
		return r;
	}

	/** Samples the alpha at {@code angleDeg} (counter-clockwise, 0 = 3 o'clock) on a 45% radius ring. */
	private static int sample(BufferedImage img, double angleDeg) {
		double cx = img.getWidth() / 2.0;
		double cy = img.getHeight() / 2.0;
		double radius = Math.min(img.getWidth(), img.getHeight()) / 2.0 - WheelRenderer.BORDER;
		double r = radius * 0.45;
		int x = (int) Math.round(cx + r * Math.cos(Math.toRadians(angleDeg)));
		int y = (int) Math.round(cy - r * Math.sin(Math.toRadians(angleDeg)));
		return img.getRGB(x, y) >>> 24;
	}

	@Test
	void umbrellaWithOneItemPaintsTheWholeWheel() {
		// A single section is a full circle; its chord triangle degenerates
		// to a line through the centre and used to paint nothing at all.
		BufferedImage img = umbrellaRenderer().render(new WheelModel(List.of("A")), 200, 200);

		for (double angle : new double[] {0, 45, 90, 135, 180, 225, 270, 315}) {
			assertNotEquals(0, sample(img, angle),
					"UMBRELLA with 1 item must paint the section at " + angle + " degrees");
		}
	}

	@Test
	void umbrellaWithTwoItemsPaintsBothHalfDiscs() {
		// Two sections are half discs (delta = 180). The chord triangle
		// collapsed onto the diameter (dx = 2r, dy = 0), so both halves
		// stayed transparent.
		BufferedImage img = umbrellaRenderer().render(new WheelModel(List.of("A", "B")), 200, 200);

		for (double angle : new double[] {45, 135, 225, 315}) {
			assertNotEquals(0, sample(img, angle),
					"UMBRELLA with 2 items must paint the half disc at " + angle + " degrees");
		}
	}

	@Test
	void umbrellaWithThreeItemsStillPaintsTriangularSections() {
		// Guard for the delta >= 180 fallback: normal sections keep the
		// triangular umbrella look and stay painted.
		BufferedImage img = umbrellaRenderer().render(
				new WheelModel(List.of("A", "B", "C")), 200, 200);

		assertNotEquals(0, sample(img, 60), "the first section must be painted");
		assertNotEquals(0, sample(img, 180), "the second section must be painted");
		assertNotEquals(0, sample(img, 300), "the third section must be painted");
	}
}
