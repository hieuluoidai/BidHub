package network;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import utils.ImageStorageService;

/**
 * HTTP handler phục vụ và nhận file ảnh cho hệ thống BidHub.
 *
 * Endpoints:
 *   GET  /{dbPath}                                    — Tải ảnh (vd: /items/ITEM_123.jpg)
 *   POST /upload?id={id}&ext={ext}&prefix={prefix}    — Upload ảnh lên server
 */
class ImageHttpHandler implements HttpHandler {

    private static final Set<String> ALLOWED_EXTS = Set.of("jpg", "jpeg", "png", "gif");
    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("GET".equals(method)) {
            handleGet(exchange);
        } else if ("POST".equals(method)) {
            handlePost(exchange);
        } else {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();
        if (requestPath.contains("..")) {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
            return;
        }
        String dbPath = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;
        Path filePath = resolveFilePath(dbPath);
        if (filePath == null || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        byte[] bytes = Files.readAllBytes(filePath);
        exchange.getResponseHeaders().set("Content-Type", getContentType(dbPath));
        exchange.getResponseHeaders().set("Cache-Control", "max-age=3600");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String id = params.get("id");
        String ext = params.get("ext");
        String prefix = params.getOrDefault("prefix", "items");

        if (id == null || ext == null || !ALLOWED_EXTS.contains(ext.toLowerCase(Locale.ROOT))) {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
            return;
        }

        byte[] bytes;
        try (InputStream is = exchange.getRequestBody()) {
            bytes = is.readAllBytes();
        }

        if (bytes.length == 0 || bytes.length > MAX_UPLOAD_BYTES) {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
            return;
        }

        String dbPath;
        try {
            dbPath = "avatars".equals(prefix)
                    ? ImageStorageService.saveAvatarFromBytes(bytes, id, ext)
                    : ImageStorageService.saveImageFromBytes(bytes, id, ext);
        } catch (IOException e) {
            System.err.println(">>> [HTTP] Lỗi lưu ảnh: " + e.getMessage());
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
            return;
        }

        byte[] response = dbPath.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private Path resolveFilePath(String dbPath) {
        if (dbPath.startsWith(ImageStorageService.DB_AVATAR_PREFIX)) {
            String filename = dbPath.substring(ImageStorageService.DB_AVATAR_PREFIX.length());
            return Paths.get(ImageStorageService.AVATAR_DIR, filename);
        } else if (dbPath.startsWith(ImageStorageService.DB_PATH_PREFIX)) {
            String filename = dbPath.substring(ImageStorageService.DB_PATH_PREFIX.length());
            return Paths.get(ImageStorageService.STORAGE_DIR, filename);
        }
        return null;
    }

    private String getContentType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null || query.isBlank()) return result;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2) {
                result.put(kv[0], kv[1]);
            }
        }
        return result;
    }
}
