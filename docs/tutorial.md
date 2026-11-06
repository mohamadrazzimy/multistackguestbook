# One Guestbook, Seven Stacks
## Collecting emails and comments with PHP, Java, ASP.NET Core, Node.js and Python in Docker

A step-by-step walkthrough for Windows 11, Docker Desktop and PowerShell 7.

Every web stack can serve a page. A guestbook takes the exercise one step further: the browser sends an email address and a comment, the server validates and saves both, and the page displays the latest comments.

This tutorial builds the same guestbook with seven backends: PHP with Apache, PHP with Nginx, plain Java, Spring Boot, ASP.NET Core, Node.js and Python. All runtimes and build tools run inside Docker. You do not need to install them on Windows.

Visitors can submit an email address and a comment. Comments appear immediately, newest first; email addresses remain private and are never included in the public API. Refreshing the page or recreating a container preserves entries because each stack uses its own Docker volume.

This is a local learning project. Each stack has an independent guestbook. Run one or all seven; submitting on port 8081 does not create an entry on port 8082.

Validation note: the tutorial's code and file layout were reviewed and selected checks were run during preparation. The complete seven-container build and runtime matrix was not executed in this environment; run the verification steps on Docker Desktop.

## What you need

- Windows 11 with Docker Desktop running in Linux-container mode.
- PowerShell 7 and a text editor such as VS Code.
- Internet access to download container images and dependencies.
- A working directory, used here as `C:\Users\User\myguestbook`.

The examples use explicit teaching baselines, not a claim that these are the latest versions. Review supported dependency versions before deploying a public application.

## The plan

| Stack | Folder | Local URL | Internal port |
| --- | --- | --- | --- |
| PHP + Apache | php-apache | http://localhost:8081 | 80 |
| PHP + Nginx / PHP-FPM | php-nginx | http://localhost:8082 | 80 (Nginx) |
| Java, plain HTTP server | java | http://localhost:8083 | 8080 |
| Java Spring Boot | spring | http://localhost:8084 | 8080 |
| ASP.NET Core | aspnet | http://localhost:8085 | 8080 |
| Node.js / Express | node | http://localhost:8086 | 3000 |
| Python / Flask + Gunicorn | python | http://localhost:8087 | 8000 |

All seven implement the same small API:

| Request | Result |
| --- | --- |
| `GET /` | Guestbook HTML page |
| `GET /api/entries` | JSON array of the latest 100 comments: `comment` and `createdAt` only |
| `POST /api/entries` | URL-encoded `email` and `comment`; returns HTTP 201 and `{"ok":true}` |
| Invalid submission | HTTP 400 and a JSON `error` message |

Both fields are required. To keep validation consistent across languages, this lesson accepts ASCII email addresses, up to 254 characters, with a simple `name@host.tld` shape. This is a format check, not proof that an address exists. Comments are trimmed, required, and limited to 2,000 UTF-8 bytes. The browser checks these rules for convenience, and every backend checks them again.

Entries are saved as newline-delimited JSON in `/data/entries.jsonl`, outside the web root. One line contains `email`, `comment` and a UTC `createdAt` timestamp. This keeps the storage example small. PHP uses an OS file lock; the other implementations serialize access within one application process. Run exactly one instance per stack and keep Python at one worker. This storage design is not intended for multiple replicas or crash-safe transactional writes; use a database for those requirements.

## Step 0: Create the project

Run this in PowerShell 7:

```powershell
docker version
docker compose version
$root = 'C:\Users\User\myguestbook'
New-Item -ItemType Directory -Force $root | Out-Null
Set-Location $root
'shared','php-apache','php-nginx','java','spring','aspnet','node','python' |
    ForEach-Object { New-Item -ItemType Directory -Force $_ | Out-Null }
```

Use your editor to create the files shown below. Paths are relative to `$root` unless stated otherwise. Save text files as UTF-8 without a BOM, especially PHP files. This avoids placing bytes before HTTP response headers. If using PowerShell here-strings to write files, use a single-quoted here-string and `Set-Content -Encoding utf8`; its closing `'@` must start at the beginning of the line.

## Step 1: Build the shared guestbook page

Create `shared/index.html`. All seven stacks use this same file.

```html
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Guestbook</title>
  <style>
    * { box-sizing: border-box; }
    body { margin: 0; background: #f3f6fa; color: #17243a;
      font: 16px/1.6 system-ui, sans-serif; }
    header, footer { background: #12345a; color: white; padding: 24px; }
    header h1, header p { margin: 0; }
    main { max-width: 1000px; margin: 28px auto; padding: 0 20px;
      display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
    section { background: white; padding: 24px; border-radius: 12px; }
    h2 { margin-top: 0; }
    label { display: block; font-weight: 600; margin-top: 16px; }
    input, textarea, button { font: inherit; }
    input, textarea { width: 100%; padding: 10px; border: 1px solid #8090a5;
      border-radius: 6px; }
    textarea { min-height: 150px; resize: vertical; }
    button { background: #12345a; color: white; border: 0;
      border-radius: 6px; padding: 10px 18px; margin-top: 16px; cursor: pointer; }
    button:disabled { opacity: .6; cursor: wait; }
    small, time { color: #536278; }
    article { border-top: 1px solid #dce3ed; padding: 16px 0; }
    article p { white-space: pre-wrap; overflow-wrap: anywhere; }
    #status { min-height: 1.6em; }
    footer { text-align: center; }
    @media (max-width: 700px) { main { grid-template-columns: 1fr; } }
  </style>
</head>
<body>
  <header><h1>Guestbook</h1><p>Thanks for visiting. Leave us a comment.</p></header>
  <main>
    <section aria-labelledby="form-title">
      <h2 id="form-title">Sign the guestbook</h2>
      <form id="guestbook">
        <label for="email">Email address</label>
        <input id="email" name="email" type="email" required maxlength="254"
          autocomplete="email" aria-describedby="privacy">
        <small id="privacy">We save your email, but do not display it publicly.</small>
        <label for="comment">Comment</label>
        <textarea id="comment" name="comment" required maxlength="2000"
          aria-describedby="comment-help"></textarea>
        <small id="comment-help">Your comment will be public. Maximum 2,000 UTF-8 bytes.</small>
        <button type="submit">Submit comment</button>
      </form>
      <p id="status" role="status" aria-live="polite"></p>
    </section>
    <section aria-labelledby="entries-title">
      <h2 id="entries-title">Recent comments</h2>
      <div id="entries" aria-live="polite">Loading comments...</div>
    </section>
  </main>
  <footer>Guestbook demo</footer>
  <script>
    const form = document.querySelector('#guestbook');
    const status = document.querySelector('#status');
    const entries = document.querySelector('#entries');
    async function loadEntries() {
      const response = await fetch('/api/entries', { cache: 'no-store' });
      if (!response.ok) throw new Error('Could not load comments.');
      const items = await response.json();
      entries.replaceChildren();
      if (!items.length) entries.textContent = 'Be the first to leave a comment.';
      for (const item of items) {
        const article = document.createElement('article');
        const time = document.createElement('time');
        time.dateTime = item.createdAt;
        time.textContent = new Date(item.createdAt).toLocaleString();
        const p = document.createElement('p');
        p.textContent = item.comment;
        article.append(time, p);
        entries.append(article);
      }
    }
    form.addEventListener('submit', async event => {
      event.preventDefault();
      const email = form.elements.email.value.trim();
      const comment = form.elements.comment.value.trim();
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ||
          !/^[\x21-\x7e]+$/.test(email) || email.length > 254) {
        status.textContent = 'Enter a valid ASCII email address.';
        return;
      }
      if (!comment || new TextEncoder().encode(comment).length > 2000) {
        status.textContent = 'Enter a comment of 1 to 2,000 UTF-8 bytes.';
        return;
      }
      const button = form.querySelector('button');
      button.disabled = true;
      status.textContent = 'Saving...';
      try {
        const response = await fetch('/api/entries', {
          method: 'POST',
          body: new URLSearchParams({ email, comment })
        });
        const result = await response.json();
        if (!response.ok) throw new Error(result.error || 'Could not save comment.');
        form.reset();
        status.textContent = 'Thank you! Your comment has been saved.';
        try { await loadEntries(); }
        catch { status.textContent += ' Refresh the page to reload comments.'; }
      } catch (error) {
        status.textContent = error.message;
      } finally { button.disabled = false; }
    });
    loadEntries().catch(error => { entries.textContent = error.message; });
  </script>
</body>
</html>
```

The page sends requests back to its own origin, so no CORS configuration is needed. It creates text nodes with `textContent` rather than inserting submitted HTML. A comment containing `<script>` is displayed as text. The initial load, empty state, submission success and failures all have visible messages. JavaScript is required for this version.

## Step 2: PHP with Apache

Create `shared/index.php`. Both PHP variants use this handler and the shared HTML file.

```php
<?php
declare(strict_types=1);
$path = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
$method = $_SERVER['REQUEST_METHOD'];
if ($path === '/' && $method === 'GET') {
    header('Content-Type: text/html; charset=utf-8');
    readfile(__DIR__ . '/index.html');
    exit;
}
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
function reply(int $code, array $body): never {
    http_response_code($code);
    echo json_encode($body, JSON_THROW_ON_ERROR);
    exit;
}
if ($path !== '/api/entries') reply(404, ['error' => 'Not found.']);
if (!in_array($method, ['GET', 'POST'], true)) {
    reply(405, ['error' => 'Method not allowed.']);
}
$email = $comment = '';
if ($method === 'POST') {
    $type = strtolower(explode(';', $_SERVER['CONTENT_TYPE'] ?? '')[0]);
    if ($type !== 'application/x-www-form-urlencoded') {
        reply(415, ['error' => 'Use a URL-encoded form.']);
    }
    if (!is_string($_POST['email'] ?? null) ||
        !is_string($_POST['comment'] ?? null)) {
        reply(400, ['error' => 'Email and comment are required.']);
    }
    $email = trim($_POST['email']);
    $comment = trim($_POST['comment']);
    if (strlen($email) > 254 || !preg_match('/^[\x21-\x7e]+$/D', $email) ||
        !preg_match('/^[^\s@]+@[^\s@]+\.[^\s@]+$/D', $email) ||
        $comment === '' || strlen($comment) > 2000 ||
        !preg_match('//u', $comment)) {
        reply(400, ['error' => 'Use a valid email and a comment of 1-2000 UTF-8 bytes.']);
    }
}
try {
    $file = fopen('/data/entries.jsonl', 'c+');
    if ($file === false || !flock($file, LOCK_EX)) {
        throw new RuntimeException('Storage unavailable');
    }
    if ($method === 'GET') {
        $items = [];
        while (($line = fgets($file)) !== false) {
            $row = json_decode($line, true, 512, JSON_THROW_ON_ERROR);
            $items[] = ['comment' => $row['comment'], 'createdAt' => $row['createdAt']];
        }
        $body = array_reverse(array_slice($items, -100));
        $code = 200;
    } else {
        $row = ['email' => $email, 'comment' => $comment,
                'createdAt' => gmdate('Y-m-d\TH:i:s\Z')];
        $line = json_encode($row, JSON_THROW_ON_ERROR) . "\n";
        fseek($file, 0, SEEK_END);
        if (fwrite($file, $line) !== strlen($line) || !fflush($file)) {
            throw new RuntimeException('Write failed');
        }
        $body = ['ok' => true];
        $code = 201;
    }
    flock($file, LOCK_UN);
    fclose($file);
    reply($code, $body);
} catch (Throwable $error) {
    error_log($error->getMessage());
    reply(500, ['error' => 'Storage unavailable.']);
}
```

Create `php-apache/Dockerfile`:

```dockerfile
FROM php:8.3-apache
COPY index.php index.html /var/www/html/
RUN mkdir -p /data && chown www-data:www-data /data \
    && printf 'FallbackResource /index.php\n' > /var/www/html/.htaccess \
    && printf '<Directory /var/www/html>\nAllowOverride All\n</Directory>\n' \
       > /etc/apache2/conf-available/guestbook.conf \
    && a2enconf guestbook
EXPOSE 80
```

Copy the shared files, build and run:

```powershell
Set-Location "$root\php-apache"
Copy-Item ..\shared\index.html, ..\shared\index.php .
docker build -t guestbook-php-apache .
docker run -d --name guestbook-php-apache -p 127.0.0.1:8081:80 `
    -v guestbook-php-apache-data:/data guestbook-php-apache
Start-Process http://localhost:8081
```

Apache routes `/api/entries` to PHP through `FallbackResource`. `/data` is not publicly served. The named volume is initialized with the image directory's ownership so the PHP user can write to it.

## Step 3: PHP with Nginx and PHP-FPM

Nginx serves the page and forwards API requests to a separate PHP-FPM container. Reuse `shared/index.php`; do not write a second PHP backend.

Create `php-nginx/Dockerfile`:

```dockerfile
FROM php:8.3-fpm-alpine
COPY index.php index.html /var/www/html/
RUN mkdir -p /data && chown www-data:www-data /data
```

Create `php-nginx/default.conf`:

```nginx
server {
    listen 80;
    root /var/www/html;
    client_max_body_size 16k;
    location = / {
        try_files /index.html =404;
    }
    location = /api/entries {
        include fastcgi_params;
        fastcgi_param SCRIPT_FILENAME /var/www/html/index.php;
        fastcgi_pass php:9000;
    }
    location / { return 404; }
}
```

Create `php-nginx/compose.yaml`:

```yaml
services:
  php:
    build: .
    volumes:
      - data:/data
  nginx:
    image: nginx:alpine
    ports:
      - "127.0.0.1:8082:80"
    volumes:
      - ./index.html:/var/www/html/index.html:ro
      - ./default.conf:/etc/nginx/conf.d/default.conf:ro
    depends_on:
      - php
volumes:
  data:
```

Build and start:

```powershell
Set-Location "$root\php-nginx"
Copy-Item ..\shared\index.html, ..\shared\index.php .
docker compose up -d --build
Start-Process http://localhost:8082
```

PHP-FPM is reachable only inside the Compose network. Nginx has no mount for `/data`. Updating the HTML takes a refresh; updating PHP requires `docker compose up -d --build`. `depends_on` starts PHP first but does not guarantee readiness; retry after a moment if the first request returns 502.

## Step 4: Java without a web framework

The JDK includes `HttpServer`. We add Jackson for JSON serialization, so Maven runs in the build image. This is still a plain Java HTTP server, without Spring. A shared storage class will also be used by the Spring variant.

Create `shared/Store.java`:

```java
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
```

Create `java/pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId><artifactId>guestbook</artifactId><version>1.0</version>
  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId><version>2.18.2</version>
    </dependency>
  </dependencies>
  <build><plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId><version>3.13.0</version>
    </plugin>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-dependency-plugin</artifactId><version>3.8.1</version>
    </plugin>
  </plugins></build>
</project>
```

Create `java/src/main/java/demo/Main.java`:

```java
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
```

Create `java/Dockerfile`:

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src/ src/
RUN mvn -q package dependency:copy-dependencies -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/classes/ classes/
COPY --from=build /src/target/dependency/ lib/
COPY public/ public/
RUN mkdir -p /data
EXPOSE 8080
CMD ["java", "-cp", "classes:lib/*", "demo.Main"]
```

Before saving `Main.java`, create its parent directory. Then copy the common files, build and run:

```powershell
Set-Location "$root\java"
New-Item -ItemType Directory -Force src\main\java\demo, public | Out-Null
# Save Main.java in src\main\java\demo before continuing.
Copy-Item ..\shared\Store.java src\main\java\demo\Store.java
Copy-Item ..\shared\index.html public\index.html
docker build -t guestbook-java .
docker run -d --name guestbook-java -p 127.0.0.1:8083:8080 `
    -v guestbook-java-data:/data guestbook-java
Start-Process http://localhost:8083
```

The route handler decodes URL-encoded form fields, then calls the same validation and storage methods used by Spring. Returning JSON through Jackson avoids hand-built JSON strings.

## Step 5: Java Spring Boot

Spring supplies routing, form parameter binding and JSON responses. We keep `Store.java` so both Java examples have the same storage behavior.

Create `spring/pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId><version>3.5.0</version>
  </parent>
  <groupId>demo</groupId><artifactId>guestbook</artifactId><version>1.0</version>
  <properties><java.version>21</java.version></properties>
  <dependencies><dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency></dependencies>
  <build><plugins><plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
  </plugin></plugins></build>
</project>
```

Create `spring/src/main/java/demo/GuestbookApplication.java`:

```java
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
```

Create `spring/Dockerfile`:

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src/ src/
RUN mvn -q package -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/guestbook-1.0.jar app.jar
RUN mkdir -p /data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Prepare directories before saving the Java source, then build:

```powershell
Set-Location "$root\spring"
New-Item -ItemType Directory -Force src\main\java\demo, `
    src\main\resources\static | Out-Null
# Save GuestbookApplication.java in src\main\java\demo before continuing.
Copy-Item ..\shared\Store.java src\main\java\demo\Store.java
Copy-Item ..\shared\index.html src\main\resources\static\index.html
docker build -t guestbook-spring .
docker run -d --name guestbook-spring -p 127.0.0.1:8084:8080 `
    -v guestbook-spring-data:/data guestbook-spring
Start-Process http://localhost:8084
```

Spring serves `static/index.html` as the home page. Give it time to start; inspect `docker logs guestbook-spring` if it is not ready.

## Step 6: ASP.NET Core

Create `aspnet/Guestbook.csproj`:

```xml
<Project Sdk="Microsoft.NET.Sdk.Web">
  <PropertyGroup>
    <TargetFramework>net10.0</TargetFramework>
    <Nullable>enable</Nullable>
    <ImplicitUsings>enable</ImplicitUsings>
  </PropertyGroup>
</Project>
```

Create `aspnet/Program.cs`:

```csharp
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

var builder = WebApplication.CreateBuilder(args);
var app = builder.Build();
var gate = new object();
var file = "/data/entries.jsonl";
Directory.CreateDirectory("/data");
app.UseDefaultFiles();
app.UseStaticFiles();
app.MapGet("/api/entries", (HttpContext context) => {
    context.Response.Headers.CacheControl = "no-store";
    lock (gate) {
        var rows = File.Exists(file)
            ? File.ReadAllLines(file).Select(line =>
                JsonSerializer.Deserialize<Entry>(line)!).ToList()
            : new List<Entry>();
        return Results.Json(rows.AsEnumerable().Reverse().Take(100)
            .Select(row => new { comment = row.Comment, createdAt = row.CreatedAt }));
    }
});
app.MapPost("/api/entries", async (HttpRequest request) => {
    if (!(request.ContentType ?? "").StartsWith(
            "application/x-www-form-urlencoded", StringComparison.OrdinalIgnoreCase))
        return Results.Json(new { error = "Use a URL-encoded form." }, statusCode: 415);
    var form = await request.ReadFormAsync();
    var email = form["email"].ToString().Trim();
    var comment = form["comment"].ToString().Trim();
    if (email.Length > 254 || !Regex.IsMatch(email, @"\A[\x21-\x7e]+\z") ||
        !Regex.IsMatch(email, @"\A[^\s@]+@[^\s@]+\.[^\s@]+\z") ||
        comment.Length == 0 || Encoding.UTF8.GetByteCount(comment) > 2000)
        return Results.BadRequest(new {
            error = "Use a valid email and a comment of 1-2000 UTF-8 bytes." });
    try {
        lock (gate) {
            var row = new Entry(email, comment, DateTimeOffset.UtcNow.ToString("O"));
            File.AppendAllText(file, JsonSerializer.Serialize(row) + "\n");
        }
        return Results.Json(new { ok = true }, statusCode: 201);
    } catch (IOException error) {
        app.Logger.LogError(error, "Guestbook write failed");
        return Results.Json(new { error = "Storage unavailable." }, statusCode: 500);
    }
});
app.Run();
record Entry(string Email, string Comment, string CreatedAt);
```

This handler reads the form explicitly from `HttpRequest`. It does not use inferred `IFormCollection` binding. The stored C# property names are PascalCase, while the public projection uses the common `comment` and `createdAt` names; the files are private to each stack.

Create `aspnet/Dockerfile`:

```dockerfile
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src
COPY Guestbook.csproj .
RUN dotnet restore
COPY . .
RUN dotnet publish -c Release -o /out
FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /out .
RUN mkdir -p /data
ENV ASPNETCORE_URLS=http://+:8080
EXPOSE 8080
ENTRYPOINT ["dotnet", "Guestbook.dll"]
```

Build and run:

```powershell
Set-Location "$root\aspnet"
New-Item -ItemType Directory -Force wwwroot | Out-Null
Copy-Item ..\shared\index.html wwwroot\index.html
docker build -t guestbook-aspnet .
docker run -d --name guestbook-aspnet -p 127.0.0.1:8085:8080 `
    -v guestbook-aspnet-data:/data guestbook-aspnet
Start-Process http://localhost:8085
```

## Step 7: Node.js with Express

Create `node/package.json`:

```json
{
  "name": "guestbook",
  "version": "1.0.0",
  "private": true,
  "scripts": { "start": "node server.js" },
  "dependencies": { "express": "4.21.2" }
}
```

Create `node/server.js`:

```javascript
const express = require('express');
const fs = require('node:fs');
const path = require('node:path');
const app = express();
const file = '/data/entries.jsonl';
fs.mkdirSync('/data', { recursive: true });
app.use(express.urlencoded({ extended: false, limit: '16kb' }));
app.use(express.static(path.join(__dirname, 'public')));
app.get('/api/entries', (req, res, next) => {
  try {
    const rows = fs.existsSync(file)
      ? fs.readFileSync(file, 'utf8').split('\n').filter(Boolean).map(JSON.parse)
      : [];
    res.set('Cache-Control', 'no-store').json(rows.slice(-100).reverse()
      .map(({ comment, createdAt }) => ({ comment, createdAt })));
  } catch (error) { next(error); }
});
app.post('/api/entries', (req, res, next) => {
  if (!req.is('application/x-www-form-urlencoded'))
    return res.status(415).json({ error: 'Use a URL-encoded form.' });
  const email = typeof req.body.email === 'string' ? req.body.email.trim() : '';
  const comment = typeof req.body.comment === 'string' ? req.body.comment.trim() : '';
  if (email.length > 254 || !/^[\x21-\x7e]+$/.test(email) ||
      !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ||
      !comment || Buffer.byteLength(comment, 'utf8') > 2000)
    return res.status(400).json({
      error: 'Use a valid email and a comment of 1-2000 UTF-8 bytes.'
    });
  try {
    fs.appendFileSync(file, JSON.stringify({
      email, comment, createdAt: new Date().toISOString()
    }) + '\n');
    res.status(201).json({ ok: true });
  } catch (error) { next(error); }
});
app.use('/api/entries', (req, res) => {
  res.status(405).json({ error: 'Method not allowed.' });
});
app.use((error, req, res, next) => {
  console.error(error.message);
  const status = error.status === 413 ? 413 : error.status === 400 ? 400 : 500;
  res.status(status).json({ error: status === 413 ? 'Request too large.' :
    status === 400 ? 'Invalid form.' : 'Storage unavailable.' });
});
app.listen(3000, '0.0.0.0', () => console.log('Guestbook on port 3000'));
```

The synchronous file operations keep the read/write sequence serialized in this one Node process. They also block the event loop, which is acceptable for this small exercise but a reason to switch to a database for a busy site.

Create `node/Dockerfile`:

```dockerfile
FROM node:22-alpine
WORKDIR /app
COPY package.json .
RUN npm install --omit=dev
COPY server.js .
COPY public/ public/
RUN mkdir -p /data
EXPOSE 3000
CMD ["node", "server.js"]
```

Build and run:

```powershell
Set-Location "$root\node"
New-Item -ItemType Directory -Force public | Out-Null
Copy-Item ..\shared\index.html public\index.html
docker build -t guestbook-node .
docker run -d --name guestbook-node -p 127.0.0.1:8086:3000 `
    -v guestbook-node-data:/data guestbook-node
Start-Process http://localhost:8086
```

For reproducible dependency resolution in a maintained project, commit a generated `package-lock.json` and change the build to `COPY package*.json ./` followed by `npm ci --omit=dev`.

## Step 8: Python with Flask and Gunicorn

Create `python/requirements.txt`:

```text
Flask==3.1.0
gunicorn==23.0.0
```

Create `python/app.py`:

```python
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from threading import Lock
from flask import Flask, jsonify, request

app = Flask(__name__, static_folder="public", static_url_path="")
app.config["MAX_CONTENT_LENGTH"] = 16 * 1024
file = Path("/data/entries.jsonl")
file.parent.mkdir(parents=True, exist_ok=True)
gate = Lock()

@app.get("/")
def home():
    return app.send_static_file("index.html")

@app.get("/api/entries")
def entries():
    with gate:
        rows = [json.loads(line) for line in file.read_text(encoding="utf-8").splitlines()] \
            if file.exists() else []
    public = [{"comment": row["comment"], "createdAt": row["createdAt"]}
              for row in rows[-100:][::-1]]
    response = jsonify(public)
    response.headers["Cache-Control"] = "no-store"
    return response

@app.post("/api/entries")
def add():
    if request.mimetype != "application/x-www-form-urlencoded":
        return jsonify(error="Use a URL-encoded form."), 415
    email = request.form.get("email", "").strip()
    comment = request.form.get("comment", "").strip()
    if (len(email) > 254 or not re.fullmatch(r"[\x21-\x7e]+", email)
            or not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", email)
            or not comment or len(comment.encode("utf-8")) > 2000):
        return jsonify(error="Use a valid email and a comment of 1-2000 UTF-8 bytes."), 400
    row = {"email": email, "comment": comment,
           "createdAt": datetime.now(timezone.utc).isoformat()}
    with gate:
        with file.open("a", encoding="utf-8") as stream:
            stream.write(json.dumps(row, ensure_ascii=False) + "\n")
    return jsonify(ok=True), 201

@app.errorhandler(413)
def too_large(error):
    return jsonify(error="Request too large."), 413

@app.errorhandler(OSError)
def storage_error(error):
    app.logger.exception("Storage unavailable")
    return jsonify(error="Storage unavailable."), 500
```

Create `python/Dockerfile`:

```dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app.py .
COPY public/ public/
RUN mkdir -p /data
EXPOSE 8000
CMD ["gunicorn", "--workers", "1", "--threads", "4", "-b", "0.0.0.0:8000", "app:app"]
```

Build and run:

```powershell
Set-Location "$root\python"
New-Item -ItemType Directory -Force public | Out-Null
Copy-Item ..\shared\index.html public\index.html
docker build -t guestbook-python .
docker run -d --name guestbook-python -p 127.0.0.1:8087:8000 `
    -v guestbook-python-data:/data guestbook-python
Start-Process http://localhost:8087
```

Gunicorn serves Flask instead of its development server. Keep `--workers 1`: a `threading.Lock` coordinates threads in one process, not independent worker processes.

## Step 9: Verify the complete workflow

A home page returning HTTP 200 is no longer enough. Check the form, validation, public response and persistence.

First confirm the containers are running:

```powershell
docker ps --format "table {{.Names}}\t{{.Ports}}\t{{.Status}}"
```

The following PowerShell 7 test creates one entry in every stack. Run only against the stacks you started by changing the port range. It uses a unique marker, checks validation and verifies that the submitted email is absent from the public JSON.

```powershell
$marker = [guid]::NewGuid().ToString('N')
foreach ($port in 8081..8087) {
    $base = "http://localhost:$port"
    $page = Invoke-WebRequest "$base/"
    if ($page.StatusCode -ne 200) { throw "Page failed on $port" }
    $body = @{ email = 'visitor@example.com'; comment = "Hello $marker" }
    $saved = Invoke-WebRequest "$base/api/entries" -Method Post `
        -ContentType 'application/x-www-form-urlencoded' -Body $body
    if ($saved.StatusCode -ne 201) { throw "Save failed on $port" }
    $public = Invoke-WebRequest "$base/api/entries"
    $rows = @($public.Content | ConvertFrom-Json)
    if (-not ($rows | Where-Object comment -eq "Hello $marker")) {
        throw "Saved comment missing on $port"
    }
    if ($public.Content -match 'visitor@example.com' -or
        $public.Content -match '"email"\s*:') {
        throw "Email leaked on $port"
    }
    $bad = Invoke-WebRequest "$base/api/entries" -Method Post `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body @{ email = 'invalid'; comment = 'Hello' } -SkipHttpErrorCheck
    if ($bad.StatusCode -ne 400) { throw "Validation failed on $port" }
    "Port ${port}: page, submission, privacy and validation passed"
}
```

In PowerShell interpolation, use `${port}` before a colon.

Now open a guestbook in the browser and check:

1. Submit a valid email and comment. The thank-you message appears and the comment is listed.
2. Refresh the page. The saved comment remains.
3. Submit a comment containing `<script>alert('test')</script>`. It must appear as text without an alert.
4. Submit a blank comment and an invalid email through the API. Each must return HTTP 400.
5. Try a comment longer than 2,000 UTF-8 bytes. The client and server should reject it. Non-ASCII characters can use more than one byte.
6. Use the browser network panel to inspect `GET /api/entries`. No email address should appear.

Test persistence by recreating the Node container with its existing volume:

```powershell
docker rm -f guestbook-node
docker run -d --name guestbook-node -p 127.0.0.1:8086:3000 `
    -v guestbook-node-data:/data guestbook-node
Invoke-RestMethod http://localhost:8086/api/entries
```

The previous comment should still be present. Repeat with the corresponding image, ports and volume for another stack. For Nginx/PHP, run `docker compose down` followed by `docker compose up -d` in `php-nginx`; do not add `-v`.

## What changed from the portal tutorial?

| Static portal | Guestbook |
| --- | --- |
| One HTML file with announcements and quick links | Shared email/comment form and recent comments |
| Backends mostly serve static files | Each backend validates submissions and saves entries |
| No application data | Per-stack persistent Docker volume |
| Verify HTTP 200 | Verify POST, validation, private email handling and persistence |
| Compare static file serving | Compare routing, request parsing, JSON and storage code |

PHP uses one request handler in both web-server arrangements. Plain Java exposes the request handling details. Spring provides annotations and automatic serialization. ASP.NET Core uses minimal API routes. Express and Flask keep the handlers compact. The UI and API contract stay the same, making the differences easier to inspect.

## Updating the shared page

Edit `shared/index.html`, then copy it again into the stack's web directory. Rebuild the image, remove the old container and rerun the original command with the same named volume. For PHP/Nginx, copy the page into `php-nginx/index.html` and refresh; rebuild only when the PHP code changes.

A Docker build does not update an already running container. Keeping the original volume name is what preserves the guestbook when the container is replaced.

## Troubleshooting

| Symptom | What to check |
| --- | --- |
| Port already allocated | Change the host port, for example `127.0.0.1:9081:80`, and use that URL. |
| Container stops immediately | Run `docker logs <container-name>`. For Compose use `docker compose logs`. |
| Form says storage unavailable | Check `/data` ownership and the volume mount. PHP must be able to write as `www-data`. |
| Page works but API returns 404 | Check Apache fallback configuration, Nginx's exact API location, or the backend route. |
| Nginx returns 502 | Inspect `docker compose logs php`; wait for PHP-FPM to be ready. |
| Comment disappears after replacement | Verify the same named volume is mounted at `/data`; do not remove the volume. |
| UI changes do not appear | Recopy the shared HTML into the stack folder, rebuild where needed, recreate and refresh. |
| JSON parsing fails | Inspect the network response and container logs; PHP files must not have a BOM or stray output before `<?php`. |
| Entries file is malformed after interruption | Restore a backup or inspect the private file offline. Do not silently discard damaged data. Use a database for transactional durability. |
| Duplicate comment after retry | This demo has no idempotency key. A network failure can occur after the server saves; check the list before retrying. |

## Before making it public

The containers bind to `127.0.0.1` for local use. A public guestbook needs a database with transactions, HTTPS, rate limiting, spam controls, request-size limits across every stack, a moderation policy, and a retention/deletion process for saved email addresses. Collecting an email does not subscribe someone to a mailing list or verify their identity. Add authentication before exposing an administration view or export containing emails. Add suitable cross-site submission protection when you publish the form, and CSRF protection if you introduce cookie-authenticated actions.

The demo displays comments immediately. If approval is needed, add a moderation status and publish only approved comments. Do not add an unauthenticated endpoint that returns the stored records, because those records contain private email addresses.

For a growing guestbook, replace the file store with SQLite for a single-instance application or a server database for multiple instances. Every listing currently scans the full file even though it returns only 100 entries. File locks and process locks prevent ordinary overlapping writes in the stated configuration; they are not a substitute for database transactions, backups or recovery.

## Clean up while retaining entries

```powershell
docker rm -f guestbook-php-apache guestbook-java guestbook-spring `
    guestbook-aspnet guestbook-node guestbook-python
Set-Location "$root\php-nginx"
docker compose down
```

These commands remove the containers but preserve the data volumes. Removing images also leaves the volumes intact.

Only if you deliberately want to erase the saved emails and comments, run:

```powershell
docker volume rm guestbook-php-apache-data guestbook-java-data `
    guestbook-spring-data guestbook-aspnet-data guestbook-node-data `
    guestbook-python-data
# Deletes the PHP/Nginx guestbook volume as well:
Set-Location "$root\php-nginx"
docker compose down -v
```

You now have the same working guestbook in seven stacks. The exercise covers the parts a static page cannot demonstrate: handling user input, validating it on the server, keeping private fields out of public responses, and retaining data beyond a container's lifetime.

## Documentation references

- PHP advisory file locking: https://www.php.net/manual/en/function.flock.php
- Java HttpServer: https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html
- Spring Boot 3.5 system requirements: https://docs.spring.io/spring-boot/3.5/system-requirements.html
- ASP.NET Core minimal APIs: https://learn.microsoft.com/en-us/aspnet/core/fundamentals/minimal-apis
- Express API: https://expressjs.com/en/4x/api.html
- Flask API: https://flask.palletsprojects.com/en/stable/api/
- Gunicorn worker settings: https://docs.gunicorn.org/en/stable/settings.html
- Docker volumes: https://docs.docker.com/engine/storage/volumes/

