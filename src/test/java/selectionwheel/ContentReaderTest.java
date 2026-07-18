package selectionwheel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

public class ContentReaderTest {
    @Test
    public void testImportListOfItems() throws IOException {

        List<String> result = ContentReader.importListOfItems();
        int LIMIT = 100;
        assertTrue(result.size() > 1, "The list should have at least one item");
        assertTrue(result.size() <= LIMIT, "The list should not exceed 100 items");
    }
}
