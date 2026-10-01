package selectionwheel;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

/**
 * Guards the info panel's layout contract: its width must not depend on the
 * currently selected item's text length. Before the fix, every selection
 * change resized the sidebar, re-laid out the frame and made the wheel jump
 * whenever the next item had a different name length.
 *
 * <p>Builds the panel directly instead of launching the window.
 */
class MainWheelInfoPanelTest {

	private static void collectLabels(Container container, List<JLabel> labels) {
		for (Component component : container.getComponents()) {
			if (component instanceof JLabel label) {
				labels.add(label);
			}
			if (component instanceof Container nested) {
				collectLabels(nested, labels);
			}
		}
	}

	private static List<JLabel> labelsIn(Container container) {
		List<JLabel> labels = new ArrayList<>();
		collectLabels(container, labels);
		return labels;
	}

	@Test
	void panelWidth_doesNotFollowTheSelectedItemText() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			MainWheel app = new MainWheel(new Random(1));
			JPanel panel = app.createInfoPanel(List.of("Cat", "Guinea Pig", "Hedgehog"));
			int widthBefore = panel.getPreferredSize().width;

			// the value labels start as "(selection)", "(angle)", "(speed)"
			List<JLabel> values = labelsIn(panel).stream()
					.filter(label -> label.getText().startsWith("("))
					.toList();
			assertThat(values).hasSize(3);
			for (JLabel value : values) {
				value.setText("an unexpectedly long selected item name");
			}

			assertThat(panel.getPreferredSize().width)
					.as("the sidebar must not resize with the label text")
					.isEqualTo(widthBefore);
		});
	}

	@Test
	void panelWidth_coversTheLongestItemName() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			MainWheel shortList = new MainWheel(new Random(1));
			MainWheel longList = new MainWheel(new Random(2));

			int narrow = shortList.createInfoPanel(List.of("A")).getPreferredSize().width;
			int wide = longList.createInfoPanel(List.of("A very long item name")).getPreferredSize().width;

			assertThat(wide)
					.as("a longer item name must widen the value column")
					.isGreaterThan(narrow);
		});
	}
}
