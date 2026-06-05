import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class AiDevAssistantServer {
    private static final int DEFAULT_PORT = 8718;
    private static final int MAX_SCAN_FILES = 260;
    private static final int MAX_FILE_BYTES = 220_000;
    private static final Set<String> CODE_EXTENSIONS = Set.of(
            ".java", ".js", ".jsx", ".ts", ".tsx", ".vue", ".py", ".go", ".kt",
            ".kts", ".rb", ".php", ".cs", ".yml", ".yaml", ".json", ".xml"
    );
    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", ".idea", ".vscode", "node_modules", "dist", "build", "target",
            "out", ".gradle", ".mvn", "coverage", ".next", ".nuxt"
    );

    private final Path workspace;
    private final HttpClient httpClient;

    private AiDevAssistantServer(Path workspace) {
        this.workspace = workspace;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public static void main(String[] args) throws Exception {
        int port = resolvePort(args);
        Path workspace = Path.of("").toAbsolutePath().normalize();
        AiDevAssistantServer app = new AiDevAssistantServer(workspace);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", app::handleUi);
        server.createContext("/api/health", app::handleHealth);
        server.createContext("/api/project/scan", app::handleProjectScan);
        server.createContext("/api/yapi/import", app::handleYapiImport);
        server.createContext("/api/ai/fill", app::handleAiFill);
        server.createContext("/api/test/run", app::handleTestRun);
        server.createContext("/api/ai/quick-start", app::handleQuickStart);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.printf(Locale.ROOT, "AI Dev Assistant is running at http://localhost:%d/%n", port);
        System.out.printf(Locale.ROOT, "Workspace: %s%n", workspace);
    }

    private static int resolvePort(String[] args) {
        if (args.length > 0 && args[0].matches("\\d+")) {
            return Integer.parseInt(args[0]);
        }
        String env = System.getenv("AI_DEV_PORT");
        if (env != null && env.matches("\\d+")) {
            return Integer.parseInt(env);
        }
        return DEFAULT_PORT;
    }

    private void handleUi(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        send(exchange, 200, "text/html; charset=utf-8", Ui.html());
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, Map.of(
                "ok", true,
                "workspace", workspace.toString(),
                "aiConfigured", aiConfigured(),
                "time", Instant.now().toString()
        ));
    }

    private void handleProjectScan(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            ProjectSnapshot snapshot = scanProject();
            sendJson(exchange, 200, snapshot.toMap());
        } catch (Exception e) {
            sendJson(exchange, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleYapiImport(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map<String, Object> request = readJsonObject(exchange);
            String yapiText = jsonTextValue(request.get("yapiText"));
            String yapiUrl = stringValue(request.get("yapiUrl"));
            String token = stringValue(request.get("yapiToken"));
            if (!yapiUrl.isBlank()) {
                yapiText = fetchYapi(yapiUrl, token);
            }
            if (yapiText.isBlank()) {
                sendJson(exchange, 400, Map.of("error", "请提供 yapiText 或 yapiUrl"));
                return;
            }
            List<EndpointSpec> endpoints = YapiParser.parse(yapiText);
            sendJson(exchange, 200, Map.of(
                    "count", endpoints.size(),
                    "endpoints", endpoints.stream().map(EndpointSpec::toMap).toList()
            ));
        } catch (Exception e) {
            sendJson(exchange, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleAiFill(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map<String, Object> request = readJsonObject(exchange);
            EndpointSpec endpoint = endpointFromRequest(request.get("endpoint"));
            if (endpoint.path.isBlank() && endpoint.params.isEmpty()) {
                sendJson(exchange, 400, Map.of("error", "请提供 endpoint，至少包含 path/method/params"));
                return;
            }
            ProjectSnapshot snapshot = scanProject();
            Map<String, Object> values = fillParameters(endpoint, snapshot);
            sendJson(exchange, 200, Map.of(
                    "endpoint", endpoint.toMap(),
                    "params", values,
                    "source", aiConfigured() ? "ai-with-heuristic-fallback" : "local-heuristic",
                    "matchedCode", snapshot.relatedFiles(endpoint.path).stream().map(CodeFile::toMap).toList()
            ));
        } catch (Exception e) {
            sendJson(exchange, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleTestRun(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map<String, Object> request = readJsonObject(exchange);
            TestResult result = runApiTest(request);
            Map<String, Object> response = new LinkedHashMap<>(result.toMap());
            String frontendUrl = stringValue(request.get("frontendUrl"));
            if (!frontendUrl.isBlank()) {
                response.put("frontendProbe", probeFrontend(frontendUrl));
            }
            sendJson(exchange, 200, response);
        } catch (Exception e) {
            sendJson(exchange, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleQuickStart(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        try {
            Map<String, Object> request = readJsonObject(exchange);
            ProjectSnapshot snapshot = scanProject();
            List<EndpointSpec> endpoints = new ArrayList<>();
            String yapiText = jsonTextValue(request.get("yapiText"));
            if (!yapiText.isBlank()) {
                endpoints.addAll(YapiParser.parse(yapiText));
            }
            Object endpointObj = request.get("endpoint");
            if (endpointObj != null) {
                endpoints.add(endpointFromRequest(endpointObj));
            }
            if (endpoints.isEmpty() && !snapshot.endpoints.isEmpty()) {
                CodeEndpoint first = snapshot.endpoints.get(0);
                endpoints.add(new EndpointSpec(first.method, first.path, "代码扫描发现的接口", List.of()));
            }

            List<Map<String, Object>> executions = new ArrayList<>();
            String baseUrl = stringValue(request.get("baseUrl"));
            for (EndpointSpec endpoint : endpoints.stream().limit(5).toList()) {
                Map<String, Object> params = fillParameters(endpoint, snapshot);
                Map<String, Object> execution = new LinkedHashMap<>();
                execution.put("endpoint", endpoint.toMap());
                execution.put("params", params);
                if (!baseUrl.isBlank()) {
                    Map<String, Object> testRequest = new LinkedHashMap<>();
                    testRequest.put("method", endpoint.method);
                    testRequest.put("url", joinUrl(baseUrl, endpoint.path));
                    testRequest.put("params", params);
                    execution.put("test", runApiTest(testRequest).toMap());
                } else {
                    execution.put("test", Map.of("skipped", true, "reason", "未提供 baseUrl，仅完成参数生成"));
                }
                executions.add(execution);
            }

            sendJson(exchange, 200, Map.of(
                    "workspace", workspace.toString(),
                    "project", snapshot.toMap(),
                    "executions", executions,
                    "nextSteps", List.of(
                            "在页面中导入 YAPI JSON 或填写 YAPI URL",
                            "点击 AI 一键填参生成请求参数",
                            "配置 baseUrl/frontendUrl 后运行接口和页面联测",
                            "接口开发完成后重复运行快捷启动工作流完成回归"
                    )
            ));
        } catch (Exception e) {
            sendJson(exchange, 500, Map.of("error", e.getMessage()));
        }
    }

    private ProjectSnapshot scanProject() throws IOException {
        List<CodeFile> files = new ArrayList<>();
        List<CodeEndpoint> endpoints = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(workspace)) {
            List<Path> candidates = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isScannable)
                    .sorted(Comparator.comparing(path -> workspace.relativize(path).toString()))
                    .limit(MAX_SCAN_FILES)
                    .toList();
            for (Path path : candidates) {
                String content = readSmallFile(path);
                if (content == null) {
                    continue;
                }
                String relative = workspace.relativize(path).toString();
                CodeFile codeFile = new CodeFile(relative, content.length(), summarize(content));
                files.add(codeFile);
                endpoints.addAll(CodeEndpointExtractor.extract(relative, content));
            }
        }
        return new ProjectSnapshot(workspace.toString(), files, endpoints);
    }

    private boolean isScannable(Path path) {
        Path relative = workspace.relativize(path);
        for (Path part : relative) {
            if (IGNORED_DIRS.contains(part.toString())) {
                return false;
            }
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return CODE_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private String readSmallFile(Path path) throws IOException {
        if (Files.size(path) > MAX_FILE_BYTES) {
            return null;
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private String summarize(String content) {
        String compact = content.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return compact.length() > 900 ? compact.substring(0, 900) + "..." : compact;
    }

    private String fetchYapi(String yapiUrl, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(yapiUrl))
                .timeout(Duration.ofSeconds(15))
                .GET();
        if (!token.isBlank()) {
            builder.header("Authorization", token);
            builder.header("token", token);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("YAPI 请求失败: HTTP " + response.statusCode());
        }
        return response.body();
    }

    private Map<String, Object> fillParameters(EndpointSpec endpoint, ProjectSnapshot snapshot) {
        Map<String, Object> heuristic = ParameterFiller.fill(endpoint, snapshot);
        if (!aiConfigured()) {
            return heuristic;
        }
        try {
            Optional<Map<String, Object>> aiValues = requestAiFill(endpoint, snapshot);
            aiValues.ifPresent(heuristic::putAll);
        } catch (Exception ignored) {
            heuristic.put("_aiWarning", "AI 调用失败，已使用本地启发式填参");
        }
        return heuristic;
    }

    private Optional<Map<String, Object>> requestAiFill(EndpointSpec endpoint, ProjectSnapshot snapshot) throws Exception {
        String apiKey = System.getenv("AI_API_KEY");
        String baseUrl = Optional.ofNullable(System.getenv("AI_BASE_URL")).orElse("https://api.openai.com/v1");
        String model = Optional.ofNullable(System.getenv("AI_MODEL")).orElse("gpt-4o-mini");
        String prompt = """
                你是接口测试参数生成助手。请基于 YAPI 参数和项目代码摘要，生成可直接用于测试的 JSON 对象。
                只输出 JSON，不要 Markdown。
                Endpoint:
                %s
                Related code:
                %s
                """.formatted(
                Json.stringify(endpoint.toMap()),
                Json.stringify(snapshot.relatedFiles(endpoint.path).stream().map(CodeFile::toMap).toList())
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("temperature", 0.2);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", "你只返回一个 JSON object。"),
                Map.of("role", "user", "content", prompt)
        ));
        HttpRequest request = HttpRequest.newBuilder(URI.create(trimRight(baseUrl, "/") + "/chat/completions"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return Optional.empty();
        }
        Object parsed = Json.parse(response.body());
        Object content = dig(parsed, "choices", 0, "message", "content");
        if (!(content instanceof String text)) {
            return Optional.empty();
        }
        String json = extractJsonObject(text);
        Object values = Json.parse(json);
        if (values instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return Optional.of(result);
        }
        return Optional.empty();
    }

    private TestResult runApiTest(Map<String, Object> request) throws Exception {
        String method = stringValue(request.getOrDefault("method", "GET")).toUpperCase(Locale.ROOT);
        String url = stringValue(request.get("url"));
        if (url.isBlank()) {
            return new TestResult(false, 0, 0, "", "缺少 url", Map.of());
        }
        Map<String, Object> params = objectMap(request.get("params"));
        Map<String, Object> headers = objectMap(request.get("headers"));
        Object body = request.get("body");
        if (body == null && !List.of("GET", "DELETE").contains(method)) {
            body = params;
        }
        String finalUrl = List.of("GET", "DELETE").contains(method) && !params.isEmpty()
                ? appendQuery(url, params)
                : url;

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(finalUrl))
                .timeout(Duration.ofSeconds(20));
        headers.forEach((key, value) -> builder.header(key, String.valueOf(value)));
        if (!headers.keySet().stream().map(key -> key.toLowerCase(Locale.ROOT)).toList().contains("content-type")) {
            builder.header("Content-Type", "application/json");
        }
        String requestBody = body == null ? "" : (body instanceof String text ? text : Json.stringify(body));
        switch (method) {
            case "GET" -> builder.GET();
            case "DELETE" -> builder.DELETE();
            default -> builder.method(method, HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
        }

        Instant start = Instant.now();
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        long elapsed = Duration.between(start, Instant.now()).toMillis();
        boolean ok = response.statusCode() >= 200 && response.statusCode() < 400;
        return new TestResult(ok, response.statusCode(), elapsed, truncate(response.body(), 5000), "", Map.of(
                "url", finalUrl,
                "method", method,
                "requestBody", requestBody
        ));
    }

    private Map<String, Object> probeFrontend(String frontendUrl) throws Exception {
        Instant start = Instant.now();
        HttpRequest request = HttpRequest.newBuilder(URI.create(frontendUrl))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = response.body();
        String title = firstMatch(body, Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)).orElse("");
        List<String> scripts = allMatches(body, Pattern.compile("<script[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE), 10);
        List<String> styles = allMatches(body, Pattern.compile("<link[^>]+href=[\"']([^\"']+)[\"'][^>]*>", Pattern.CASE_INSENSITIVE), 10);
        return Map.of(
                "ok", response.statusCode() >= 200 && response.statusCode() < 400,
                "status", response.statusCode(),
                "elapsedMs", Duration.between(start, Instant.now()).toMillis(),
                "title", title,
                "scripts", scripts,
                "styles", styles,
                "bodyPreview", truncate(body.replaceAll("\\s+", " ").trim(), 600)
        );
    }

    private Map<String, Object> readJsonObject(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.isBlank()) {
            return new LinkedHashMap<>();
        }
        Object parsed = Json.parse(body);
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        throw new IOException("请求体必须是 JSON object");
    }

    private EndpointSpec endpointFromRequest(Object value) {
        Map<String, Object> map = objectMap(value);
        String method = stringValue(map.getOrDefault("method", "GET")).toUpperCase(Locale.ROOT);
        String path = stringValue(map.get("path"));
        String title = stringValue(map.get("title"));
        List<ParamSpec> params = new ArrayList<>();
        Object paramsObj = map.get("params");
        if (paramsObj instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> paramMap = objectMap(item);
                String name = stringValue(paramMap.get("name"));
                if (!name.isBlank()) {
                    params.add(new ParamSpec(
                            name,
                            stringValue(paramMap.getOrDefault("type", "string")),
                            booleanValue(paramMap.get("required")),
                            stringValue(paramMap.get("example")),
                            stringValue(paramMap.get("source")),
                            stringValue(paramMap.get("description"))
                    ));
                }
            }
        }
        return new EndpointSpec(method, path, title, params);
    }

    private boolean aiConfigured() {
        String key = System.getenv("AI_API_KEY");
        return key != null && !key.isBlank();
    }

    private void sendJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        send(exchange, statusCode, "application/json; charset=utf-8", Json.stringify(payload));
    }

    private void send(HttpExchange exchange, int statusCode, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "content-type, authorization");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private static String appendQuery(String url, Map<String, Object> params) {
        StringBuilder builder = new StringBuilder(url);
        builder.append(url.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!first) {
                builder.append("&");
            }
            first = false;
            builder.append(encode(entry.getKey())).append("=").append(encode(String.valueOf(entry.getValue())));
        }
        return builder.toString();
    }

    private static String joinUrl(String baseUrl, String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return trimRight(baseUrl, "/") + "/" + trimLeft(path, "/");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimLeft(String value, String token) {
        String result = value;
        while (result.startsWith(token)) {
            result = result.substring(token.length());
        }
        return result;
    }

    private static String trimRight(String value, String token) {
        String result = value;
        while (result.endsWith(token)) {
            result = result.substring(0, result.length() - token.length());
        }
        return result;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String jsonTextValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text.trim();
        }
        return Json.stringify(value);
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = stringValue(value).toLowerCase(Locale.ROOT);
        return text.equals("true") || text.equals("1") || text.equals("yes") || text.equals("required");
    }

    private static Map<String, Object> objectMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
        }
        return result;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    private static Optional<String> firstMatch(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group(1)).map(String::trim);
        }
        return Optional.empty();
    }

    private static List<String> allMatches(String text, Pattern pattern, int limit) {
        List<String> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find() && matches.size() < limit) {
            matches.add(matcher.group(1));
        }
        return matches;
    }

    private static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private static Object dig(Object value, Object... path) {
        Object current = value;
        for (Object part : path) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else if (current instanceof List<?> list && part instanceof Integer index && index >= 0 && index < list.size()) {
                current = list.get(index);
            } else {
                return null;
            }
        }
        return current;
    }

    record ProjectSnapshot(String workspace, List<CodeFile> files, List<CodeEndpoint> endpoints) {
        Map<String, Object> toMap() {
            return Map.of(
                    "workspace", workspace,
                    "fileCount", files.size(),
                    "endpointCount", endpoints.size(),
                    "files", files.stream().map(CodeFile::toMap).toList(),
                    "endpoints", endpoints.stream().map(CodeEndpoint::toMap).toList()
            );
        }

        List<CodeFile> relatedFiles(String endpointPath) {
            String normalized = endpointPath == null ? "" : endpointPath.replaceAll("[{}]", "").toLowerCase(Locale.ROOT);
            String compact = normalized.replaceAll("[^a-z0-9]", "");
            return files.stream()
                    .filter(file -> {
                        String haystack = (file.path + " " + file.summary).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                        return !compact.isBlank() && haystack.contains(compact.length() > 18 ? compact.substring(0, 18) : compact);
                    })
                    .limit(8)
                    .toList();
        }
    }

    record CodeFile(String path, int size, String summary) {
        Map<String, Object> toMap() {
            return Map.of("path", path, "size", size, "summary", summary);
        }
    }

    record CodeEndpoint(String method, String path, String file, String source) {
        Map<String, Object> toMap() {
            return Map.of("method", method, "path", path, "file", file, "source", source);
        }
    }

    record EndpointSpec(String method, String path, String title, List<ParamSpec> params) {
        Map<String, Object> toMap() {
            return Map.of(
                    "method", method == null || method.isBlank() ? "GET" : method,
                    "path", path == null ? "" : path,
                    "title", title == null ? "" : title,
                    "params", params.stream().map(ParamSpec::toMap).toList()
            );
        }
    }

    record ParamSpec(String name, String type, boolean required, String example, String source, String description) {
        Map<String, Object> toMap() {
            return Map.of(
                    "name", name,
                    "type", type == null || type.isBlank() ? "string" : type,
                    "required", required,
                    "example", example == null ? "" : example,
                    "source", source == null ? "" : source,
                    "description", description == null ? "" : description
            );
        }
    }

    record TestResult(boolean ok, int status, long elapsedMs, String body, String error, Map<String, Object> request) {
        Map<String, Object> toMap() {
            return Map.of(
                    "ok", ok,
                    "status", status,
                    "elapsedMs", elapsedMs,
                    "body", body,
                    "error", error,
                    "request", request
            );
        }
    }

    static class CodeEndpointExtractor {
        private static final Pattern SPRING_MAPPING = Pattern.compile(
                "@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\\s*(?:\\(([^)]*)\\))?",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        private static final Pattern ROUTER_MAPPING = Pattern.compile(
                "(router|app)\\s*\\.\\s*(get|post|put|delete|patch)\\s*\\(\\s*[\"'`]([^\"'`]+)[\"'`]",
                Pattern.CASE_INSENSITIVE
        );
        private static final Pattern CLIENT_CALL = Pattern.compile(
                "(fetch\\s*\\(\\s*[\"'`]([^\"'`]+)[\"'`]|axios\\s*\\.\\s*(get|post|put|delete|patch)\\s*\\(\\s*[\"'`]([^\"'`]+)[\"'`])",
                Pattern.CASE_INSENSITIVE
        );
        private static final Pattern HTTP_SERVER_CONTEXT = Pattern.compile(
                "createContext\\s*\\(\\s*[\"']([^\"']+)[\"']",
                Pattern.CASE_INSENSITIVE
        );

        static List<CodeEndpoint> extract(String file, String content) {
            List<CodeEndpoint> endpoints = new ArrayList<>();
            Matcher spring = SPRING_MAPPING.matcher(content);
            while (spring.find()) {
                String annotation = spring.group(1);
                String args = spring.group(2) == null ? "" : spring.group(2);
                Optional<String> path = firstMatch(args, Pattern.compile("[\"']([^\"']+)[\"']"));
                path.ifPresent(value -> endpoints.add(new CodeEndpoint(methodForSpring(annotation, args), value, file, "spring-annotation")));
            }
            Matcher router = ROUTER_MAPPING.matcher(content);
            while (router.find()) {
                endpoints.add(new CodeEndpoint(router.group(2).toUpperCase(Locale.ROOT), router.group(3), file, "server-route"));
            }
            Matcher client = CLIENT_CALL.matcher(content);
            while (client.find()) {
                String method = client.group(3) == null ? "GET" : client.group(3).toUpperCase(Locale.ROOT);
                String path = client.group(2) == null ? client.group(4) : client.group(2);
                endpoints.add(new CodeEndpoint(method, path, file, "client-call"));
            }
            Matcher context = HTTP_SERVER_CONTEXT.matcher(content);
            while (context.find()) {
                endpoints.add(new CodeEndpoint("HTTP", context.group(1), file, "http-server-context"));
            }
            return dedupe(endpoints);
        }

        private static String methodForSpring(String annotation, String args) {
            return switch (annotation.toLowerCase(Locale.ROOT)) {
                case "getmapping" -> "GET";
                case "postmapping" -> "POST";
                case "putmapping" -> "PUT";
                case "deletemapping" -> "DELETE";
                case "patchmapping" -> "PATCH";
                default -> {
                    Matcher matcher = Pattern.compile("method\\s*=\\s*RequestMethod\\.([A-Z]+)").matcher(args);
                    yield matcher.find() ? matcher.group(1) : "GET";
                }
            };
        }

        private static List<CodeEndpoint> dedupe(List<CodeEndpoint> endpoints) {
            Set<String> seen = new HashSet<>();
            List<CodeEndpoint> result = new ArrayList<>();
            for (CodeEndpoint endpoint : endpoints) {
                String key = endpoint.method + " " + endpoint.path + " " + endpoint.file;
                if (seen.add(key)) {
                    result.add(endpoint);
                }
            }
            return result;
        }
    }

    static class YapiParser {
        static List<EndpointSpec> parse(String text) {
            Object root = Json.parse(text);
            List<EndpointSpec> endpoints = new ArrayList<>();
            walk(root, endpoints);
            return endpoints;
        }

        private static void walk(Object value, List<EndpointSpec> endpoints) {
            if (value instanceof Map<?, ?> rawMap) {
                Map<String, Object> map = new LinkedHashMap<>();
                rawMap.forEach((key, item) -> map.put(String.valueOf(key), item));
                if (looksLikeEndpoint(map)) {
                    endpoints.add(toEndpoint(map));
                }
                map.values().forEach(item -> walk(item, endpoints));
            } else if (value instanceof List<?> list) {
                list.forEach(item -> walk(item, endpoints));
            }
        }

        private static boolean looksLikeEndpoint(Map<String, Object> map) {
            return map.containsKey("path")
                    && (map.containsKey("method") || map.containsKey("req_query") || map.containsKey("req_body_form") || map.containsKey("req_params"));
        }

        private static EndpointSpec toEndpoint(Map<String, Object> map) {
            String method = stringValue(map.getOrDefault("method", "GET")).toUpperCase(Locale.ROOT);
            String path = stringValue(map.get("path"));
            String title = stringValue(map.getOrDefault("title", map.getOrDefault("name", "")));
            List<ParamSpec> params = new ArrayList<>();
            addParams(params, map.get("req_params"), "path");
            addParams(params, map.get("req_query"), "query");
            addParams(params, map.get("req_body_form"), "body-form");
            addJsonBodyParams(params, map.get("req_body_other"));
            return new EndpointSpec(method, path, title, dedupeParams(params));
        }

        private static void addParams(List<ParamSpec> params, Object value, String source) {
            if (!(value instanceof List<?> list)) {
                return;
            }
            for (Object item : list) {
                Map<String, Object> map = objectMap(item);
                String name = firstNonBlank(map, "name", "field", "key");
                if (name.isBlank()) {
                    continue;
                }
                params.add(new ParamSpec(
                        name,
                        firstNonBlank(map, "type", "field_type"),
                        booleanValue(map.getOrDefault("required", map.get("require"))),
                        firstNonBlank(map, "example", "value", "default"),
                        source,
                        firstNonBlank(map, "desc", "description", "remark")
                ));
            }
        }

        private static void addJsonBodyParams(List<ParamSpec> params, Object value) {
            String body = stringValue(value);
            if (body.isBlank()) {
                return;
            }
            try {
                Object parsed = Json.parse(body);
                if (parsed instanceof Map<?, ?> map) {
                    map.forEach((key, item) -> params.add(new ParamSpec(
                            String.valueOf(key),
                            item == null ? "string" : item.getClass().getSimpleName().toLowerCase(Locale.ROOT),
                            false,
                            item == null ? "" : String.valueOf(item),
                            "body-json",
                            "从 req_body_other 示例推断"
                    )));
                }
            } catch (RuntimeException ignored) {
                Matcher matcher = Pattern.compile("\"([A-Za-z_][A-Za-z0-9_]*)\"\\s*:").matcher(body);
                while (matcher.find()) {
                    params.add(new ParamSpec(matcher.group(1), "string", false, "", "body-json", "从 JSON 字段名推断"));
                }
            }
        }

        private static List<ParamSpec> dedupeParams(List<ParamSpec> params) {
            Set<String> seen = new HashSet<>();
            List<ParamSpec> result = new ArrayList<>();
            for (ParamSpec param : params) {
                if (seen.add(param.name)) {
                    result.add(param);
                }
            }
            return result;
        }

        private static String firstNonBlank(Map<String, Object> map, String... keys) {
            for (String key : keys) {
                String value = stringValue(map.get(key));
                if (!value.isBlank()) {
                    return value;
                }
            }
            return "";
        }
    }

    static class ParameterFiller {
        static Map<String, Object> fill(EndpointSpec endpoint, ProjectSnapshot snapshot) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (ParamSpec param : endpoint.params) {
                if (param.example != null && !param.example.isBlank()) {
                    values.put(param.name, parseExample(param.example, param.type));
                } else {
                    values.put(param.name, guessValue(param.name, param.type, endpoint, snapshot));
                }
            }
            applyCommonDefaults(values, endpoint);
            return values;
        }

        private static Object parseExample(String example, String type) {
            String trimmed = example.trim();
            if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")) {
                return Boolean.parseBoolean(trimmed);
            }
            if (isNumberType(type) && trimmed.matches("-?\\d+(\\.\\d+)?")) {
                return trimmed.contains(".") ? Double.parseDouble(trimmed) : Long.parseLong(trimmed);
            }
            return trimmed;
        }

        private static Object guessValue(String name, String type, EndpointSpec endpoint, ProjectSnapshot snapshot) {
            String key = name.toLowerCase(Locale.ROOT);
            if (key.contains("page") && (key.contains("size") || key.contains("limit"))) {
                return 20;
            }
            if (key.equals("page") || key.contains("pageno") || key.contains("pageindex")) {
                return 1;
            }
            if (key.contains("trace") || key.contains("requestid") || key.contains("correlation")) {
                return "trace-" + UUID.randomUUID().toString().substring(0, 8);
            }
            if (key.contains("id")) {
                return isNumberType(type) ? 1001 : name + "_1001";
            }
            if (key.contains("phone") || key.contains("mobile")) {
                return "13800138000";
            }
            if (key.contains("email")) {
                return "demo@example.com";
            }
            if (key.contains("token")) {
                return "dev-token-" + UUID.randomUUID().toString().substring(0, 8);
            }
            if (key.contains("name")) {
                return endpoint.title == null || endpoint.title.isBlank() ? "demo_name" : endpoint.title.replaceAll("\\s+", "_");
            }
            if (key.contains("date") || key.contains("time")) {
                return Instant.now().toString();
            }
            if (key.startsWith("is") || key.contains("enable") || key.contains("active") || "boolean".equalsIgnoreCase(type)) {
                return true;
            }
            if (isNumberType(type)) {
                return 1;
            }
            if (key.contains("url")) {
                return "https://example.com/demo";
            }
            return "demo_" + name;
        }

        private static void applyCommonDefaults(Map<String, Object> values, EndpointSpec endpoint) {
            String path = endpoint.path == null ? "" : endpoint.path.toLowerCase(Locale.ROOT);
            if (values.isEmpty() && path.contains("list")) {
                values.put("pageNo", 1);
                values.put("pageSize", 20);
            }
            if (path.contains("login")) {
                values.putIfAbsent("username", "demo_user");
                values.putIfAbsent("password", "Passw0rd!");
            }
        }

        private static boolean isNumberType(String type) {
            String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT);
            return normalized.contains("int") || normalized.contains("long") || normalized.contains("double")
                    || normalized.contains("float") || normalized.contains("number");
        }
    }

    static class Json {
        static Object parse(String text) {
            return new Parser(text).parse();
        }

        static String stringify(Object value) {
            StringBuilder builder = new StringBuilder();
            write(builder, value);
            return builder.toString();
        }

        private static void write(StringBuilder builder, Object value) {
            if (value == null) {
                builder.append("null");
            } else if (value instanceof String text) {
                builder.append('"').append(escape(text)).append('"');
            } else if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
            } else if (value instanceof Map<?, ?> map) {
                builder.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) {
                        builder.append(',');
                    }
                    first = false;
                    write(builder, String.valueOf(entry.getKey()));
                    builder.append(':');
                    write(builder, entry.getValue());
                }
                builder.append('}');
            } else if (value instanceof Iterable<?> iterable) {
                builder.append('[');
                boolean first = true;
                for (Object item : iterable) {
                    if (!first) {
                        builder.append(',');
                    }
                    first = false;
                    write(builder, item);
                }
                builder.append(']');
            } else {
                write(builder, String.valueOf(value));
            }
        }

        private static String escape(String text) {
            return text.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }

        static class Parser {
            private final String text;
            private int index;

            Parser(String text) {
                this.text = text == null ? "" : text;
            }

            Object parse() {
                skipWhitespace();
                Object value = readValue();
                skipWhitespace();
                return value;
            }

            private Object readValue() {
                skipWhitespace();
                if (index >= text.length()) {
                    throw new IllegalArgumentException("Unexpected end of JSON");
                }
                char current = text.charAt(index);
                return switch (current) {
                    case '{' -> readObject();
                    case '[' -> readArray();
                    case '"' -> readString();
                    case 't', 'f' -> readBoolean();
                    case 'n' -> readNull();
                    default -> readNumber();
                };
            }

            private Map<String, Object> readObject() {
                expect('{');
                Map<String, Object> map = new LinkedHashMap<>();
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return map;
                }
                while (true) {
                    String key = readString();
                    skipWhitespace();
                    expect(':');
                    map.put(key, readValue());
                    skipWhitespace();
                    if (peek('}')) {
                        index++;
                        return map;
                    }
                    expect(',');
                    skipWhitespace();
                }
            }

            private List<Object> readArray() {
                expect('[');
                List<Object> list = new ArrayList<>();
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return list;
                }
                while (true) {
                    list.add(readValue());
                    skipWhitespace();
                    if (peek(']')) {
                        index++;
                        return list;
                    }
                    expect(',');
                }
            }

            private String readString() {
                expect('"');
                StringBuilder builder = new StringBuilder();
                while (index < text.length()) {
                    char current = text.charAt(index++);
                    if (current == '"') {
                        return builder.toString();
                    }
                    if (current == '\\') {
                        if (index >= text.length()) {
                            break;
                        }
                        char escaped = text.charAt(index++);
                        switch (escaped) {
                            case '"' -> builder.append('"');
                            case '\\' -> builder.append('\\');
                            case '/' -> builder.append('/');
                            case 'b' -> builder.append('\b');
                            case 'f' -> builder.append('\f');
                            case 'n' -> builder.append('\n');
                            case 'r' -> builder.append('\r');
                            case 't' -> builder.append('\t');
                            case 'u' -> {
                                String hex = text.substring(index, Math.min(index + 4, text.length()));
                                builder.append((char) Integer.parseInt(hex, 16));
                                index += 4;
                            }
                            default -> builder.append(escaped);
                        }
                    } else {
                        builder.append(current);
                    }
                }
                throw new IllegalArgumentException("Unterminated string");
            }

            private Boolean readBoolean() {
                if (text.startsWith("true", index)) {
                    index += 4;
                    return true;
                }
                if (text.startsWith("false", index)) {
                    index += 5;
                    return false;
                }
                throw new IllegalArgumentException("Invalid boolean");
            }

            private Object readNull() {
                if (text.startsWith("null", index)) {
                    index += 4;
                    return null;
                }
                throw new IllegalArgumentException("Invalid null");
            }

            private Number readNumber() {
                int start = index;
                while (index < text.length()) {
                    char current = text.charAt(index);
                    if ((current >= '0' && current <= '9') || current == '-' || current == '+' || current == '.' || current == 'e' || current == 'E') {
                        index++;
                    } else {
                        break;
                    }
                }
                String number = text.substring(start, index);
                if (number.isBlank()) {
                    throw new IllegalArgumentException("Invalid JSON at " + index);
                }
                if (number.contains(".") || number.contains("e") || number.contains("E")) {
                    return Double.parseDouble(number);
                }
                return Long.parseLong(number);
            }

            private void skipWhitespace() {
                while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                    index++;
                }
            }

            private boolean peek(char expected) {
                return index < text.length() && text.charAt(index) == expected;
            }

            private void expect(char expected) {
                if (index >= text.length() || text.charAt(index) != expected) {
                    throw new IllegalArgumentException("Expected '" + expected + "' at " + index);
                }
                index++;
            }
        }
    }

    static class Ui {
        static String html() {
            return """
                    <!doctype html>
                    <html lang="zh-CN">
                    <head>
                      <meta charset="utf-8">
                      <meta name="viewport" content="width=device-width, initial-scale=1">
                      <title>AI 接口开发测试控制台</title>
                      <style>
                        :root {
                          color-scheme: dark;
                          --bg: #070a13;
                          --panel: rgba(15, 23, 42, .72);
                          --panel-strong: rgba(30, 41, 59, .92);
                          --line: rgba(148, 163, 184, .18);
                          --text: #e5eefb;
                          --muted: #94a3b8;
                          --accent: #8b5cf6;
                          --accent-2: #06b6d4;
                          --ok: #22c55e;
                          --warn: #f59e0b;
                          --danger: #fb7185;
                        }
                        * { box-sizing: border-box; }
                        body {
                          margin: 0;
                          min-height: 100vh;
                          font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                          color: var(--text);
                          background:
                            radial-gradient(circle at 12% 10%, rgba(139, 92, 246, .35), transparent 26rem),
                            radial-gradient(circle at 82% 0%, rgba(6, 182, 212, .25), transparent 24rem),
                            linear-gradient(135deg, #070a13 0%, #0f172a 48%, #111827 100%);
                        }
                        .shell { width: min(1440px, calc(100vw - 40px)); margin: 0 auto; padding: 34px 0 48px; }
                        .hero {
                          display: grid;
                          grid-template-columns: minmax(0, 1.1fr) minmax(320px, .9fr);
                          gap: 24px;
                          align-items: stretch;
                        }
                        .glass {
                          position: relative;
                          border: 1px solid var(--line);
                          background: var(--panel);
                          box-shadow: 0 24px 80px rgba(0, 0, 0, .35);
                          backdrop-filter: blur(22px);
                          border-radius: 28px;
                          overflow: hidden;
                        }
                        .hero-main { padding: 34px; }
                        .badge {
                          display: inline-flex;
                          align-items: center;
                          gap: 10px;
                          padding: 9px 13px;
                          border: 1px solid rgba(139, 92, 246, .35);
                          border-radius: 999px;
                          color: #ddd6fe;
                          background: rgba(139, 92, 246, .14);
                          font-size: 13px;
                        }
                        .pulse {
                          width: 9px; height: 9px; border-radius: 99px; background: var(--ok);
                          box-shadow: 0 0 0 8px rgba(34, 197, 94, .12);
                        }
                        h1 { margin: 22px 0 14px; font-size: clamp(34px, 6vw, 68px); line-height: .96; letter-spacing: -0.06em; }
                        .lead { max-width: 860px; color: var(--muted); font-size: 17px; line-height: 1.8; }
                        .actions { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 28px; }
                        button {
                          border: 0;
                          color: white;
                          background: linear-gradient(135deg, var(--accent), var(--accent-2));
                          padding: 12px 16px;
                          border-radius: 14px;
                          font-weight: 800;
                          cursor: pointer;
                          transition: transform .18s ease, filter .18s ease, box-shadow .18s ease;
                          box-shadow: 0 14px 34px rgba(6, 182, 212, .18);
                        }
                        button:hover { transform: translateY(-2px); filter: brightness(1.08); }
                        button.secondary { background: rgba(148, 163, 184, .12); border: 1px solid var(--line); box-shadow: none; }
                        button.warn { background: linear-gradient(135deg, #f59e0b, #fb7185); }
                        .hero-card { padding: 26px; display: grid; gap: 16px; }
                        .metric-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
                        .metric { padding: 16px; border-radius: 20px; background: rgba(15, 23, 42, .74); border: 1px solid var(--line); }
                        .metric strong { display: block; font-size: 28px; }
                        .metric span { color: var(--muted); font-size: 12px; }
                        .workflow { display: grid; gap: 10px; }
                        .step {
                          display: flex; align-items: center; gap: 12px;
                          padding: 13px; border-radius: 16px; background: rgba(255,255,255,.045); border: 1px solid var(--line);
                        }
                        .step i {
                          display: grid; place-items: center; width: 30px; height: 30px; border-radius: 10px;
                          background: rgba(139, 92, 246, .18); color: #c4b5fd; font-style: normal; font-weight: 900;
                        }
                        .grid { display: grid; grid-template-columns: 390px 1fr; gap: 22px; margin-top: 22px; }
                        .panel { padding: 22px; }
                        .panel h2 { margin: 0 0 12px; letter-spacing: -0.03em; }
                        label { display: block; margin: 14px 0 7px; color: #cbd5e1; font-size: 13px; font-weight: 800; }
                        input, textarea, select {
                          width: 100%;
                          border: 1px solid var(--line);
                          border-radius: 14px;
                          color: var(--text);
                          background: rgba(2, 6, 23, .52);
                          padding: 12px 13px;
                          outline: none;
                          transition: border .18s ease, box-shadow .18s ease;
                        }
                        textarea { min-height: 150px; resize: vertical; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
                        input:focus, textarea:focus, select:focus { border-color: rgba(6, 182, 212, .65); box-shadow: 0 0 0 4px rgba(6, 182, 212, .12); }
                        .row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
                        .endpoint-list { display: grid; gap: 10px; max-height: 420px; overflow: auto; padding-right: 4px; }
                        .endpoint {
                          padding: 14px;
                          border-radius: 16px;
                          border: 1px solid var(--line);
                          background: rgba(15, 23, 42, .62);
                          cursor: pointer;
                          transition: transform .16s ease, border .16s ease, background .16s ease;
                        }
                        .endpoint:hover, .endpoint.active { transform: translateX(4px); border-color: rgba(139, 92, 246, .6); background: rgba(139, 92, 246, .12); }
                        .method { display: inline-flex; min-width: 58px; justify-content: center; margin-right: 8px; padding: 4px 8px; border-radius: 999px; background: rgba(6, 182, 212, .16); color: #67e8f9; font-size: 12px; font-weight: 900; }
                        .path { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
                        .muted { color: var(--muted); }
                        .output {
                          min-height: 360px;
                          max-height: 720px;
                          overflow: auto;
                          padding: 18px;
                          border-radius: 20px;
                          background: rgba(2, 6, 23, .68);
                          border: 1px solid var(--line);
                          font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
                          font-size: 13px;
                          line-height: 1.55;
                          white-space: pre-wrap;
                        }
                        .toast {
                          position: fixed; right: 24px; bottom: 24px; z-index: 10;
                          padding: 14px 16px; border-radius: 16px; background: var(--panel-strong);
                          border: 1px solid var(--line); box-shadow: 0 20px 60px rgba(0,0,0,.4);
                          transform: translateY(120px); opacity: 0; transition: .22s ease;
                        }
                        .toast.show { transform: translateY(0); opacity: 1; }
                        @media (max-width: 1040px) {
                          .hero, .grid { grid-template-columns: 1fr; }
                        }
                      </style>
                    </head>
                    <body>
                      <div class="shell">
                        <section class="hero">
                          <div class="glass hero-main">
                            <div class="badge"><span class="pulse"></span> AI Dev Assistant · YAPI + Code + Frontend Test</div>
                            <h1>接口写完后，让 AI 自动填参、调用和联测。</h1>
                            <p class="lead">读取 YAPI 参数和项目代码上下文，自动生成请求参数；可访问开发环境前端页面，完成页面探测与接口调用测试。支持 OpenAI 兼容模型，也内置本地启发式填参作为兜底。</p>
                            <div class="actions">
                              <button onclick="scanProject()">扫描项目代码</button>
                              <button onclick="importYapi()" class="secondary">导入 YAPI</button>
                              <button onclick="quickStart()" class="warn">AI 快捷启动</button>
                            </div>
                          </div>
                          <div class="glass hero-card">
                            <div class="metric-grid">
                              <div class="metric"><strong id="fileCount">0</strong><span>扫描文件</span></div>
                              <div class="metric"><strong id="endpointCount">0</strong><span>代码接口</span></div>
                              <div class="metric"><strong id="yapiCount">0</strong><span>YAPI 接口</span></div>
                            </div>
                            <div class="workflow">
                              <div class="step"><i>1</i><div><b>读取 YAPI</b><div class="muted">URL 或 JSON 文本均可</div></div></div>
                              <div class="step"><i>2</i><div><b>理解代码</b><div class="muted">提取接口、调用和上下文摘要</div></div></div>
                              <div class="step"><i>3</i><div><b>一键填参</b><div class="muted">AI 优先，本地规则兜底</div></div></div>
                              <div class="step"><i>4</i><div><b>联测回归</b><div class="muted">调用接口并探测前端页面</div></div></div>
                            </div>
                          </div>
                        </section>

                        <section class="grid">
                          <aside class="glass panel">
                            <h2>配置</h2>
                            <label>YAPI URL</label>
                            <input id="yapiUrl" placeholder="https://yapi.example.com/api/interface/get?id=1">
                            <label>YAPI Token（可选）</label>
                            <input id="yapiToken" placeholder="token / Authorization">
                            <label>或粘贴 YAPI JSON</label>
                            <textarea id="yapiText" placeholder='{"data":{"path":"/api/user","method":"POST","req_query":[]}}'></textarea>
                            <div class="row">
                              <div>
                                <label>接口 Base URL</label>
                                <input id="baseUrl" placeholder="http://localhost:8080">
                              </div>
                              <div>
                                <label>前端页面 URL</label>
                                <input id="frontendUrl" placeholder="http://localhost:5173">
                              </div>
                            </div>
                            <div class="actions">
                              <button onclick="aiFill()">AI 一键填参</button>
                              <button onclick="runTest()" class="secondary">运行测试</button>
                            </div>
                            <h2 style="margin-top:22px;">接口列表</h2>
                            <div id="endpoints" class="endpoint-list"><div class="muted">先导入 YAPI 或扫描项目。</div></div>
                          </aside>

                          <main class="glass panel">
                            <h2>运行结果</h2>
                            <div id="output" class="output">等待操作...</div>
                          </main>
                        </section>
                      </div>
                      <div id="toast" class="toast"></div>
                      <script>
                        const state = { project: null, yapiEndpoints: [], selected: null, params: {} };
                        const $ = id => document.getElementById(id);

                        function toast(message) {
                          const el = $('toast');
                          el.textContent = message;
                          el.classList.add('show');
                          setTimeout(() => el.classList.remove('show'), 2400);
                        }

                        function print(value) {
                          $('output').textContent = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
                        }

                        async function api(path, options = {}) {
                          const res = await fetch(path, {
                            headers: { 'content-type': 'application/json' },
                            ...options
                          });
                          const data = await res.json();
                          if (!res.ok) throw new Error(data.error || '请求失败');
                          return data;
                        }

                        function renderEndpoints() {
                          const list = [...state.yapiEndpoints];
                          if (state.project?.endpoints?.length) {
                            state.project.endpoints.slice(0, 30).forEach(item => list.push({ ...item, title: item.source, params: [] }));
                          }
                          $('yapiCount').textContent = state.yapiEndpoints.length;
                          const el = $('endpoints');
                          if (!list.length) {
                            el.innerHTML = '<div class="muted">先导入 YAPI 或扫描项目。</div>';
                            return;
                          }
                          el.innerHTML = list.map((endpoint, index) => `
                            <div class="endpoint ${state.selected === endpoint ? 'active' : ''}" onclick="selectEndpoint(${index})">
                              <span class="method">${endpoint.method || 'GET'}</span>
                              <span class="path">${endpoint.path || '-'}</span>
                              <div class="muted">${endpoint.title || ''} · ${(endpoint.params || []).length} params</div>
                            </div>
                          `).join('');
                          state.rendered = list;
                        }

                        function selectEndpoint(index) {
                          state.selected = state.rendered[index];
                          renderEndpoints();
                          print(state.selected);
                        }

                        async function scanProject() {
                          toast('正在扫描项目代码...');
                          const data = await api('/api/project/scan');
                          state.project = data;
                          $('fileCount').textContent = data.fileCount;
                          $('endpointCount').textContent = data.endpointCount;
                          renderEndpoints();
                          print(data);
                        }

                        async function importYapi() {
                          toast('正在导入 YAPI...');
                          const data = await api('/api/yapi/import', {
                            method: 'POST',
                            body: JSON.stringify({
                              yapiUrl: $('yapiUrl').value,
                              yapiToken: $('yapiToken').value,
                              yapiText: $('yapiText').value
                            })
                          });
                          state.yapiEndpoints = data.endpoints;
                          state.selected = data.endpoints[0] || state.selected;
                          renderEndpoints();
                          print(data);
                        }

                        async function aiFill() {
                          if (!state.selected) await importYapi();
                          if (!state.selected) throw new Error('没有可填参的接口');
                          toast('正在生成参数...');
                          const data = await api('/api/ai/fill', {
                            method: 'POST',
                            body: JSON.stringify({ endpoint: state.selected })
                          });
                          state.params = data.params;
                          print(data);
                        }

                        async function runTest() {
                          if (!state.selected) throw new Error('请先选择接口');
                          if (!Object.keys(state.params).length) await aiFill();
                          const baseUrl = $('baseUrl').value.trim();
                          if (!baseUrl && !(state.selected.path || '').startsWith('http')) throw new Error('请填写接口 Base URL');
                          toast('正在调用接口并探测页面...');
                          const data = await api('/api/test/run', {
                            method: 'POST',
                            body: JSON.stringify({
                              method: state.selected.method,
                              url: (state.selected.path || '').startsWith('http') ? state.selected.path : baseUrl.replace(/\\/$/, '') + '/' + (state.selected.path || '').replace(/^\\//, ''),
                              params: state.params,
                              frontendUrl: $('frontendUrl').value
                            })
                          });
                          print(data);
                        }

                        async function quickStart() {
                          toast('AI 快捷启动中...');
                          const data = await api('/api/ai/quick-start', {
                            method: 'POST',
                            body: JSON.stringify({
                              yapiText: $('yapiText').value,
                              baseUrl: $('baseUrl').value,
                              frontendUrl: $('frontendUrl').value,
                              endpoint: state.selected
                            })
                          });
                          state.project = data.project;
                          $('fileCount').textContent = data.project.fileCount;
                          $('endpointCount').textContent = data.project.endpointCount;
                          renderEndpoints();
                          print(data);
                        }

                        window.addEventListener('error', event => {
                          toast(event.message);
                          print({ error: event.message });
                        });
                        scanProject().catch(error => print({ error: error.message }));
                      </script>
                    </body>
                    </html>
                    """;
        }
    }
}
