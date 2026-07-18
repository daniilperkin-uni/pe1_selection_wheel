package SelectionWheel;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

public class ContentReaderTest {
    @Test
    public void testImportListOfItems() throws IOException {

        List<String> result = ContentReader.importListOfItems();
        int LIMIT = 100;
        assertTrue("The list should have at least one item", result.size() > 1);
        assertTrue("The list should not exceed 100 items", result.size() <= LIMIT);
    }
}
