package demo;

import com.sun.net.httpserver.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Main {
    static final Store store = new Store();
    static final ObjectMapper json = new ObjectMapper();
    static void send(HttpExchange x, int code, Object body) throws Exception {
        byte[] bytes = json.writeValueAsBytes(body);
        x.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        x.getResponseHeaders().set("Cache-Control", "no-store");
        x.sendResponseHeaders(code, bytes.length);
        x.getResponseBody().write(bytes);
    }
    public static void main(String[] args) throws Exception {
        byte[] html = Files.readAllBytes(Path.of("public/index.html"));
        var server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", x -> {
            try {
                String path = x.getRequestURI().getPath();
                String method = x.getRequestMethod();
                if (path.equals("/") && method.equals("GET")) {
                    x.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    x.sendResponseHeaders(200, html.length);
                    x.getResponseBody().write(html);
                } else if (!path.equals("/api/entries")) {
                    send(x, 404, Map.of("error", "Not found."));
                } else if (method.equals("GET")) {
                    send(x, 200, store.list());
                } else if (method.equals("POST")) {
                    String type = x.getRequestHeaders().getFirst("Content-Type");
                    if (type == null || !type.toLowerCase(Locale.ROOT)
                            .startsWith("application/x-www-form-urlencoded")) {
                        send(x, 415, Map.of("error", "Use a URL-encoded form."));
                        return;
                    }
                    byte[] raw = x.getRequestBody().readNBytes(16385);
                    if (raw.length > 16384) {
                        send(x, 413, Map.of("error", "Request too large."));
                        return;
                    }
                    Map<String, String> form = new HashMap<>();
                    for (String pair : new String(raw, StandardCharsets.UTF_8).split("&")) {
                        String[] parts = pair.split("=", 2);
                        if (parts.length == 2) form.put(
                            URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                            URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
                    }
                    store.add(form.getOrDefault("email", "").trim(),
                              form.getOrDefault("comment", "").trim());
                    send(x, 201, Map.of("ok", true));
                } else send(x, 405, Map.of("error", "Method not allowed."));
            } catch (IllegalArgumentException e) {
                try { send(x, 400, Map.of("error", "Invalid email or comment.")); }
                catch (Exception ignored) { }
            } catch (Exception e) {
                e.printStackTrace();
                try { send(x, 500, Map.of("error", "Storage unavailable.")); }
                catch (Exception ignored) { }
            } finally { x.close(); }
        });
        server.start();
    }
}
