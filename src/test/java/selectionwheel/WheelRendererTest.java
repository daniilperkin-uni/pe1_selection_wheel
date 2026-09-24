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
}
