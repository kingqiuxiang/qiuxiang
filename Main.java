import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Main {
    private static final int DEFAULT_PORT = 8787;
    private static final Path DEFAULT_PROJECT_ROOT = Paths.get("").toAbsolutePath().normalize();
    private static final ProcessRegistry PROCESS_REGISTRY = new ProcessRegistry();

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : intFromEnv("PORT", DEFAULT_PORT);
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", Main::route);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("AI YAPI Test Console is running at http://localhost:" + port);
    }

    private static int intFromEnv(String name, int fallback) {
        try {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static void route(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        try {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && "/".equals(path)) {
                sendText(exchange, 200, "text/html; charset=utf-8", FRONTEND);
                return;
            }
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && "/api/scan".equals(path)) {
                handleScan(exchange);
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && "/api/analyze".equals(path)) {
                handleAnalyze(exchange);
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && "/api/fill".equals(path)) {
                handleFill(exchange);
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && "/api/test".equals(path)) {
                handleTest(exchange);
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && "/api/quick-start".equals(path)) {
                handleQuickStart(exchange);
                return;
            }
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && "/api/quick-start/logs".equals(path)) {
                handleQuickStartLogs(exchange);
                return;
            }
            sendJson(exchange, 404, Map.of("ok", false, "error", "Route not found: " + path));
        } catch (Exception error) {
            sendJson(exchange, 500, Map.of("ok", false, "error", error.getMessage()));
        }
    }

    private static void handleScan(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        Path projectRoot = projectRoot(query.get("path"));
        sendJson(exchange, 200, Map.of("ok", true, "project", ProjectScanner.scan(projectRoot)));
    }

    private static void handleAnalyze(HttpExchange exchange) throws Exception {
        Map<String, Object> request = readObject(exchange);
        Path projectRoot = projectRoot(asString(request.get("projectPath")));
        String yapiSource = asString(request.get("yapiSource"));
        String yapiContent = asString(request.get("yapiContent"));
        String rawYapi = yapiContent.isBlank() ? loadSource(yapiSource, projectRoot) : yapiContent;

        List<Map<String, Object>> endpoints = rawYapi.isBlank()
                ? List.of()
                : YapiParser.endpointsFrom(Json.parse(rawYapi));
        Map<String, Object> project = ProjectScanner.scan(projectRoot);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("project", project);
        response.put("endpoints", endpoints);
        response.put("endpointCount", endpoints.size());
        response.put("aiConfigured", AiClient.isConfigured());
        sendJson(exchange, 200, response);
    }

    @SuppressWarnings("unchecked")
    private static void handleFill(HttpExchange exchange) throws Exception {
        Map<String, Object> request = readObject(exchange);
        Object endpoint = request.get("endpoint");
        if (!(endpoint instanceof Map)) {
            sendJson(exchange, 400, Map.of("ok", false, "error", "Missing endpoint payload"));
            return;
        }

        Path projectRoot = projectRoot(asString(request.get("projectPath")));
        Map<String, Object> project = ProjectScanner.scan(projectRoot);
        Map<String, Object> filled = ParameterFiller.fill((Map<String, Object>) endpoint, project);
        boolean useAi = asBoolean(request.get("useAi"), false);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("filled", filled);
        response.put("aiConfigured", AiClient.isConfigured());
        if (useAi) {
            response.put("ai", AiClient.enhance((Map<String, Object>) endpoint, project, filled));
        }
        sendJson(exchange, 200, response);
    }

    private static void handleTest(HttpExchange exchange) throws Exception {
        Map<String, Object> request = readObject(exchange);
        sendJson(exchange, 200, DevTester.run(request));
    }

    private static void handleQuickStart(HttpExchange exchange) throws Exception {
        Map<String, Object> request = readObject(exchange);
        Path projectRoot = projectRoot(asString(request.get("projectPath")));
        String command = asString(request.get("command"));
        if (command.isBlank()) {
            command = QuickStart.inferCommand(projectRoot);
        }
        boolean run = asBoolean(request.get("run"), false);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("command", command);
        response.put("projectPath", projectRoot.toString());
        if (run) {
            response.put("session", PROCESS_REGISTRY.start(projectRoot, command));
        }
        sendJson(exchange, 200, response);
    }

    private static void handleQuickStartLogs(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String id = query.get("id");
        if (id == null || id.isBlank()) {
            sendJson(exchange, 400, Map.of("ok", false, "error", "Missing session id"));
            return;
        }
        sendJson(exchange, 200, PROCESS_REGISTRY.logs(id));
    }

    private static Map<String, Object> readObject(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        if (body.isBlank()) {
            return new LinkedHashMap<>();
        }
        Object parsed = Json.parse(body);
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("Request body must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> object = (Map<String, Object>) parsed;
        return object;
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path projectRoot(String requestedPath) {
        if (requestedPath == null || requestedPath.isBlank()) {
            return DEFAULT_PROJECT_ROOT;
        }
        return Paths.get(requestedPath).toAbsolutePath().normalize();
    }

    private static String loadSource(String source, Path projectRoot) throws Exception {
        if (source == null || source.isBlank()) {
            return "";
        }
        if (source.startsWith("http://") || source.startsWith("https://")) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(source))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
        }
        Path file = Paths.get(source);
        if (!file.isAbsolute()) {
            file = projectRoot.resolve(source).normalize();
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> query = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return query;
        }
        for (String pair : rawQuery.split("&")) {
            int index = pair.indexOf('=');
            String key = decode(index >= 0 ? pair.substring(0, index) : pair);
            String value = index >= 0 ? decode(pair.substring(index + 1)) : "";
            query.put(key, value);
        }
        return query;
    }

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        sendText(exchange, status, "application/json; charset=utf-8", Json.stringify(payload));
    }

    private static void sendText(HttpExchange exchange, int status, String contentType, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type,Authorization");
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean asBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static String firstNonBlank(Object... values) {
        for (Object value : values) {
            String text = asString(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    static class ProjectScanner {
        private static final Set<String> CODE_EXTENSIONS = Set.of(
                ".java", ".kt", ".js", ".jsx", ".ts", ".tsx", ".vue", ".go", ".py", ".php", ".rb", ".cs"
        );
        private static final Pattern SPRING_MAPPING = Pattern.compile(
                "@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\\s*(?:\\(([^)]*)\\))?",
                Pattern.CASE_INSENSITIVE
        );
        private static final Pattern ROUTER_MAPPING = Pattern.compile(
                "(?:router|app|route)\\s*\\.\\s*(get|post|put|delete|patch)\\s*\\(\\s*['\"]([^'\"]+)['\"]",
                Pattern.CASE_INSENSITIVE
        );
        private static final Pattern CLIENT_CALL = Pattern.compile(
                "(?:fetch|axios\\.(get|post|put|delete|patch)|request)\\s*\\(\\s*['\"]([^'\"]+)['\"]",
                Pattern.CASE_INSENSITIVE
        );
        private static final Pattern JAVA_FIELD = Pattern.compile(
                "(?:private|public|protected)\\s+(?:final\\s+)?([A-Za-z0-9_<>, ?]+)\\s+([A-Za-z][A-Za-z0-9_]*)\\s*(?:=|;)"
        );
        private static final Pattern TS_FIELD = Pattern.compile(
                "([A-Za-z][A-Za-z0-9_]*)\\??\\s*:\\s*([A-Za-z0-9_\\[\\]<>|]+)\\s*[;,]"
        );

        static Map<String, Object> scan(Path root) throws IOException {
            Map<String, Object> result = new LinkedHashMap<>();
            List<Map<String, Object>> endpoints = new ArrayList<>();
            List<Map<String, Object>> models = new ArrayList<>();
            Set<String> scannedExtensions = new LinkedHashSet<>();
            int[] filesScanned = {0};

            if (!Files.exists(root)) {
                result.put("root", root.toString());
                result.put("filesScanned", 0);
                result.put("endpoints", endpoints);
                result.put("models", models);
                result.put("warning", "Project path does not exist");
                return result;
            }

            try (Stream<Path> stream = Files.walk(root, 10)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> !shouldSkip(root, path))
                        .filter(ProjectScanner::isCodeFile)
                        .limit(700)
                        .forEach(path -> {
                            try {
                                scannedExtensions.add(extension(path.getFileName().toString()));
                                String content = readSmallFile(path);
                                filesScanned[0]++;
                                endpoints.addAll(extractEndpoints(root, path, content));
                                if (models.size() < 160) {
                                    models.addAll(extractModels(root, path, content));
                                }
                            } catch (IOException ignored) {
                                // Ignore unreadable files so scanning never blocks the console.
                            }
                        });
            }

            result.put("root", root.toString());
            result.put("filesScanned", filesScanned[0]);
            result.put("extensions", new ArrayList<>(scannedExtensions));
            result.put("endpoints", endpoints);
            result.put("models", models.size() > 160 ? models.subList(0, 160) : models);
            return result;
        }

        private static boolean shouldSkip(Path root, Path path) {
            Path relative = root.relativize(path);
            for (Path part : relative) {
                String name = part.toString();
                if (Set.of(".git", "node_modules", "target", "build", "dist", "out", ".gradle", ".idea").contains(name)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isCodeFile(Path path) {
            return CODE_EXTENSIONS.contains(extension(path.getFileName().toString()));
        }

        private static String extension(String fileName) {
            int index = fileName.lastIndexOf('.');
            return index >= 0 ? fileName.substring(index).toLowerCase(Locale.ROOT) : "";
        }

        private static String readSmallFile(Path path) throws IOException {
            long size = Files.size(path);
            if (size > 300_000) {
                return "";
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        }

        private static List<Map<String, Object>> extractEndpoints(Path root, Path file, String content) {
            List<Map<String, Object>> endpoints = new ArrayList<>();
            Matcher spring = SPRING_MAPPING.matcher(content);
            while (spring.find() && endpoints.size() < 80) {
                String annotation = spring.group(1);
                String args = spring.group(2) == null ? "" : spring.group(2);
                Map<String, Object> endpoint = new LinkedHashMap<>();
                endpoint.put("source", "spring");
                endpoint.put("method", springMethod(annotation, args));
                endpoint.put("path", quotedValue(args));
                endpoint.put("file", root.relativize(file).toString());
                endpoint.put("line", lineNumber(content, spring.start()));
                endpoints.add(endpoint);
            }

            Matcher router = ROUTER_MAPPING.matcher(content);
            while (router.find() && endpoints.size() < 120) {
                endpoints.add(endpoint("node-router", router.group(1).toUpperCase(Locale.ROOT), router.group(2), root, file, content, router.start()));
            }

            Matcher client = CLIENT_CALL.matcher(content);
            while (client.find() && endpoints.size() < 160) {
                String method = client.group(1) == null ? "GET" : client.group(1).toUpperCase(Locale.ROOT);
                endpoints.add(endpoint("frontend-call", method, client.group(2), root, file, content, client.start()));
            }
            return endpoints;
        }

        private static Map<String, Object> endpoint(String source, String method, String path, Path root, Path file, String content, int start) {
            Map<String, Object> endpoint = new LinkedHashMap<>();
            endpoint.put("source", source);
            endpoint.put("method", method);
            endpoint.put("path", path);
            endpoint.put("file", root.relativize(file).toString());
            endpoint.put("line", lineNumber(content, start));
            return endpoint;
        }

        private static String springMethod(String annotation, String args) {
            String normalized = annotation.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("get")) return "GET";
            if (normalized.startsWith("post")) return "POST";
            if (normalized.startsWith("put")) return "PUT";
            if (normalized.startsWith("delete")) return "DELETE";
            if (normalized.startsWith("patch")) return "PATCH";
            Matcher method = Pattern.compile("RequestMethod\\.([A-Z]+)").matcher(args);
            return method.find() ? method.group(1) : "ANY";
        }

        private static String quotedValue(String text) {
            Matcher matcher = Pattern.compile("['\"]([^'\"]+)['\"]").matcher(text);
            return matcher.find() ? matcher.group(1) : "";
        }

        private static int lineNumber(String content, int offset) {
            int line = 1;
            for (int i = 0; i < offset && i < content.length(); i++) {
                if (content.charAt(i) == '\n') {
                    line++;
                }
            }
            return line;
        }

        private static List<Map<String, Object>> extractModels(Path root, Path file, String content) {
            List<Map<String, Object>> models = new ArrayList<>();
            String ext = extension(file.getFileName().toString());
            if (Set.of(".java", ".kt", ".cs").contains(ext)) {
                Matcher javaField = JAVA_FIELD.matcher(content);
                while (javaField.find() && models.size() < 40) {
                    models.add(model(root, file, javaField.group(2), javaField.group(1), lineNumber(content, javaField.start())));
                }
            }
            if (Set.of(".ts", ".tsx", ".vue").contains(ext)) {
                Matcher tsField = TS_FIELD.matcher(content);
                while (tsField.find() && models.size() < 80) {
                    models.add(model(root, file, tsField.group(1), tsField.group(2), lineNumber(content, tsField.start())));
                }
            }
            return models;
        }

        private static Map<String, Object> model(Path root, Path file, String name, String type, int line) {
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("name", name);
            model.put("type", type.trim());
            model.put("file", root.relativize(file).toString());
            model.put("line", line);
            return model;
        }
    }

    static class YapiParser {
        static List<Map<String, Object>> endpointsFrom(Object root) {
            if (!(root instanceof Map<?, ?> map)) {
                return List.of();
            }
            if (map.containsKey("paths")) {
                return fromOpenApi(castMap(map));
            }
            Object data = map.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                Object list = dataMap.get("list");
                if (list instanceof List<?>) {
                    return fromYapiList(list);
                }
                Object cat = dataMap.get("cat");
                if (cat instanceof List<?>) {
                    return fromYapiCategories(cat);
                }
            }
            Object list = map.get("list");
            if (list instanceof List<?>) {
                return fromYapiList(list);
            }
            return List.of();
        }

        private static List<Map<String, Object>> fromYapiCategories(Object categories) {
            List<Map<String, Object>> endpoints = new ArrayList<>();
            for (Object category : (List<?>) categories) {
                if (category instanceof Map<?, ?> categoryMap) {
                    Object list = categoryMap.get("list");
                    if (list instanceof List<?>) {
                        endpoints.addAll(fromYapiList(list));
                    }
                }
            }
            return endpoints;
        }

        private static List<Map<String, Object>> fromYapiList(Object items) {
            List<Map<String, Object>> endpoints = new ArrayList<>();
            for (Object item : (List<?>) items) {
                if (!(item instanceof Map<?, ?> itemMap)) {
                    continue;
                }
                Map<String, Object> source = castMap(itemMap);
                Map<String, Object> endpoint = new LinkedHashMap<>();
                endpoint.put("source", "yapi");
                endpoint.put("title", firstNonBlank(source.get("title"), source.get("name"), source.get("desc")));
                endpoint.put("method", asString(source.getOrDefault("method", "GET")).toUpperCase(Locale.ROOT));
                endpoint.put("path", firstNonBlank(source.get("path"), source.get("url")));
                endpoint.put("inputs", yapiInputs(source));
                Object bodySchema = parseMaybeJson(source.get("req_body_other"));
                if (bodySchema != null) {
                    endpoint.put("bodySchema", bodySchema);
                }
                endpoints.add(endpoint);
            }
            return endpoints;
        }

        private static List<Map<String, Object>> yapiInputs(Map<String, Object> source) {
            List<Map<String, Object>> inputs = new ArrayList<>();
            addYapiFields(inputs, source.get("req_params"), "path");
            addYapiFields(inputs, source.get("req_query"), "query");
            addYapiFields(inputs, source.get("req_headers"), "header");
            addYapiFields(inputs, source.get("req_body_form"), "body");
            return inputs;
        }

        private static void addYapiFields(List<Map<String, Object>> inputs, Object rawFields, String location) {
            if (!(rawFields instanceof List<?> fields)) {
                return;
            }
            for (Object field : fields) {
                if (!(field instanceof Map<?, ?> fieldMap)) {
                    continue;
                }
                Map<String, Object> source = castMap(fieldMap);
                Map<String, Object> input = new LinkedHashMap<>();
                input.put("in", location);
                input.put("name", firstNonBlank(source.get("name"), source.get("key")));
                input.put("type", firstNonBlank(source.get("type"), source.get("field_type"), "string"));
                input.put("required", Objects.equals(source.get("required"), "1") || Objects.equals(source.get("required"), 1) || Boolean.TRUE.equals(source.get("required")));
                input.put("example", firstNonBlank(source.get("example"), source.get("value"), source.get("mock")));
                input.put("description", firstNonBlank(source.get("desc"), source.get("description")));
                inputs.add(input);
            }
        }

        private static List<Map<String, Object>> fromOpenApi(Map<String, Object> root) {
            List<Map<String, Object>> endpoints = new ArrayList<>();
            Object rawPaths = root.get("paths");
            if (!(rawPaths instanceof Map<?, ?> paths)) {
                return endpoints;
            }
            for (Map.Entry<?, ?> pathEntry : paths.entrySet()) {
                if (!(pathEntry.getValue() instanceof Map<?, ?> methods)) {
                    continue;
                }
                for (Map.Entry<?, ?> methodEntry : methods.entrySet()) {
                    String method = String.valueOf(methodEntry.getKey()).toUpperCase(Locale.ROOT);
                    if (!Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD").contains(method)) {
                        continue;
                    }
                    Map<String, Object> operation = castMap((Map<?, ?>) methodEntry.getValue());
                    Map<String, Object> endpoint = new LinkedHashMap<>();
                    endpoint.put("source", "openapi");
                    endpoint.put("title", firstNonBlank(operation.get("summary"), operation.get("operationId")));
                    endpoint.put("method", method);
                    endpoint.put("path", String.valueOf(pathEntry.getKey()));
                    endpoint.put("inputs", openApiInputs(operation));
                    Object requestBody = openApiRequestBody(operation.get("requestBody"));
                    if (requestBody != null) {
                        endpoint.put("bodySchema", requestBody);
                    }
                    endpoints.add(endpoint);
                }
            }
            return endpoints;
        }

        private static List<Map<String, Object>> openApiInputs(Map<String, Object> operation) {
            List<Map<String, Object>> inputs = new ArrayList<>();
            Object parameters = operation.get("parameters");
            if (!(parameters instanceof List<?> list)) {
                return inputs;
            }
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> parameter)) {
                    continue;
                }
                Map<String, Object> source = castMap(parameter);
                Map<String, Object> input = new LinkedHashMap<>();
                input.put("in", firstNonBlank(source.get("in"), "query"));
                input.put("name", firstNonBlank(source.get("name")));
                input.put("required", Boolean.TRUE.equals(source.get("required")));
                input.put("description", firstNonBlank(source.get("description")));
                Object schema = source.get("schema");
                if (schema instanceof Map<?, ?> schemaMap) {
                    input.put("schema", castMap(schemaMap));
                    input.put("type", firstNonBlank(castMap(schemaMap).get("type"), "string"));
                } else {
                    input.put("type", "string");
                }
                inputs.add(input);
            }
            return inputs;
        }

        private static Object openApiRequestBody(Object rawRequestBody) {
            if (!(rawRequestBody instanceof Map<?, ?> requestBody)) {
                return null;
            }
            Object content = requestBody.get("content");
            if (!(content instanceof Map<?, ?> contentMap)) {
                return null;
            }
            for (String type : List.of("application/json", "application/*+json", "multipart/form-data")) {
                Object media = contentMap.get(type);
                if (media instanceof Map<?, ?> mediaMap) {
                    return mediaMap.get("schema");
                }
            }
            return null;
        }

        private static Object parseMaybeJson(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Map<?, ?> || value instanceof List<?>) {
                return value;
            }
            String text = String.valueOf(value).trim();
            if (text.isBlank()) {
                return null;
            }
            try {
                return Json.parse(text);
            } catch (RuntimeException ignored) {
                return Map.of("raw", text);
            }
        }
    }

    static class ParameterFiller {
        @SuppressWarnings("unchecked")
        static Map<String, Object> fill(Map<String, Object> endpoint, Map<String, Object> project) {
            Map<String, Object> payload = new LinkedHashMap<>();
            Map<String, Object> path = new LinkedHashMap<>();
            Map<String, Object> query = new LinkedHashMap<>();
            Map<String, Object> headers = new LinkedHashMap<>();
            Map<String, Object> body = new LinkedHashMap<>();

            Object inputs = endpoint.get("inputs");
            if (inputs instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> inputMap)) {
                        continue;
                    }
                    Map<String, Object> input = castMap(inputMap);
                    String name = firstNonBlank(input.get("name"));
                    if (name.isBlank()) {
                        continue;
                    }
                    Object sample = sampleValue(input, project);
                    String location = firstNonBlank(input.get("in"), "query").toLowerCase(Locale.ROOT);
                    if ("path".equals(location)) {
                        path.put(name, sample);
                    } else if ("header".equals(location) || "headers".equals(location)) {
                        headers.put(name, sample);
                    } else if ("body".equals(location) || "formData".equals(location)) {
                        body.put(name, sample);
                    } else {
                        query.put(name, sample);
                    }
                }
            }

            Object bodySchema = endpoint.get("bodySchema");
            if (bodySchema instanceof Map<?, ?> schema) {
                Object sample = sampleFromSchema(castMap(schema), project);
                if (sample instanceof Map<?, ?> sampleMap) {
                    body.putAll(castMap(sampleMap));
                } else if (!body.containsKey("value")) {
                    body.put("value", sample);
                }
            }

            payload.put("method", firstNonBlank(endpoint.get("method"), "GET"));
            payload.put("pathTemplate", firstNonBlank(endpoint.get("path")));
            payload.put("path", path);
            payload.put("query", query);
            payload.put("headers", headers);
            payload.put("body", body);
            payload.put("curl", toCurl(payload));
            payload.put("confidence", confidence(endpoint, project));
            payload.put("notes", List.of(
                    "样例值优先使用 YAPI/OpenAPI example/mock，其次按字段名、类型和项目模型推断。",
                    "如配置 AI_API_KEY，可打开 AI 增强获得更贴近业务语义的参数。"
            ));
            return payload;
        }

        private static String confidence(Map<String, Object> endpoint, Map<String, Object> project) {
            Object inputs = endpoint.get("inputs");
            int inputCount = inputs instanceof List<?> list ? list.size() : 0;
            int models = project.get("models") instanceof List<?> list ? list.size() : 0;
            if (inputCount > 0 && models > 0) {
                return "high";
            }
            if (inputCount > 0) {
                return "medium";
            }
            return "low";
        }

        private static Object sampleValue(Map<String, Object> input, Map<String, Object> project) {
            String example = firstNonBlank(input.get("example"));
            if (!example.isBlank()) {
                return coerce(example, firstNonBlank(input.get("type")));
            }
            Object schema = input.get("schema");
            if (schema instanceof Map<?, ?> schemaMap) {
                return sampleFromSchema(castMap(schemaMap), project);
            }
            String name = firstNonBlank(input.get("name")).toLowerCase(Locale.ROOT);
            String type = firstNonBlank(input.get("type"), inferTypeFromProject(input, project), "string").toLowerCase(Locale.ROOT);
            if (name.contains("email")) return "user@example.com";
            if (name.contains("phone") || name.contains("mobile")) return "13800138000";
            if (name.contains("token")) return "Bearer demo-token";
            if (name.contains("password")) return "P@ssw0rd123";
            if (name.equals("id") || name.endsWith("id") || name.contains("_id")) return 10001;
            if (name.contains("page")) return 1;
            if (name.contains("size") || name.contains("limit")) return 20;
            if (name.contains("date")) return "2026-06-05";
            if (name.contains("time")) return Instant.now().toString();
            return switch (type) {
                case "integer", "int", "long", "number", "float", "double" -> 1;
                case "boolean", "bool" -> true;
                case "array", "list" -> List.of("demo");
                case "object" -> Map.of("value", "demo");
                default -> "demo-" + normalizeName(name);
            };
        }

        private static Object sampleFromSchema(Map<String, Object> schema, Map<String, Object> project) {
            if (schema.containsKey("example")) {
                return schema.get("example");
            }
            Object enumValues = schema.get("enum");
            if (enumValues instanceof List<?> list && !list.isEmpty()) {
                return list.get(0);
            }
            if (schema.containsKey("raw")) {
                return schema.get("raw");
            }
            Object properties = schema.get("properties");
            if (properties instanceof Map<?, ?> propertyMap) {
                Map<String, Object> object = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : propertyMap.entrySet()) {
                    if (entry.getValue() instanceof Map<?, ?> childSchema) {
                        Map<String, Object> synthetic = castMap(childSchema);
                        synthetic.putIfAbsent("name", String.valueOf(entry.getKey()));
                        object.put(String.valueOf(entry.getKey()), sampleValue(synthetic, project));
                    } else {
                        object.put(String.valueOf(entry.getKey()), "demo-" + normalizeName(String.valueOf(entry.getKey())));
                    }
                }
                return object;
            }
            String type = firstNonBlank(schema.get("type"), "object").toLowerCase(Locale.ROOT);
            if ("array".equals(type)) {
                Object items = schema.get("items");
                if (items instanceof Map<?, ?> itemSchema) {
                    return List.of(sampleFromSchema(castMap(itemSchema), project));
                }
                return List.of("demo");
            }
            return sampleValue(schema, project);
        }

        @SuppressWarnings("unchecked")
        private static String inferTypeFromProject(Map<String, Object> input, Map<String, Object> project) {
            String name = firstNonBlank(input.get("name"));
            Object models = project.get("models");
            if (!(models instanceof List<?> list)) {
                return "";
            }
            for (Object item : list) {
                if (item instanceof Map<?, ?> model && name.equalsIgnoreCase(firstNonBlank(((Map<String, Object>) model).get("name")))) {
                    return firstNonBlank(((Map<String, Object>) model).get("type"));
                }
            }
            return "";
        }

        private static Object coerce(String value, String type) {
            String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT);
            try {
                if (Set.of("integer", "int", "long").contains(normalized)) {
                    return Long.parseLong(value);
                }
                if (Set.of("number", "float", "double").contains(normalized)) {
                    return Double.parseDouble(value);
                }
                if (Set.of("boolean", "bool").contains(normalized)) {
                    return Boolean.parseBoolean(value);
                }
                if ((value.startsWith("{") && value.endsWith("}")) || (value.startsWith("[") && value.endsWith("]"))) {
                    return Json.parse(value);
                }
            } catch (RuntimeException ignored) {
                return value;
            }
            return value;
        }

        @SuppressWarnings("unchecked")
        private static String toCurl(Map<String, Object> payload) {
            String method = firstNonBlank(payload.get("method"), "GET");
            String path = firstNonBlank(payload.get("pathTemplate"), "/api/demo");
            Map<String, Object> pathParams = (Map<String, Object>) payload.getOrDefault("path", Map.of());
            for (Map.Entry<String, Object> entry : pathParams.entrySet()) {
                path = path.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()))
                        .replace(":" + entry.getKey(), String.valueOf(entry.getValue()));
            }
            Map<String, Object> query = (Map<String, Object>) payload.getOrDefault("query", Map.of());
            if (!query.isEmpty()) {
                List<String> parts = new ArrayList<>();
                for (Map.Entry<String, Object> entry : query.entrySet()) {
                    parts.add(encode(entry.getKey()) + "=" + encode(String.valueOf(entry.getValue())));
                }
                path += (path.contains("?") ? "&" : "?") + String.join("&", parts);
            }
            StringBuilder curl = new StringBuilder("curl -X ").append(method).append(" 'http://localhost:8080").append(path).append("'");
            Map<String, Object> headers = (Map<String, Object>) payload.getOrDefault("headers", Map.of());
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                curl.append(" -H '").append(entry.getKey()).append(": ").append(entry.getValue()).append("'");
            }
            Map<String, Object> body = (Map<String, Object>) payload.getOrDefault("body", Map.of());
            if (!body.isEmpty() && !"GET".equalsIgnoreCase(method)) {
                curl.append(" -H 'Content-Type: application/json' -d '").append(Json.stringify(body).replace("'", "'\\''")).append("'");
            }
            return curl.toString();
        }

        private static String normalizeName(String name) {
            return name == null || name.isBlank() ? "value" : name.replaceAll("[^A-Za-z0-9_-]", "-");
        }
    }

    static class AiClient {
        static boolean isConfigured() {
            return !env("AI_API_KEY").isBlank();
        }

        static Map<String, Object> enhance(Map<String, Object> endpoint, Map<String, Object> project, Map<String, Object> filled) {
            Map<String, Object> result = new LinkedHashMap<>();
            if (!isConfigured()) {
                result.put("used", false);
                result.put("message", "未配置 AI_API_KEY，已使用本地确定性参数生成。");
                return result;
            }
            try {
                String prompt = """
                        You are an API testing assistant. Improve the generated request parameters with business-friendly demo values.
                        Return only compact JSON with keys: path, query, headers, body, rationale.
                        Endpoint:
                        %s
                        Project scan summary:
                        %s
                        Current generated payload:
                        %s
                        """.formatted(Json.stringify(endpoint), Json.stringify(project), Json.stringify(filled));
                String content = chat(prompt);
                Object parsed = Json.parse(extractJson(content));
                result.put("used", true);
                result.put("suggestion", parsed);
            } catch (Exception error) {
                result.put("used", false);
                result.put("message", "AI 调用失败，已保留本地结果：" + error.getMessage());
            }
            return result;
        }

        private static String chat(String prompt) throws Exception {
            String url = env("AI_BASE_URL");
            if (url.isBlank()) {
                url = "https://api.openai.com/v1/chat/completions";
            }
            String model = env("AI_MODEL");
            if (model.isBlank()) {
                model = "gpt-4o-mini";
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("temperature", 0.2);
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", "Return valid JSON only."),
                    Map.of("role", "user", "content", prompt)
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + env("AI_API_KEY"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(payload)))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            Object parsed = Json.parse(response.body());
            Object choices = ((Map<?, ?>) parsed).get("choices");
            if (choices instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> choice) {
                Object message = choice.get("message");
                if (message instanceof Map<?, ?> messageMap) {
                    return String.valueOf(messageMap.get("content"));
                }
            }
            throw new IllegalStateException("Unexpected AI response");
        }

        private static String extractJson(String text) {
            String trimmed = text.trim();
            if (trimmed.startsWith("```")) {
                trimmed = trimmed.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
            }
            int objectStart = trimmed.indexOf('{');
            int arrayStart = trimmed.indexOf('[');
            int start = objectStart >= 0 && (arrayStart < 0 || objectStart < arrayStart) ? objectStart : arrayStart;
            if (start > 0) {
                trimmed = trimmed.substring(start);
            }
            return trimmed;
        }

        private static String env(String name) {
            String value = System.getenv(name);
            return value == null ? "" : value.trim();
        }
    }

    static class DevTester {
        @SuppressWarnings("unchecked")
        static Map<String, Object> run(Map<String, Object> request) throws Exception {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("ok", true);
            List<Map<String, Object>> checks = new ArrayList<>();
            String frontendUrl = firstNonBlank(request.get("frontendUrl"));
            if (!frontendUrl.isBlank()) {
                checks.add(fetchPage(frontendUrl));
            }

            String apiBaseUrl = firstNonBlank(request.get("apiBaseUrl"));
            String apiPath = firstNonBlank(request.get("apiPath"));
            if (!apiBaseUrl.isBlank() && !apiPath.isBlank()) {
                Object rawPayload = request.get("payload");
                Map<String, Object> payload = rawPayload instanceof Map<?, ?> map ? castMap(map) : new LinkedHashMap<>();
                checks.add(callApi(apiBaseUrl, apiPath, firstNonBlank(request.get("method"), payload.get("method"), "GET"), payload));
            }
            response.put("checks", checks);
            response.put("passed", checks.stream().allMatch(check -> Boolean.TRUE.equals(check.get("passed"))));
            return response;
        }

        private static Map<String, Object> fetchPage(String url) throws Exception {
            Instant started = Instant.now();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> check = new LinkedHashMap<>();
            check.put("type", "frontend-page");
            check.put("url", url);
            check.put("status", response.statusCode());
            check.put("durationMs", Duration.between(started, Instant.now()).toMillis());
            check.put("title", htmlTitle(response.body()));
            check.put("passed", response.statusCode() >= 200 && response.statusCode() < 400);
            check.put("preview", response.body().substring(0, Math.min(280, response.body().length())));
            return check;
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> callApi(String baseUrl, String apiPath, String method, Map<String, Object> payload) throws Exception {
            Map<String, Object> query = payload.get("query") instanceof Map<?, ?> map ? castMap(map) : Map.of();
            Map<String, Object> body = payload.get("body") instanceof Map<?, ?> map ? castMap(map) : Map.of();
            Map<String, Object> headers = payload.get("headers") instanceof Map<?, ?> map ? castMap(map) : Map.of();
            String url = joinUrl(baseUrl, apiPath);
            if (!query.isEmpty()) {
                List<String> parts = new ArrayList<>();
                for (Map.Entry<String, Object> entry : query.entrySet()) {
                    parts.add(encode(entry.getKey()) + "=" + encode(String.valueOf(entry.getValue())));
                }
                url += (url.contains("?") ? "&" : "?") + String.join("&", parts);
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20));
            headers.forEach((key, value) -> builder.header(key, String.valueOf(value)));
            if ("GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
                builder.method(method.toUpperCase(Locale.ROOT), HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json");
                builder.method(method.toUpperCase(Locale.ROOT), HttpRequest.BodyPublishers.ofString(Json.stringify(body)));
            }

            Instant started = Instant.now();
            HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> check = new LinkedHashMap<>();
            check.put("type", "api-call");
            check.put("method", method.toUpperCase(Locale.ROOT));
            check.put("url", url);
            check.put("status", response.statusCode());
            check.put("durationMs", Duration.between(started, Instant.now()).toMillis());
            check.put("passed", response.statusCode() >= 200 && response.statusCode() < 400);
            check.put("responsePreview", response.body().substring(0, Math.min(600, response.body().length())));
            return check;
        }

        private static String joinUrl(String baseUrl, String apiPath) {
            if (baseUrl.endsWith("/") && apiPath.startsWith("/")) {
                return baseUrl.substring(0, baseUrl.length() - 1) + apiPath;
            }
            if (!baseUrl.endsWith("/") && !apiPath.startsWith("/")) {
                return baseUrl + "/" + apiPath;
            }
            return baseUrl + apiPath;
        }

        private static String htmlTitle(String html) {
            Matcher matcher = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
            return matcher.find() ? matcher.group(1).replaceAll("\\s+", " ").trim() : "";
        }
    }

    static class QuickStart {
        static String inferCommand(Path root) {
            if (Files.exists(root.resolve("package.json"))) {
                return "npm install && npm run dev";
            }
            if (Files.exists(root.resolve("pom.xml"))) {
                return "mvn spring-boot:run";
            }
            if (Files.exists(root.resolve("gradlew"))) {
                return "./gradlew bootRun";
            }
            if (Files.exists(root.resolve("Main.java"))) {
                return "javac Main.java && java Main 8788";
            }
            return "echo '请在这里填写项目启动命令'";
        }
    }

    static class ProcessRegistry {
        private final Map<String, Session> sessions = new ConcurrentHashMap<>();

        Map<String, Object> start(Path root, String command) throws IOException {
            ProcessBuilder builder = new ProcessBuilder("bash", "-lc", command);
            builder.directory(root.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String id = UUID.randomUUID().toString();
            Session session = new Session(id, command, process);
            sessions.put(id, session);
            Thread reader = new Thread(() -> readOutput(process, session), "quick-start-" + id);
            reader.setDaemon(true);
            reader.start();
            return session.snapshot();
        }

        Map<String, Object> logs(String id) {
            Session session = sessions.get(id);
            if (session == null) {
                return Map.of("ok", false, "error", "Session not found");
            }
            Map<String, Object> snapshot = session.snapshot();
            snapshot.put("ok", true);
            return snapshot;
        }

        private void readOutput(Process process, Session session) {
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    session.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                }
            } catch (IOException error) {
                session.append("\n[log reader error] " + error.getMessage());
            }
        }

        static class Session {
            final String id;
            final String command;
            final Process process;
            final Instant startedAt = Instant.now();
            final StringBuilder logs = new StringBuilder();

            Session(String id, String command, Process process) {
                this.id = id;
                this.command = command;
                this.process = process;
            }

            synchronized void append(String text) {
                logs.append(text);
                if (logs.length() > 16_000) {
                    logs.delete(0, logs.length() - 16_000);
                }
            }

            synchronized Map<String, Object> snapshot() {
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("id", id);
                snapshot.put("command", command);
                snapshot.put("running", process.isAlive());
                snapshot.put("uptimeMs", Duration.between(startedAt, Instant.now()).toMillis());
                snapshot.put("logs", logs.toString());
                if (!process.isAlive()) {
                    snapshot.put("exitCode", process.exitValue());
                }
                return snapshot;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    static class Json {
        static Object parse(String text) {
            return new Parser(text).parse();
        }

        static String stringify(Object value) {
            StringBuilder out = new StringBuilder();
            write(value, out);
            return out.toString();
        }

        private static void write(Object value, StringBuilder out) {
            if (value == null) {
                out.append("null");
            } else if (value instanceof String string) {
                writeString(string, out);
            } else if (value instanceof Number || value instanceof Boolean) {
                out.append(value);
            } else if (value instanceof Map<?, ?> map) {
                out.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    writeString(String.valueOf(entry.getKey()), out);
                    out.append(':');
                    write(entry.getValue(), out);
                }
                out.append('}');
            } else if (value instanceof Iterable<?> iterable) {
                out.append('[');
                boolean first = true;
                for (Object item : iterable) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    write(item, out);
                }
                out.append(']');
            } else {
                writeString(String.valueOf(value), out);
            }
        }

        private static void writeString(String value, StringBuilder out) {
            out.append('"');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\b' -> out.append("\\b");
                    case '\f' -> out.append("\\f");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            out.append(String.format("\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                    }
                }
            }
            out.append('"');
        }

        static class Parser {
            private final String text;
            private int index;

            Parser(String text) {
                this.text = text == null ? "" : text;
            }

            Object parse() {
                Object value = value();
                skipWhitespace();
                if (index != text.length()) {
                    throw new IllegalArgumentException("Unexpected JSON at position " + index);
                }
                return value;
            }

            private Object value() {
                skipWhitespace();
                if (index >= text.length()) {
                    throw new IllegalArgumentException("Unexpected end of JSON");
                }
                char c = text.charAt(index);
                return switch (c) {
                    case '{' -> object();
                    case '[' -> array();
                    case '"' -> string();
                    case 't' -> literal("true", Boolean.TRUE);
                    case 'f' -> literal("false", Boolean.FALSE);
                    case 'n' -> literal("null", null);
                    default -> {
                        if (c == '-' || Character.isDigit(c)) {
                            yield number();
                        }
                        throw new IllegalArgumentException("Unexpected JSON token at position " + index);
                    }
                };
            }

            private Map<String, Object> object() {
                expect('{');
                Map<String, Object> object = new LinkedHashMap<>();
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return object;
                }
                while (true) {
                    String key = string();
                    skipWhitespace();
                    expect(':');
                    object.put(key, value());
                    skipWhitespace();
                    if (peek('}')) {
                        index++;
                        return object;
                    }
                    expect(',');
                }
            }

            private List<Object> array() {
                expect('[');
                List<Object> array = new ArrayList<>();
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return array;
                }
                while (true) {
                    array.add(value());
                    skipWhitespace();
                    if (peek(']')) {
                        index++;
                        return array;
                    }
                    expect(',');
                }
            }

            private String string() {
                skipWhitespace();
                expect('"');
                StringBuilder value = new StringBuilder();
                while (index < text.length()) {
                    char c = text.charAt(index++);
                    if (c == '"') {
                        return value.toString();
                    }
                    if (c == '\\') {
                        if (index >= text.length()) {
                            throw new IllegalArgumentException("Invalid JSON escape");
                        }
                        char escaped = text.charAt(index++);
                        switch (escaped) {
                            case '"' -> value.append('"');
                            case '\\' -> value.append('\\');
                            case '/' -> value.append('/');
                            case 'b' -> value.append('\b');
                            case 'f' -> value.append('\f');
                            case 'n' -> value.append('\n');
                            case 'r' -> value.append('\r');
                            case 't' -> value.append('\t');
                            case 'u' -> {
                                if (index + 4 > text.length()) {
                                    throw new IllegalArgumentException("Invalid unicode escape");
                                }
                                value.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
                                index += 4;
                            }
                            default -> throw new IllegalArgumentException("Invalid JSON escape: " + escaped);
                        }
                    } else {
                        value.append(c);
                    }
                }
                throw new IllegalArgumentException("Unterminated JSON string");
            }

            private Object number() {
                int start = index;
                if (peek('-')) {
                    index++;
                }
                while (index < text.length() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
                boolean decimal = false;
                if (peek('.')) {
                    decimal = true;
                    index++;
                    while (index < text.length() && Character.isDigit(text.charAt(index))) {
                        index++;
                    }
                }
                if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                    decimal = true;
                    index++;
                    if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                        index++;
                    }
                    while (index < text.length() && Character.isDigit(text.charAt(index))) {
                        index++;
                    }
                }
                String raw = text.substring(start, index);
                return decimal ? Double.parseDouble(raw) : Long.parseLong(raw);
            }

            private Object literal(String literal, Object value) {
                if (!text.startsWith(literal, index)) {
                    throw new IllegalArgumentException("Expected " + literal + " at position " + index);
                }
                index += literal.length();
                return value;
            }

            private void expect(char expected) {
                skipWhitespace();
                if (index >= text.length() || text.charAt(index) != expected) {
                    throw new IllegalArgumentException("Expected '" + expected + "' at position " + index);
                }
                index++;
            }

            private boolean peek(char expected) {
                return index < text.length() && text.charAt(index) == expected;
            }

            private void skipWhitespace() {
                while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                    index++;
                }
            }
        }
    }

    private static final String FRONTEND = """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>AI YAPI Test Console</title>
              <style>
                :root {
                  color-scheme: dark;
                  --bg: #07111f;
                  --panel: rgba(255, 255, 255, .09);
                  --panel-strong: rgba(255, 255, 255, .16);
                  --text: #ecf6ff;
                  --muted: #9fb3c8;
                  --brand: #67e8f9;
                  --brand-2: #a78bfa;
                  --good: #34d399;
                  --warn: #fbbf24;
                  --bad: #fb7185;
                }
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  min-height: 100vh;
                  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                  background:
                    radial-gradient(circle at 20% 10%, rgba(103, 232, 249, .24), transparent 28rem),
                    radial-gradient(circle at 85% 5%, rgba(167, 139, 250, .22), transparent 28rem),
                    linear-gradient(135deg, #07111f 0%, #0f172a 48%, #111827 100%);
                  color: var(--text);
                  overflow-x: hidden;
                }
                .orb {
                  position: fixed;
                  width: 20rem;
                  height: 20rem;
                  border-radius: 999px;
                  filter: blur(50px);
                  opacity: .25;
                  pointer-events: none;
                  animation: float 12s ease-in-out infinite alternate;
                }
                .orb.one { background: #22d3ee; left: -5rem; bottom: 4rem; }
                .orb.two { background: #8b5cf6; right: -4rem; top: 8rem; animation-delay: -4s; }
                @keyframes float { from { transform: translateY(-1rem) scale(.95); } to { transform: translateY(2rem) scale(1.08); } }
                header {
                  padding: 3rem min(5vw, 4rem) 1.5rem;
                  position: relative;
                  z-index: 1;
                }
                .hero {
                  display: grid;
                  grid-template-columns: minmax(0, 1.3fr) minmax(22rem, .7fr);
                  gap: 1.2rem;
                  align-items: stretch;
                }
                .hero-card, .card {
                  background: linear-gradient(135deg, rgba(255,255,255,.13), rgba(255,255,255,.06));
                  border: 1px solid rgba(255,255,255,.16);
                  border-radius: 28px;
                  box-shadow: 0 20px 70px rgba(0,0,0,.28);
                  backdrop-filter: blur(18px);
                }
                .hero-card { padding: 2rem; overflow: hidden; position: relative; }
                .eyebrow {
                  display: inline-flex;
                  gap: .5rem;
                  align-items: center;
                  padding: .45rem .75rem;
                  border-radius: 999px;
                  background: rgba(103, 232, 249, .13);
                  color: #bff8ff;
                  font-weight: 700;
                  letter-spacing: .04em;
                  text-transform: uppercase;
                  font-size: .78rem;
                }
                h1 {
                  margin: 1rem 0 .75rem;
                  font-size: clamp(2.2rem, 5vw, 5rem);
                  line-height: .95;
                  letter-spacing: -.06em;
                }
                .gradient-text {
                  background: linear-gradient(90deg, #fff, #67e8f9 45%, #c4b5fd);
                  -webkit-background-clip: text;
                  color: transparent;
                }
                p { color: var(--muted); line-height: 1.7; }
                .stats {
                  display: grid;
                  grid-template-columns: repeat(3, 1fr);
                  gap: .8rem;
                  margin-top: 1.5rem;
                }
                .stat {
                  border-radius: 18px;
                  background: rgba(255,255,255,.08);
                  padding: 1rem;
                }
                .stat strong { display: block; font-size: 1.6rem; color: #fff; }
                .layout {
                  display: grid;
                  grid-template-columns: 25rem minmax(0, 1fr);
                  gap: 1rem;
                  padding: 0 min(5vw, 4rem) 4rem;
                  position: relative;
                  z-index: 1;
                }
                .card { padding: 1rem; }
                .card h2, .card h3 { margin: .3rem 0 1rem; }
                label { display: block; color: #c9d7e8; font-size: .86rem; margin: .8rem 0 .35rem; }
                input, textarea, select {
                  width: 100%;
                  border: 1px solid rgba(255,255,255,.14);
                  background: rgba(8, 15, 28, .72);
                  color: var(--text);
                  border-radius: 16px;
                  padding: .8rem .9rem;
                  outline: none;
                  transition: border .2s ease, box-shadow .2s ease, transform .2s ease;
                }
                textarea { min-height: 9rem; resize: vertical; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
                input:focus, textarea:focus, select:focus {
                  border-color: rgba(103, 232, 249, .75);
                  box-shadow: 0 0 0 4px rgba(103, 232, 249, .12);
                }
                button {
                  border: 0;
                  color: #04111d;
                  background: linear-gradient(135deg, var(--brand), var(--brand-2));
                  padding: .85rem 1rem;
                  border-radius: 16px;
                  font-weight: 800;
                  cursor: pointer;
                  transition: transform .18s ease, filter .18s ease, box-shadow .18s ease;
                  box-shadow: 0 12px 34px rgba(103,232,249,.22);
                }
                button:hover { transform: translateY(-2px); filter: brightness(1.05); }
                button.secondary {
                  color: var(--text);
                  background: rgba(255,255,255,.1);
                  box-shadow: none;
                  border: 1px solid rgba(255,255,255,.12);
                }
                .row { display: flex; gap: .65rem; align-items: center; flex-wrap: wrap; }
                .stack { display: grid; gap: 1rem; }
                .endpoint {
                  padding: .85rem;
                  border-radius: 18px;
                  background: rgba(255,255,255,.07);
                  border: 1px solid rgba(255,255,255,.1);
                  cursor: pointer;
                  transition: background .18s ease, transform .18s ease, border .18s ease;
                }
                .endpoint:hover, .endpoint.active {
                  transform: translateY(-2px);
                  background: rgba(103,232,249,.14);
                  border-color: rgba(103,232,249,.35);
                }
                .method {
                  display: inline-flex;
                  min-width: 4rem;
                  justify-content: center;
                  padding: .24rem .45rem;
                  border-radius: 999px;
                  background: rgba(52, 211, 153, .15);
                  color: #a7f3d0;
                  font-weight: 900;
                  font-size: .76rem;
                }
                pre {
                  margin: 0;
                  white-space: pre-wrap;
                  word-break: break-word;
                  background: rgba(2, 6, 23, .65);
                  border-radius: 18px;
                  border: 1px solid rgba(255,255,255,.1);
                  padding: 1rem;
                  max-height: 28rem;
                  overflow: auto;
                }
                .tabs { display: flex; gap: .5rem; margin-bottom: .8rem; flex-wrap: wrap; }
                .tab {
                  color: var(--text);
                  background: rgba(255,255,255,.08);
                  box-shadow: none;
                  padding: .65rem .85rem;
                }
                .tab.active { background: linear-gradient(135deg, var(--brand), var(--brand-2)); color: #06121f; }
                .panel { display: none; animation: rise .28s ease; }
                .panel.active { display: block; }
                @keyframes rise { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
                .pill {
                  display: inline-flex;
                  align-items: center;
                  gap: .35rem;
                  border-radius: 999px;
                  padding: .36rem .6rem;
                  background: rgba(255,255,255,.1);
                  color: #dcecff;
                  font-size: .82rem;
                }
                .status.good { color: var(--good); }
                .status.warn { color: var(--warn); }
                .status.bad { color: var(--bad); }
                .mini { font-size: .82rem; color: var(--muted); }
                @media (max-width: 1040px) {
                  .hero, .layout { grid-template-columns: 1fr; }
                }
              </style>
            </head>
            <body>
              <div class="orb one"></div>
              <div class="orb two"></div>
              <header>
                <div class="hero">
                  <section class="hero-card">
                    <span class="eyebrow">AI API Copilot</span>
                    <h1><span class="gradient-text">YAPI 参数读取</span><br/>一键填充与自动测试</h1>
                    <p>读取 YAPI/OpenAPI、扫描项目代码、生成接口参数、启动本地项目，并在接口完成后从前端页面和 API 两侧发起验证。</p>
                    <div class="stats">
                      <div class="stat"><strong id="statEndpoints">0</strong><span class="mini">YAPI 接口</span></div>
                      <div class="stat"><strong id="statFiles">0</strong><span class="mini">扫描文件</span></div>
                      <div class="stat"><strong id="statAi">OFF</strong><span class="mini">AI 增强</span></div>
                    </div>
                  </section>
                  <aside class="card">
                    <h2>快速启动</h2>
                    <p class="mini">根据项目结构推断启动命令，也可以输入自己的 dev 命令并在这里启动。</p>
                    <label>项目路径</label>
                    <input id="projectPath" value="" placeholder="/workspace" />
                    <label>启动命令</label>
                    <input id="startCommand" placeholder="自动推断或填写 npm run dev / mvn spring-boot:run" />
                    <div class="row" style="margin-top: .8rem">
                      <button onclick="quickStart(false)">推断命令</button>
                      <button class="secondary" onclick="quickStart(true)">启动项目</button>
                      <button class="secondary" onclick="refreshLogs()">刷新日志</button>
                    </div>
                    <pre id="logs" style="margin-top:.8rem; min-height: 8rem">等待启动...</pre>
                  </aside>
                </div>
              </header>

              <main class="layout">
                <aside class="card">
                  <h2>输入源</h2>
                  <label>YAPI/OpenAPI 文件路径或 URL</label>
                  <input id="yapiSource" value="examples/yapi-sample.json" />
                  <label>或粘贴 JSON 内容</label>
                  <textarea id="yapiContent" placeholder="粘贴 YAPI 导出 JSON / OpenAPI JSON"></textarea>
                  <div class="row" style="margin-top:.8rem">
                    <button onclick="analyze()">分析项目与接口</button>
                    <button class="secondary" onclick="scanOnly()">仅扫描代码</button>
                  </div>
                  <p id="message" class="mini"></p>
                  <h3>接口列表</h3>
                  <div id="endpointList" class="stack"></div>
                </aside>

                <section class="card">
                  <div class="tabs">
                    <button class="tab active" onclick="showTab('fill', this)">参数填充</button>
                    <button class="tab" onclick="showTab('test', this)">前端/API 测试</button>
                    <button class="tab" onclick="showTab('project', this)">项目扫描</button>
                  </div>

                  <div id="fill" class="panel active">
                    <div class="row">
                      <span class="pill" id="selectedEndpoint">未选择接口</span>
                      <label class="pill"><input type="checkbox" id="useAi" style="width:auto" /> 使用 AI 增强</label>
                      <button onclick="fillParams()">一键生成参数</button>
                      <button class="secondary" onclick="copyCurl()">复制 curl</button>
                    </div>
                    <pre id="fillOutput" style="margin-top:1rem">选择接口后点击一键生成参数。</pre>
                  </div>

                  <div id="test" class="panel">
                    <div class="row">
                      <div style="flex:1; min-width:14rem">
                        <label>前端页面 URL</label>
                        <input id="frontendUrl" placeholder="http://localhost:3000" />
                      </div>
                      <div style="flex:1; min-width:14rem">
                        <label>API Base URL</label>
                        <input id="apiBaseUrl" placeholder="http://localhost:8080" />
                      </div>
                    </div>
                    <p class="mini">测试会请求前端页面确认可访问，并用生成的参数调用选中接口。</p>
                    <button onclick="runTest()">运行测试</button>
                    <pre id="testOutput" style="margin-top:1rem">等待测试...</pre>
                  </div>

                  <div id="project" class="panel">
                    <div class="row" id="projectPills"></div>
                    <pre id="projectOutput" style="margin-top:1rem">等待扫描...</pre>
                  </div>
                </section>
              </main>

              <script>
                const $ = (id) => document.getElementById(id);
                const state = { endpoints: [], selected: null, filled: null, project: null, sessionId: null };
                $('projectPath').value = location.pathname === '/' ? '' : '/workspace';

                function setMessage(text, kind = 'warn') {
                  $('message').innerHTML = text ? `<span class="status ${kind}">${text}</span>` : '';
                }

                async function api(path, body, method = 'POST') {
                  const options = { method, headers: { 'Content-Type': 'application/json' } };
                  if (body) options.body = JSON.stringify(body);
                  const res = await fetch(path, options);
                  const json = await res.json();
                  if (!res.ok || json.ok === false) throw new Error(json.error || '请求失败');
                  return json;
                }

                async function analyze() {
                  setMessage('正在读取 YAPI 并扫描项目...', 'warn');
                  try {
                    const data = await api('/api/analyze', {
                      projectPath: $('projectPath').value,
                      yapiSource: $('yapiSource').value,
                      yapiContent: $('yapiContent').value
                    });
                    state.endpoints = data.endpoints || [];
                    state.project = data.project;
                    $('statEndpoints').textContent = data.endpointCount || 0;
                    $('statFiles').textContent = data.project?.filesScanned || 0;
                    $('statAi').textContent = data.aiConfigured ? 'ON' : 'OFF';
                    renderEndpoints();
                    renderProject();
                    setMessage(`完成：读取 ${state.endpoints.length} 个接口，扫描 ${data.project?.filesScanned || 0} 个代码文件。`, 'good');
                  } catch (error) {
                    setMessage(error.message, 'bad');
                  }
                }

                async function scanOnly() {
                  setMessage('正在扫描项目代码...', 'warn');
                  const path = encodeURIComponent($('projectPath').value || '');
                  const res = await fetch('/api/scan?path=' + path);
                  const data = await res.json();
                  state.project = data.project;
                  $('statFiles').textContent = data.project?.filesScanned || 0;
                  renderProject();
                  setMessage('项目扫描完成。', 'good');
                }

                function renderEndpoints() {
                  const list = $('endpointList');
                  list.innerHTML = '';
                  if (!state.endpoints.length) {
                    list.innerHTML = '<p class="mini">暂无接口，请导入 YAPI/OpenAPI。</p>';
                    return;
                  }
                  state.endpoints.forEach((endpoint, index) => {
                    const item = document.createElement('div');
                    item.className = 'endpoint' + (state.selected === endpoint ? ' active' : '');
                    item.innerHTML = `<div class="row"><span class="method">${endpoint.method || 'GET'}</span><strong>${endpoint.path || '(no path)'}</strong></div><p class="mini">${endpoint.title || endpoint.source || ''}</p>`;
                    item.onclick = () => selectEndpoint(index);
                    list.appendChild(item);
                  });
                }

                function selectEndpoint(index) {
                  state.selected = state.endpoints[index];
                  state.filled = null;
                  $('selectedEndpoint').textContent = `${state.selected.method || 'GET'} ${state.selected.path || ''}`;
                  $('fillOutput').textContent = JSON.stringify(state.selected, null, 2);
                  renderEndpoints();
                }

                async function fillParams() {
                  if (!state.selected) return setMessage('请先选择一个接口。', 'bad');
                  $('fillOutput').textContent = 'AI/规则引擎正在生成参数...';
                  const data = await api('/api/fill', {
                    projectPath: $('projectPath').value,
                    endpoint: state.selected,
                    useAi: $('useAi').checked
                  });
                  state.filled = data.filled;
                  $('fillOutput').textContent = JSON.stringify(data, null, 2);
                }

                async function runTest() {
                  if (!state.selected || !state.filled) return setMessage('请先选择接口并生成参数。', 'bad');
                  $('testOutput').textContent = '测试执行中...';
                  const data = await api('/api/test', {
                    frontendUrl: $('frontendUrl').value,
                    apiBaseUrl: $('apiBaseUrl').value,
                    apiPath: state.selected.path,
                    method: state.selected.method,
                    payload: state.filled
                  });
                  $('testOutput').textContent = JSON.stringify(data, null, 2);
                }

                function renderProject() {
                  const project = state.project || {};
                  $('projectPills').innerHTML = `
                    <span class="pill">Root: ${project.root || '-'}</span>
                    <span class="pill">Files: ${project.filesScanned || 0}</span>
                    <span class="pill">Code Endpoints: ${(project.endpoints || []).length}</span>
                    <span class="pill">Models: ${(project.models || []).length}</span>
                  `;
                  $('projectOutput').textContent = JSON.stringify(project, null, 2);
                }

                async function quickStart(run) {
                  const data = await api('/api/quick-start', {
                    projectPath: $('projectPath').value,
                    command: $('startCommand').value,
                    run
                  });
                  $('startCommand').value = data.command;
                  if (data.session) {
                    state.sessionId = data.session.id;
                    $('logs').textContent = data.session.logs || `已启动：${data.command}`;
                    setTimeout(refreshLogs, 800);
                  } else {
                    $('logs').textContent = `推荐命令：${data.command}`;
                  }
                }

                async function refreshLogs() {
                  if (!state.sessionId) return;
                  const res = await fetch('/api/quick-start/logs?id=' + encodeURIComponent(state.sessionId));
                  const data = await res.json();
                  $('logs').textContent = data.logs || '(暂无日志)';
                  if (data.running) setTimeout(refreshLogs, 1500);
                }

                function copyCurl() {
                  if (!state.filled?.curl) return;
                  navigator.clipboard?.writeText(state.filled.curl);
                  setMessage('curl 已复制。', 'good');
                }

                function showTab(id, button) {
                  document.querySelectorAll('.panel').forEach(panel => panel.classList.remove('active'));
                  document.querySelectorAll('.tab').forEach(tab => tab.classList.remove('active'));
                  $(id).classList.add('active');
                  button.classList.add('active');
                }

                analyze();
              </script>
            </body>
            </html>
            """;
}
