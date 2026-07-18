package selectionwheel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the list of selectable items from a bundled resource file.
 *
 * <p>The default resource is {@code itemlist.txt} shipped on the classpath.
 * Each non-empty line of the file becomes one item. Blank lines are skipped
 * so the file can use trailing newlines without affecting the result.
 *
 * <p>Validation mirrors the invariants enforced by {@link Wheel}:
 * <ul>
 *   <li>The list must be non-empty (otherwise the wheel has no sections).</li>
 *   <li>The list size must not exceed {@value #ITEM_LIMIT}
 *       (matches {@code Wheel.LIMIT}).</li>
 * </ul>
 * Violations raise an {@link IllegalArgumentException}.
 */
public final class ContentReader {

    /** Maximum number of items supported by the wheel. Must match {@code Wheel.LIMIT}. */
    public static final int ITEM_LIMIT = 100;

    /** Name of the bundled resource loaded by {@link #importListOfItems()}. */
    public static final String DEFAULT_RESOURCE = "itemlist.txt";

    private ContentReader() {
        // Utility class - no instances.
    }

    /**
     * Loads the default item list from the classpath resource
     * {@value #DEFAULT_RESOURCE}.
     *
     * @return a non-empty, at most {@value #ITEM_LIMIT}-element list of items
     * @throws IOException              if the resource cannot be read
     * @throws IllegalArgumentException if the list is empty or exceeds
     *                                  {@value #ITEM_LIMIT} items
     */
    public static ArrayList<String> importListOfItems() throws IOException {
        return importFromResource(DEFAULT_RESOURCE);
    }

    /**
     * Loads an item list from an arbitrary classpath resource name.
     *
     * @param resource name of a resource resolvable by
     *                 {@link ClassLoader#getResourceAsStream(String)}
     * @return a validated, non-empty list of items
     * @throws IOException              if the resource is missing or unreadable
     * @throws IllegalArgumentException if the list is empty or exceeds
     *                                  {@value #ITEM_LIMIT} items
     */
    public static ArrayList<String> importFromResource(String resource) throws IOException {
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException("resource name must not be null or blank");
        }
        ClassLoader loader = ContentReader.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Resource not found on classpath: " + resource);
            }
            return parse(in);
        }
    }

    /**
     * Parses a UTF-8 input stream into a validated list of items.
     *
     * @param in the stream to read; will be fully consumed
     * @return a validated, non-empty list of items
     * @throws IOException              if reading the stream fails
     * @throws IllegalArgumentException if the parsed list is empty or
     *                                  exceeds {@value #ITEM_LIMIT} items
     */
    static ArrayList<String> parse(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        String content = new String(bytes, StandardCharsets.UTF_8);
        String[] lines = content.split("\\r?\\n");
        ArrayList<String> items = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Item list is empty");
        }
        if (items.size() > ITEM_LIMIT) {
            throw new IllegalArgumentException(
                    "Item list size " + items.size()
                            + " exceeds limit " + ITEM_LIMIT);
        }
        return items;
    }
}
