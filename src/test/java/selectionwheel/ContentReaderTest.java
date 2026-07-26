package selectionwheel;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ContentReader}.
 *
 * <p>Covers the happy path (default resource loads within the item limit),
 * the error paths (missing resource, empty list, oversized list), and
 * verifies actual item content rather than just the list size.
 *
 * <p>Uses AssertJ ({@code assertThat}) for consistency with the other
 * test classes in this package. The package-private
 * {@link ContentReader#parse(InputStream)} method is used directly for
 * the empty-list and oversized-list cases so they do not depend on a
 * classpath resource being present.
 */
class ContentReaderTest {

	// ----- happy path -----

	@Test
	void importListOfItems_loadsNonEmptyListWithinLimit() throws IOException {
		List<String> result = ContentReader.importListOfItems();

		assertThat(result).isNotEmpty();
		assertThat(result).hasSizeLessThanOrEqualTo(ContentReader.ITEM_LIMIT);
	}

	@Test
	void importListOfItems_verifiesActualContent() throws IOException {
		List<String> result = ContentReader.importListOfItems();

		// The bundled itemlist.txt ships with these items (see
		// src/main/resources/itemlist.txt). Asserting on actual content
		// catches silent regressions where the file is swapped out or
		// the parser drops lines.
		assertThat(result).containsExactly(
				"Cat", "Dog", "Rabbit", "Hamster", "Parrot", "Guinea Pig",
				"Goldfish", "Turtle", "Rat", "Crab", "Fish", "Frog",
				"Dragon", "Chinchilla", "Hedgehog");
		assertThat(result).hasSize(15);
		assertThat(result.get(0)).isEqualTo("Cat");
		assertThat(result).contains("Hamster", "Hedgehog");
		assertThat(result).doesNotContain(null, "", " ");
	}

	// ----- error paths -----

	@Test
	void importFromResource_missingResource_throwsIOException() {
		assertThatThrownBy(() -> ContentReader.importFromResource("does-not-exist.txt"))
				.isInstanceOf(IOException.class)
				.hasMessageContaining("Resource not found on classpath")
				.hasMessageContaining("does-not-exist.txt");
	}

	@Test
	void importFromResource_blankName_throwsIllegalArgumentException() {
		assertThatThrownBy(() -> ContentReader.importFromResource(""))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("resource name must not be null or blank");
		assertThatThrownBy(() -> ContentReader.importFromResource(null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void parse_emptyInput_throwsIllegalArgumentException() throws IOException {
		try (InputStream empty = new ByteArrayInputStream(new byte[0])) {
			assertThatThrownBy(() -> ContentReader.parse(empty))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("Item list is empty");
		}
	}

	@Test
	void parse_onlyBlankLines_throwsIllegalArgumentException() throws IOException {
		try (InputStream blanks = new ByteArrayInputStream(
				"\n  \n\t\n".getBytes(StandardCharsets.UTF_8))) {
			assertThatThrownBy(() -> ContentReader.parse(blanks))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("Item list is empty");
		}
	}

	@Test
	void parse_oversizedList_throwsIllegalArgumentException() throws IOException {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < ContentReader.ITEM_LIMIT + 1; i++) {
			sb.append("Item").append(i).append('\n');
		}
		try (InputStream oversized = new ByteArrayInputStream(
				sb.toString().getBytes(StandardCharsets.UTF_8))) {
			assertThatThrownBy(() -> ContentReader.parse(oversized))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("exceeds limit")
					.hasMessageContaining(String.valueOf(ContentReader.ITEM_LIMIT + 1));
		}
	}

	@Test
	void parse_atExactlyLimit_isAccepted() throws IOException {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < ContentReader.ITEM_LIMIT; i++) {
			sb.append("Item").append(i).append('\n');
		}
		ArrayList<String> items;
		try (InputStream atLimit = new ByteArrayInputStream(
				sb.toString().getBytes(StandardCharsets.UTF_8))) {
			items = ContentReader.parse(atLimit);
		}
		assertThat(items).hasSize(ContentReader.ITEM_LIMIT);
		assertThat(items.get(0)).isEqualTo("Item0");
		assertThat(items.get(ContentReader.ITEM_LIMIT - 1)).isEqualTo("Item99");
	}

	@Test
	void parse_trimsWhitespaceAndSkipsBlankLines() throws IOException {
		try (InputStream in = new ByteArrayInputStream(
				"  A  \n\n\t\nB\n".getBytes(StandardCharsets.UTF_8))) {
			ArrayList<String> items = ContentReader.parse(in);
			assertThat(items).containsExactly("A", "B");
		}
	}
}
