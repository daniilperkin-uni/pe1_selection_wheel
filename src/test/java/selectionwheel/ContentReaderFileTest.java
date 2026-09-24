package selectionwheel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;

/** Tests for loading a user-picked item file (pure I/O, no Swing). */
class ContentReaderFileTest {

    @TempDir
    Path dir;

    private Path write(String content) throws IOException {
        return Files.writeString(dir.resolve("items.txt"), content, StandardCharsets.UTF_8);
    }

    @Test
    void readsNonBlankTrimmedLinesKeepingUmlauts() throws IOException {
        Path f = write("Äpfel\r\n\n  Brötchen  \nKäse\n");
        assertThat(ContentReader.importFromFile(f)).containsExactly("Äpfel", "Brötchen", "Käse");
    }

    @Test
    void rejectsEmptyFile() throws IOException {
        Path f = write("\n  \n");
        assertThatIllegalArgumentException().isThrownBy(() -> ContentReader.importFromFile(f));
    }

    @Test
    void rejectsTooManyItems() throws IOException {
        Path f = write(String.join("\n", Collections.nCopies(ContentReader.ITEM_LIMIT + 1, "x")));
        assertThatIllegalArgumentException().isThrownBy(() -> ContentReader.importFromFile(f));
    }

    @Test
    void missingFileThrowsIOException() {
        assertThatIOException().isThrownBy(() -> ContentReader.importFromFile(dir.resolve("nope.txt")));
    }

    @Test
    void rejectsNullPath() {
        assertThatIllegalArgumentException().isThrownBy(() -> ContentReader.importFromFile(null));
    }
}
