package demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class Store {
    private final Path file = Path.of("/data/entries.jsonl");
    private final ObjectMapper json = new ObjectMapper();

    public static void validate(String email, String comment) {
        if (email.length() > 254 || !email.matches("[\\x21-\\x7e]+") ||
            !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") ||
            comment.isBlank() ||
            comment.getBytes(StandardCharsets.UTF_8).length > 2000) {
            throw new IllegalArgumentException(
                "Use a valid email and a comment of 1-2000 UTF-8 bytes.");
        }
    }
    public synchronized void add(String email, String comment) throws Exception {
        validate(email, comment);
        Files.createDirectories(file.getParent());
        String line = json.writeValueAsString(Map.of(
            "email", email, "comment", comment,
            "createdAt", Instant.now().toString())) + "\n";
        Files.writeString(file, line, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    public synchronized List<Map<String, String>> list() throws Exception {
        List<Map<String, String>> items = new ArrayList<>();
        if (!Files.exists(file)) return items;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            var row = json.readTree(line);
            items.add(Map.of("comment", row.get("comment").asText(),
                "createdAt", row.get("createdAt").asText()));
        }
        Collections.reverse(items);
        return new ArrayList<>(items.subList(0, Math.min(100, items.size())));
    }
}
