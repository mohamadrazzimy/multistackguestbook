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
