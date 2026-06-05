import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Main {
    private static final Path PROJECT_ROOT = Paths.get("").toAbsolutePath().normalize();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ProcessRegistry PROCESS_REGISTRY = new ProcessRegistry();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", new Router());
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        System.out.println("AI YAPI Dev Assistant started");
        System.out.println("Open http://localhost:" + port + " in your browser");
        System.out.println("Project root: " + PROJECT_ROOT);
    }

    private static final class Router implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 204, "", "text/plain");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            try {
                if ("GET".equals(exchange.getRequestMethod()) && "/".equals(path)) {
                    sendStatic(exchange, "static/index.html", "text/html; charset=utf-8");
                    return;
                }
                if ("GET".equals(exchange.getRequestMethod()) && path.startsWith("/static/")) {
                    serveStaticAsset(exchange, path);
                    return;
                }
                if ("GET".equals(exchange.getRequestMethod()) && "/api/health".equals(path)) {
                    json(exchange, 200, mapOf(
                            "ok", true,
                            "service", "ai-yapi-dev-assistant",
                            "projectRoot", PROJECT_ROOT.toString(),
                            "time", Instant.now().toString()
                    ));
                    return;
                }
                if ("POST".equals(exchange.getRequestMethod()) && "/api/yapi/import".equals(path)) {
                    json(exchange, 200, importFromYapi(readJsonObject(exchange)));
                    return;
                }
                if ("POST".equals(exchange.getRequestMethod()) && "/api/project/scan".equals(path)) {
                    json(exchange, 200, scanProject(readJsonObject(exchange)));
                    return;
                }
                if ("POST".equals(exchange.getRequestMethod()) && "/api/ai/fill".equals(path)) {
                    json(exchange, 200, fillParameters(readJsonObject(exchange)));
                    return;
                }
                if ("POST".equals(exchange.getRequestMethod()) && "/api/test/request".equals(path)) {
                    json(exchange, 200, testRequest(readJsonObject(exchange)));
                    return;
                }
                if ("POST".equals(exchange.getRequestMethod()) && "/api/test/page".equals(path)) {
                    json(exchange, 200, inspectPage(readJsonObject(exchange)));
                    return;
                }
                if ("POST".equals(exchange.getRequestMethod()) && "/api/project/start".equals(path)) {
                    json(exchange, 200, PROCESS_REGISTRY.start(readJsonObject(exchange)));
                    return;
                }
                if ("POST".equals(exchange.getRequestMethod()) && "/api/project/stop".equals(path)) {
                    json(exchange, 200, PROCESS_REGISTRY.stop(readJsonObject(exchange)));
                    return;
                }
                if ("GET".equals(exchange.getRequestMethod()) && "/api/project/processes".equals(path)) {
                    json(exchange, 200, mapOf("processes", PROCESS_REGISTRY.list()));
                    return;
                }
                if ("GET".equals(exchange.getRequestMethod()) && "/api/project/logs".equals(path)) {
                    Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                    json(exchange, 200, PROCESS_REGISTRY.logs(query.get("id")));
                    return;
                }

                json(exchange, 404, mapOf("error", "Not found", "path", path));
            } catch (BadRequestException e) {
                json(exchange, 400, mapOf("error", e.getMessage()));
            } catch (Exception e) {
                e.printStackTrace();
                json(exchange, 500, mapOf("error", e.getMessage(), "type", e.getClass().getSimpleName()));
            }
        }
    }

    private static Map<String, Object> importFromYapi(Map<String, Object> input) throws Exception {
        String baseUrl = requiredString(input, "baseUrl").replaceAll("/+$", "");
        String token = stringValue(input.get("token"));
        String interfaceId = stringValue(input.get("interfaceId"));
        String projectId = stringValue(input.get("projectId"));

        if (!interfaceId.isBlank()) {
            String url = baseUrl + "/api/interface/get?id=" + encode(interfaceId);
            if (!token.isBlank()) {
                url += "&token=" + encode(token);
            }
            Map<String, Object> response = getJson(url);
            Object data = unwrapYapiData(response);
            return mapOf(
                    "source", "interface",
                    "requestUrl", redactToken(url),
                    "interface", normalizeYapiInterface(asMap(data))
            );
        }

        if (!projectId.isBlank()) {
            String url = baseUrl + "/api/interface/list?project_id=" + encode(projectId) + "&limit=100&page=1";
            if (!token.isBlank()) {
                url += "&token=" + encode(token);
            }
            Map<String, Object> response = getJson(url);
            Object data = unwrapYapiData(response);
            return mapOf(
                    "source", "project",
                    "requestUrl", redactToken(url),
                    "interfaces", normalizeYapiList(data)
            );
        }

        throw new BadRequestException("请提供 interfaceId 或 projectId");
    }

    private static Object unwrapYapiData(Map<String, Object> response) {
        if (response.containsKey("errcode")) {
            int errcode = toInt(response.get("errcode"), 0);
            if (errcode != 0) {
                throw new BadRequestException("YAPI 返回错误: " + stringValue(response.getOrDefault("errmsg", response)));
            }
        }
        return response.getOrDefault("data", response);
    }

    private static Map<String, Object> normalizeYapiInterface(Map<String, Object> raw) {
        return mapOf(
                "id", raw.getOrDefault("_id", raw.get("id")),
                "title", raw.getOrDefault("title", "未命名接口"),
                "method", upper(raw.getOrDefault("method", "GET")),
                "path", raw.getOrDefault("path", ""),
                "status", raw.getOrDefault("status", ""),
                "description", raw.getOrDefault("desc", ""),
                "headers", normalizeParamList(raw.get("req_headers")),
                "pathParams", normalizeParamList(raw.get("req_params")),
                "query", normalizeParamList(raw.get("req_query")),
                "bodyForm", normalizeParamList(raw.get("req_body_form")),
                "bodyType", raw.getOrDefault("req_body_type", ""),
                "bodySchema", raw.getOrDefault("req_body_other", ""),
                "responseSchema", raw.getOrDefault("res_body", ""),
                "raw", raw
        );
    }

    private static List<Object> normalizeYapiList(Object data) {
        Object list = data;
        if (data instanceof Map<?, ?> map) {
            list = map.getOrDefault("list", Collections.emptyList());
        }
        List<Object> result = new ArrayList<>();
        for (Object item : asList(list)) {
            Map<String, Object> raw = asMap(item);
            result.add(mapOf(
                    "id", raw.getOrDefault("_id", raw.get("id")),
                    "title", raw.getOrDefault("title", "未命名接口"),
                    "method", upper(raw.getOrDefault("method", "GET")),
                    "path", raw.getOrDefault("path", "")
            ));
        }
        return result;
    }

    private static List<Object> normalizeParamList(Object value) {
        List<Object> result = new ArrayList<>();
        for (Object item : asList(value)) {
            Map<String, Object> raw = asMap(item);
            result.add(mapOf(
                    "name", raw.getOrDefault("name", raw.getOrDefault("key", "")),
                    "required", truthy(raw.get("required")),
                    "example", raw.getOrDefault("example", raw.getOrDefault("value", "")),
                    "description", raw.getOrDefault("desc", raw.getOrDefault("description", "")),
                    "type", raw.getOrDefault("type", raw.getOrDefault("schema", "string"))
            ));
        }
        return result;
    }

    private static Map<String, Object> scanProject(Map<String, Object> input) throws IOException {
        int maxFiles = Math.max(10, Math.min(toInt(input.get("maxFiles"), 350), 2000));
        Path root = resolveProjectPath(stringValue(input.getOrDefault("root", ".")));
        Set<String> extensions = new LinkedHashSet<>(List.of(
                ".java", ".kt", ".go", ".py", ".js", ".jsx", ".ts", ".tsx",
                ".vue", ".svelte", ".json", ".yaml", ".yml", ".md"
        ));
        List<Object> files = new ArrayList<>();
        List<Object> endpoints = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(root, 12)) {
            List<Path> selected = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !isIgnored(path))
                    .filter(path -> extensions.stream().anyMatch(ext -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ext)))
                    .sorted(Comparator.comparing(Path::toString))
                    .limit(maxFiles)
                    .toList();

            for (Path file : selected) {
                String content = safeRead(file, 180_000);
                List<Object> fileEndpoints = detectEndpoints(file, content);
                endpoints.addAll(fileEndpoints);
                files.add(mapOf(
                        "path", PROJECT_ROOT.relativize(file.toAbsolutePath().normalize()).toString(),
                        "bytes", Files.size(file),
                        "signals", detectSignals(content),
                        "endpoints", fileEndpoints
                ));
            }
        }

        return mapOf(
                "root", PROJECT_ROOT.relativize(root).toString().isBlank() ? "." : PROJECT_ROOT.relativize(root).toString(),
                "fileCount", files.size(),
                "endpointCount", endpoints.size(),
                "files", files,
                "endpoints", endpoints
        );
    }

    private static Map<String, Object> fillParameters(Map<String, Object> input) throws Exception {
        Map<String, Object> yapi = asMap(input.get("yapi"));
        Map<String, Object> project = asMap(input.get("project"));
        Map<String, Object> fallback = heuristicFill(yapi, project);

        String apiKey = firstEnv("AI_API_KEY", "OPENAI_API_KEY");
        if (apiKey.isBlank()) {
            return mapOf(
                    "mode", "heuristic",
                    "message", "未配置 AI_API_KEY/OPENAI_API_KEY，已使用本地规则生成可测试参数。",
                    "result", fallback
            );
        }

        try {
            Map<String, Object> ai = callAiForFill(input, fallback, apiKey);
            return mapOf(
                    "mode", "ai",
                    "message", "AI 已基于 YAPI 和项目扫描结果生成参数。",
                    "result", ai,
                    "fallback", fallback
            );
        } catch (Exception e) {
            return mapOf(
                    "mode", "heuristic",
                    "message", "AI 调用失败，已回退本地规则: " + e.getMessage(),
                    "result", fallback
            );
        }
    }

    private static Map<String, Object> heuristicFill(Map<String, Object> yapi, Map<String, Object> project) {
        Map<String, Object> headers = new LinkedHashMap<>();
        Map<String, Object> query = new LinkedHashMap<>();
        Map<String, Object> pathParams = new LinkedHashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();

        for (Object item : asList(yapi.get("headers"))) {
            Map<String, Object> param = asMap(item);
            String name = stringValue(param.get("name"));
            if (!name.isBlank()) {
                headers.put(name, sampleValue(name, param, project));
            }
        }
        headers.putIfAbsent("Content-Type", "application/json");

        for (Object item : asList(yapi.get("query"))) {
            Map<String, Object> param = asMap(item);
            String name = stringValue(param.get("name"));
            if (!name.isBlank()) {
                query.put(name, sampleValue(name, param, project));
            }
        }

        for (Object item : asList(yapi.get("pathParams"))) {
            Map<String, Object> param = asMap(item);
            String name = stringValue(param.get("name"));
            if (!name.isBlank()) {
                pathParams.put(name, sampleValue(name, param, project));
            }
        }

        for (Object item : asList(yapi.get("bodyForm"))) {
            Map<String, Object> param = asMap(item);
            String name = stringValue(param.get("name"));
            if (!name.isBlank()) {
                body.put(name, sampleValue(name, param, project));
            }
        }

        String bodySchema = stringValue(yapi.get("bodySchema"));
        if (!bodySchema.isBlank() && body.isEmpty()) {
            Object parsed = tryParseJson(bodySchema).orElse(null);
            if (parsed instanceof Map<?, ?> map) {
                body.putAll(fillSchemaObject(asMap(map), project));
            } else if (parsed != null) {
                body.put("payload", parsed);
            }
        }

        String path = stringValue(yapi.get("path"));
        for (Map.Entry<String, Object> entry : pathParams.entrySet()) {
            path = path.replace(":" + entry.getKey(), encodePath(entry.getValue()));
            path = path.replace("{" + entry.getKey() + "}", encodePath(entry.getValue()));
        }

        return mapOf(
                "title", yapi.getOrDefault("title", "接口测试"),
                "method", upper(yapi.getOrDefault("method", "GET")),
                "path", path,
                "headers", headers,
                "query", query,
                "pathParams", pathParams,
                "body", body,
                "assertions", List.of(
                        "HTTP 状态码应为 2xx",
                        "响应耗时应符合项目预期",
                        "响应体应包含 YAPI 定义的核心字段"
                ),
                "reasoning", List.of(
                        "优先使用 YAPI example/value 字段",
                        "缺失示例时按参数名和类型生成测试值",
                        "结合项目扫描结果保留接口路径与方法"
                )
        );
    }

    private static Map<String, Object> fillSchemaObject(Map<String, Object> schema, Map<String, Object> project) {
        if (schema.containsKey("properties")) {
            Map<String, Object> result = new LinkedHashMap<>();
            Map<String, Object> properties = asMap(schema.get("properties"));
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                Map<String, Object> property = asMap(entry.getValue());
                result.put(entry.getKey(), sampleValue(entry.getKey(), property, project));
            }
            return result;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> nested) {
                result.put(entry.getKey(), sampleValue(entry.getKey(), asMap(nested), project));
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static Map<String, Object> callAiForFill(Map<String, Object> input, Map<String, Object> fallback, String apiKey) throws Exception {
        String endpoint = firstEnv("AI_API_BASE_URL", "OPENAI_API_BASE_URL");
        if (endpoint.isBlank()) {
            endpoint = "https://api.openai.com/v1/chat/completions";
        }
        String model = firstEnv("AI_MODEL", "OPENAI_MODEL");
        if (model.isBlank()) {
            model = "gpt-4o-mini";
        }

        String prompt = """
                你是资深前端/后端测试工程师。请读取 YAPI 接口定义和项目扫描结果，生成一个可直接用于接口测试的 JSON。
                输出必须是严格 JSON，不要 Markdown。字段包含 method、path、headers、query、pathParams、body、assertions、reasoning。
                优先使用 YAPI 示例值；缺失时根据字段名、类型、项目代码语义生成合理测试值。
                """;
        Map<String, Object> requestBody = mapOf(
                "model", model,
                "temperature", 0.2,
                "messages", List.of(
                        mapOf("role", "system", "content", prompt),
                        mapOf("role", "user", "content", Json.stringify(mapOf(
                                "input", input,
                                "localFallback", fallback
                        )))
                )
        );

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(requestBody)))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("AI HTTP " + response.statusCode() + ": " + truncate(response.body(), 500));
        }

        Map<String, Object> parsed = asMap(Json.parse(response.body()));
        List<Object> choices = asList(parsed.get("choices"));
        if (choices.isEmpty()) {
            throw new IOException("AI 响应缺少 choices");
        }
        Map<String, Object> first = asMap(choices.getFirst());
        String content = stringValue(asMap(first.get("message")).get("content")).trim();
        content = stripJsonFence(content);
        return asMap(Json.parse(content));
    }

    private static Map<String, Object> testRequest(Map<String, Object> input) throws Exception {
        String method = upper(input.getOrDefault("method", "GET"));
        String url = requiredString(input, "url");
        Map<String, Object> headers = asMap(input.get("headers"));
        Map<String, Object> query = asMap(input.get("query"));
        Object body = input.get("body");
        String finalUrl = appendQuery(url, query);

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(finalUrl))
                .timeout(Duration.ofSeconds(Math.max(3, Math.min(toInt(input.get("timeoutSeconds"), 20), 120))));
        headers.forEach((key, value) -> {
            if (!key.isBlank() && value != null) {
                builder.header(key, stringValue(value));
            }
        });

        if (List.of("POST", "PUT", "PATCH", "DELETE").contains(method)) {
            String payload = body instanceof String text ? text : Json.stringify(body == null ? Map.of() : body);
            builder.method(method, HttpRequest.BodyPublishers.ofString(payload));
            if (!headers.keySet().stream().map(k -> k.toLowerCase(Locale.ROOT)).toList().contains("content-type")) {
                builder.header("Content-Type", "application/json");
            }
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        Instant start = Instant.now();
        HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        long durationMs = Duration.between(start, Instant.now()).toMillis();
        return mapOf(
                "url", finalUrl,
                "method", method,
                "status", response.statusCode(),
                "durationMs", durationMs,
                "headers", response.headers().map(),
                "body", truncate(response.body(), 60_000),
                "ok", response.statusCode() >= 200 && response.statusCode() < 300
        );
    }

    private static Map<String, Object> inspectPage(Map<String, Object> input) throws Exception {
        String url = requiredString(input, "url");
        Instant start = Instant.now();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(3, Math.min(toInt(input.get("timeoutSeconds"), 20), 120))))
                .header("User-Agent", "AI-YAPI-Dev-Assistant/1.0")
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String html = response.body();
        return mapOf(
                "url", url,
                "status", response.statusCode(),
                "durationMs", Duration.between(start, Instant.now()).toMillis(),
                "title", firstMatch(html, Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)).orElse(""),
                "scriptCount", countMatches(html, Pattern.compile("<script\\b", Pattern.CASE_INSENSITIVE)),
                "styleCount", countMatches(html, Pattern.compile("<style\\b|<link[^>]+stylesheet", Pattern.CASE_INSENSITIVE)),
                "formCount", countMatches(html, Pattern.compile("<form\\b", Pattern.CASE_INSENSITIVE)),
                "links", extractAttributeSamples(html, "a", "href"),
                "assets", extractAttributeSamples(html, "script", "src"),
                "preview", truncate(html.replaceAll("\\s+", " ").trim(), 4000),
                "recommendations", List.of(
                        "如需真实浏览器交互，可将此接口返回的页面信息交给 AI 生成 Playwright/Cypress 脚本。",
                        "先用项目启动命令启动前端，再填入本地页面地址进行连通性检查。"
                )
        );
    }

    private static List<Object> detectEndpoints(Path file, String content) {
        List<Object> endpoints = new ArrayList<>();
        String relative = PROJECT_ROOT.relativize(file.toAbsolutePath().normalize()).toString();
        List<Pattern> patterns = List.of(
                Pattern.compile("@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(?:app|router|server)\\s*\\.\\s*(get|post|put|delete|patch)\\s*\\(\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(?:fetch|axios(?:\\.request|\\.get|\\.post|\\.put|\\.delete|\\.patch)?)\\s*\\(\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE)
        );

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(content);
            while (matcher.find() && endpoints.size() < 80) {
                String method;
                String path;
                if (matcher.groupCount() >= 2 && matcher.group(1).toLowerCase(Locale.ROOT).endsWith("mapping")) {
                    method = matcher.group(1).replace("Mapping", "").toUpperCase(Locale.ROOT);
                    if (method.isBlank()) {
                        method = "REQUEST";
                    }
                    path = extractMappingPath(matcher.group(2));
                } else if (matcher.groupCount() >= 2 && List.of("get", "post", "put", "delete", "patch").contains(matcher.group(1).toLowerCase(Locale.ROOT))) {
                    method = matcher.group(1).toUpperCase(Locale.ROOT);
                    path = matcher.group(2);
                } else {
                    method = "CLIENT";
                    path = matcher.group(1);
                }
                endpoints.add(mapOf(
                        "file", relative,
                        "method", method,
                        "path", path,
                        "line", lineNumber(content, matcher.start())
                ));
            }
        }
        return endpoints;
    }

    private static List<Object> detectSignals(String content) {
        List<Object> signals = new ArrayList<>();
        addSignal(signals, content, "YAPI", "YAPI");
        addSignal(signals, content, "OpenAI", "openai|chat/completions|AI_API_KEY");
        addSignal(signals, content, "HTTP Client", "axios|fetch\\(|HttpClient|RestTemplate|Feign");
        addSignal(signals, content, "Router", "router\\.|@.*Mapping|app\\.(get|post|put|delete|patch)");
        addSignal(signals, content, "Test", "playwright|cypress|jest|junit|pytest|vitest");
        return signals;
    }

    private static void addSignal(List<Object> signals, String content, String label, String regex) {
        if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(content).find()) {
            signals.add(label);
        }
    }

    private static final class ProcessRegistry {
        private final Map<String, ManagedProcess> processes = new ConcurrentHashMap<>();

        Map<String, Object> start(Map<String, Object> input) throws IOException {
            String command = requiredString(input, "command");
            Path cwd = resolveProjectPath(stringValue(input.getOrDefault("cwd", ".")));
            String id = UUID.randomUUID().toString().substring(0, 8);
            ProcessBuilder builder = new ProcessBuilder("bash", "-lc", command);
            builder.directory(cwd.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            ManagedProcess managed = new ManagedProcess(id, command, cwd, process);
            processes.put(id, managed);
            Thread.ofVirtual().name("process-log-" + id).start(managed::pumpLogs);
            return mapOf("id", id, "command", command, "cwd", cwd.toString(), "running", process.isAlive());
        }

        Map<String, Object> stop(Map<String, Object> input) {
            String id = requiredString(input, "id");
            ManagedProcess managed = processes.get(id);
            if (managed == null) {
                throw new BadRequestException("未知进程 id: " + id);
            }
            managed.process.destroy();
            return mapOf("id", id, "stopped", true);
        }

        List<Object> list() {
            return processes.values().stream()
                    .sorted(Comparator.comparing(item -> item.startedAt))
                    .map(ManagedProcess::summary)
                    .map(item -> (Object) item)
                    .toList();
        }

        Map<String, Object> logs(String id) {
            if (id == null || id.isBlank()) {
                return mapOf("processes", list());
            }
            ManagedProcess managed = processes.get(id);
            if (managed == null) {
                throw new BadRequestException("未知进程 id: " + id);
            }
            return managed.summaryWithLogs();
        }
    }

    private static final class ManagedProcess {
        private final String id;
        private final String command;
        private final Path cwd;
        private final Process process;
        private final Instant startedAt = Instant.now();
        private final StringBuilder logs = new StringBuilder();

        private ManagedProcess(String id, String command, Path cwd, Process process) {
            this.id = id;
            this.command = command;
            this.cwd = cwd;
            this.process = process;
        }

        void pumpLogs() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (logs) {
                        logs.append(line).append('\n');
                        if (logs.length() > 80_000) {
                            logs.delete(0, logs.length() - 80_000);
                        }
                    }
                }
            } catch (IOException e) {
                synchronized (logs) {
                    logs.append("[log error] ").append(e.getMessage()).append('\n');
                }
            }
        }

        Map<String, Object> summary() {
            return mapOf(
                    "id", id,
                    "command", command,
                    "cwd", cwd.toString(),
                    "running", process.isAlive(),
                    "exitCode", process.isAlive() ? null : process.exitValue(),
                    "startedAt", startedAt.toString()
            );
        }

        Map<String, Object> summaryWithLogs() {
            Map<String, Object> result = new LinkedHashMap<>(summary());
            synchronized (logs) {
                result.put("logs", logs.toString());
            }
            return result;
        }
    }

    private static void serveStaticAsset(HttpExchange exchange, String path) throws IOException {
        String safe = path.replaceFirst("^/+", "");
        if (safe.contains("..")) {
            json(exchange, 400, mapOf("error", "Invalid static path"));
            return;
        }
        String contentType = switch (extensionOf(safe)) {
            case ".css" -> "text/css; charset=utf-8";
            case ".js" -> "application/javascript; charset=utf-8";
            case ".svg" -> "image/svg+xml";
            default -> "text/plain; charset=utf-8";
        };
        sendStatic(exchange, safe, contentType);
    }

    private static void sendStatic(HttpExchange exchange, String relativePath, String contentType) throws IOException {
        Path file = PROJECT_ROOT.resolve(relativePath).normalize();
        if (!file.startsWith(PROJECT_ROOT) || !Files.exists(file)) {
            json(exchange, 404, mapOf("error", "Static file not found"));
            return;
        }
        byte[] bytes = Files.readAllBytes(file);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Cache-Control", "no-store");
        headers.set("Content-Type", contentType);
        headers.set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, Object> readJsonObject(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.isBlank()) {
            return new LinkedHashMap<>();
        }
        return asMap(Json.parse(body));
    }

    private static void json(HttpExchange exchange, int status, Object payload) throws IOException {
        send(exchange, status, Json.stringify(payload), "application/json; charset=utf-8");
    }

    private static void send(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type,Authorization");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, Object> getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(25))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + ": " + truncate(response.body(), 500));
        }
        return asMap(Json.parse(response.body()));
    }

    private static Object sampleValue(String name, Map<String, Object> param, Map<String, Object> project) {
        Object example = firstNonBlank(param.get("example"), param.get("value"), param.get("default"));
        if (example != null) {
            return example;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        String type = stringValue(param.getOrDefault("type", param.get("schema"))).toLowerCase(Locale.ROOT);
        if (lower.contains("token") || lower.contains("authorization")) {
            return "Bearer <your-token>";
        }
        if (lower.endsWith("id") || lower.equals("id") || lower.contains("_id")) {
            return 10001;
        }
        if (lower.contains("phone") || lower.contains("mobile")) {
            return "13800138000";
        }
        if (lower.contains("email")) {
            return "tester@example.com";
        }
        if (lower.contains("name") || lower.contains("title")) {
            return "AI 自动化测试";
        }
        if (lower.contains("time") || lower.contains("date")) {
            return Instant.now().toString();
        }
        if (lower.contains("page")) {
            return 1;
        }
        if (lower.contains("size") || lower.contains("limit")) {
            return 10;
        }
        if (type.contains("integer") || type.contains("number") || type.equals("int")) {
            return 1;
        }
        if (type.contains("boolean") || type.equals("bool")) {
            return true;
        }
        if (type.contains("array")) {
            return List.of("sample");
        }
        if (type.contains("object")) {
            return Map.of("sample", true);
        }
        return "sample";
    }

    private static Object firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value != null && !stringValue(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Path resolveProjectPath(String rawPath) {
        Path path = PROJECT_ROOT.resolve(rawPath == null || rawPath.isBlank() ? "." : rawPath).normalize();
        if (!path.startsWith(PROJECT_ROOT)) {
            throw new BadRequestException("路径必须位于项目目录内");
        }
        return path;
    }

    private static boolean isIgnored(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.contains("/.git/")
                || normalized.contains("/node_modules/")
                || normalized.contains("/target/")
                || normalized.contains("/build/")
                || normalized.contains("/dist/")
                || normalized.contains("/.idea/")
                || normalized.contains("/.gradle/");
    }

    private static String safeRead(Path file, int maxChars) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return truncate(content, maxChars);
        } catch (Exception e) {
            return "";
        }
    }

    private static String extractMappingPath(String args) {
        Matcher matcher = Pattern.compile("(?:value\\s*=\\s*)?[\"']([^\"']+)[\"']").matcher(args);
        return matcher.find() ? matcher.group(1) : "/";
    }

    private static int lineNumber(String content, int offset) {
        int line = 1;
        for (int i = 0; i < Math.min(offset, content.length()); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static Optional<String> firstMatch(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return Optional.of(matcher.group(1).replaceAll("\\s+", " ").trim());
        }
        return Optional.empty();
    }

    private static int countMatches(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static List<Object> extractAttributeSamples(String html, String tag, String attribute) {
        Pattern pattern = Pattern.compile("<" + tag + "\\b[^>]*\\s" + attribute + "\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        List<Object> result = new ArrayList<>();
        while (matcher.find() && result.size() < 12) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private static String appendQuery(String url, Map<String, Object> query) {
        if (query.isEmpty()) {
            return url;
        }
        StringBuilder builder = new StringBuilder(url);
        builder.append(url.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, Object> entry : query.entrySet()) {
            if (entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            if (!first) {
                builder.append('&');
            }
            first = false;
            builder.append(encode(entry.getKey())).append('=').append(encode(stringValue(entry.getValue())));
        }
        return builder.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encodePath(Object value) {
        return encode(stringValue(value)).replace("+", "%20");
    }

    private static String redactToken(String url) {
        return url.replaceAll("([?&]token=)[^&]+", "$1[REDACTED]");
    }

    private static String stripJsonFence(String content) {
        if (content.startsWith("```")) {
            content = content.replaceFirst("^```(?:json)?\\s*", "");
            content = content.replaceFirst("\\s*```$", "");
        }
        return content.trim();
    }

    private static Optional<Object> tryParseJson(String text) {
        try {
            return Optional.of(Json.parse(text));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static String firstEnv(String... names) {
        for (String name : names) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String requiredString(Map<String, Object> input, String key) {
        String value = stringValue(input.get(key));
        if (value.isBlank()) {
            throw new BadRequestException("缺少必填字段: " + key);
        }
        return value;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String upper(Object value) {
        return stringValue(value).toUpperCase(Locale.ROOT);
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(stringValue(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = stringValue(value).toLowerCase(Locale.ROOT);
        return text.equals("true") || text.equals("1") || text.equals("yes");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    private static Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private static String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "...[truncated]";
    }

    private static String extensionOf(String path) {
        int index = path.lastIndexOf('.');
        return index >= 0 ? path.substring(index).toLowerCase(Locale.ROOT) : "";
    }

    private static final class BadRequestException extends RuntimeException {
        private BadRequestException(String message) {
            super(message);
        }
    }

    private static final class Json {
        static Object parse(String input) {
            return new Parser(input).parse();
        }

        static String stringify(Object value) {
            StringBuilder builder = new StringBuilder();
            writeJson(builder, value);
            return builder.toString();
        }

        private static void writeJson(StringBuilder builder, Object value) {
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
                    builder.append('"').append(escape(String.valueOf(entry.getKey()))).append('"').append(':');
                    writeJson(builder, entry.getValue());
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
                    writeJson(builder, item);
                }
                builder.append(']');
            } else if (value.getClass().isArray()) {
                builder.append('[');
                int length = java.lang.reflect.Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    if (i > 0) {
                        builder.append(',');
                    }
                    writeJson(builder, java.lang.reflect.Array.get(value, i));
                }
                builder.append(']');
            } else {
                builder.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }

        private static String escape(String text) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                switch (c) {
                    case '"' -> builder.append("\\\"");
                    case '\\' -> builder.append("\\\\");
                    case '\b' -> builder.append("\\b");
                    case '\f' -> builder.append("\\f");
                    case '\n' -> builder.append("\\n");
                    case '\r' -> builder.append("\\r");
                    case '\t' -> builder.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            builder.append(String.format("\\u%04x", (int) c));
                        } else {
                            builder.append(c);
                        }
                    }
                }
            }
            return builder.toString();
        }

        private static final class Parser {
            private final String input;
            private int index;

            private Parser(String input) {
                this.input = Objects.requireNonNull(input);
            }

            private Object parse() {
                skipWhitespace();
                Object value = readValue();
                skipWhitespace();
                if (index != input.length()) {
                    throw new BadRequestException("JSON 解析失败: 多余内容位于 " + index);
                }
                return value;
            }

            private Object readValue() {
                skipWhitespace();
                if (index >= input.length()) {
                    throw new BadRequestException("JSON 解析失败: 输入结束");
                }
                char c = input.charAt(index);
                return switch (c) {
                    case '{' -> readObject();
                    case '[' -> readArray();
                    case '"' -> readString();
                    case 't' -> readLiteral("true", true);
                    case 'f' -> readLiteral("false", false);
                    case 'n' -> readLiteral("null", null);
                    default -> {
                        if (c == '-' || Character.isDigit(c)) {
                            yield readNumber();
                        }
                        throw new BadRequestException("JSON 解析失败: 非法字符 " + c + " 位于 " + index);
                    }
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
                    skipWhitespace();
                    String key = readString();
                    skipWhitespace();
                    expect(':');
                    Object value = readValue();
                    map.put(key, value);
                    skipWhitespace();
                    if (peek('}')) {
                        index++;
                        return map;
                    }
                    expect(',');
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
                while (index < input.length()) {
                    char c = input.charAt(index++);
                    if (c == '"') {
                        return builder.toString();
                    }
                    if (c == '\\') {
                        if (index >= input.length()) {
                            throw new BadRequestException("JSON 解析失败: 非法转义");
                        }
                        char escaped = input.charAt(index++);
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
                                if (index + 4 > input.length()) {
                                    throw new BadRequestException("JSON 解析失败: 非法 unicode 转义");
                                }
                                String hex = input.substring(index, index + 4);
                                builder.append((char) Integer.parseInt(hex, 16));
                                index += 4;
                            }
                            default -> throw new BadRequestException("JSON 解析失败: 未知转义 " + escaped);
                        }
                    } else {
                        builder.append(c);
                    }
                }
                throw new BadRequestException("JSON 解析失败: 字符串未闭合");
            }

            private Object readNumber() {
                int start = index;
                if (peek('-')) {
                    index++;
                }
                while (index < input.length() && Character.isDigit(input.charAt(index))) {
                    index++;
                }
                if (peek('.')) {
                    index++;
                    while (index < input.length() && Character.isDigit(input.charAt(index))) {
                        index++;
                    }
                }
                if (index < input.length() && (input.charAt(index) == 'e' || input.charAt(index) == 'E')) {
                    index++;
                    if (index < input.length() && (input.charAt(index) == '+' || input.charAt(index) == '-')) {
                        index++;
                    }
                    while (index < input.length() && Character.isDigit(input.charAt(index))) {
                        index++;
                    }
                }
                String number = input.substring(start, index);
                if (number.contains(".") || number.contains("e") || number.contains("E")) {
                    return Double.parseDouble(number);
                }
                try {
                    return Integer.parseInt(number);
                } catch (NumberFormatException ignored) {
                    return Long.parseLong(number);
                }
            }

            private Object readLiteral(String literal, Object value) {
                if (!input.startsWith(literal, index)) {
                    throw new BadRequestException("JSON 解析失败: 期望 " + literal);
                }
                index += literal.length();
                return value;
            }

            private void expect(char expected) {
                if (index >= input.length() || input.charAt(index) != expected) {
                    throw new BadRequestException("JSON 解析失败: 期望 " + expected + " 位于 " + index);
                }
                index++;
            }

            private boolean peek(char expected) {
                return index < input.length() && input.charAt(index) == expected;
            }

            private void skipWhitespace() {
                while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                    index++;
                }
            }
        }
    }
}
