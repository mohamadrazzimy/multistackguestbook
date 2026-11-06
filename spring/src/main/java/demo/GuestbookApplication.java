package demo;

import java.util.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@RestController
public class GuestbookApplication {
    private final Store store = new Store();
    public static void main(String[] args) {
        SpringApplication.run(GuestbookApplication.class, args);
    }
    @GetMapping("/api/entries")
    public ResponseEntity<?> list() throws Exception {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(store.list());
    }
    @PostMapping(value = "/api/entries",
                 consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> add(
        @RequestParam(defaultValue = "") String email,
        @RequestParam(defaultValue = "") String comment) throws Exception {
        store.add(email.trim(), comment.trim());
        return ResponseEntity.status(201).body(Map.of("ok", true));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
    @ExceptionHandler(java.io.IOException.class)
    public ResponseEntity<?> storage(java.io.IOException e) {
        e.printStackTrace();
        return ResponseEntity.status(500).body(Map.of("error", "Storage unavailable."));
    }
}
