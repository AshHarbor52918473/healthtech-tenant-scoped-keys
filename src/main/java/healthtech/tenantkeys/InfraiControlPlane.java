package healthtech.tenantkeys;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small REST client for the two control-plane capabilities used by this example. */
public final class InfraiControlPlane {
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern ERROR_CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern STRING_FIELD = Pattern.compile("\\\"%s\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;

    public InfraiControlPlane(String baseUrl, String apiKey) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.baseUrl = withoutTrailingSlash(baseUrl);
        this.apiKey = apiKey;
    }

    public String createUser(String email, String name, String idempotencyKey) throws IOException, InterruptedException {
        String body = "{\"email\":\"" + json(email) + "\",\"name\":\"" + json(name)
                + "\",\"idempotency_key\":\"" + json(idempotencyKey) + "\"}";
        return requiredString(post("/v1/auth/user/create", body), "user_id");
    }

    public IssuedKey createScopedKey(String name, String scopesJson, String idempotencyKey)
            throws IOException, InterruptedException {
        String body = "{\"name\":\"" + json(name) + "\",\"scopes\":" + scopesJson
                + ",\"idempotency_key\":\"" + json(idempotencyKey) + "\"}";
        Envelope response = post("/v1/account/keys/create", body);
        return new IssuedKey(requiredString(response, "id"), requiredString(response, "key"));
    }

    public void deleteUser(String userId) throws IOException, InterruptedException {
        delete("/v1/auth/user/delete/" + userId);
    }

    public void revokeKey(String keyId) throws IOException, InterruptedException {
        delete("/v1/account/keys/revoke/" + keyId);
    }

    private Envelope post(String path, String body) throws IOException, InterruptedException {
        return send(path, "POST", body);
    }

    private Envelope delete(String path) throws IOException, InterruptedException {
        return send(path, "DELETE", null);
    }

    private Envelope send(String path, String method, String body) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json");
            HttpRequest request = body == null
                    ? builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
                    : builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Envelope envelope = Envelope.parse(response.body()); // Decode the envelope before inspecting status.
            if (response.statusCode() == 429 && attempt < 2) {
                pause(response.headers().map(), attempt);
                continue;
            }
            if (!envelope.ok()) {
                throw new InfraiRejected(envelope.errorCode(), response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new IOException("Control-plane transport response: " + response.statusCode());
            }
            return envelope;
        }
        throw new IOException("Rate limit retry budget exhausted");
    }

    private static void pause(Map<String, java.util.List<String>> headers, int attempt) throws InterruptedException {
        String retryAfter = headers.getOrDefault("retry-after", java.util.List.of()).stream().findFirst().orElse("");
        long milliseconds;
        try {
            milliseconds = Long.parseLong(retryAfter) * 1000L;
        } catch (NumberFormatException ignored) {
            milliseconds = 250L * (1L << attempt);
        }
        Thread.sleep(milliseconds);
    }

    private static String requiredString(Envelope envelope, String field) throws IOException {
        Matcher matcher = Pattern.compile(String.format(STRING_FIELD.pattern(), field)).matcher(envelope.data());
        if (!matcher.find()) throw new IOException("Missing response field: " + field);
        return matcher.group(1);
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String withoutTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record IssuedKey(String id, String plaintext) { }

    private record Envelope(boolean ok, String data, String errorCode, String metadata) {
        static Envelope parse(String body) throws IOException {
            Matcher ok = OK.matcher(body);
            if (!ok.find()) throw new IOException("Response did not contain an Infrai envelope");
            Matcher error = ERROR_CODE.matcher(body);
            return new Envelope(Boolean.parseBoolean(ok.group(1)), body, error.find() ? error.group(1) : "UNKNOWN", body);
        }
    }

    public static final class InfraiRejected extends IOException {
        public InfraiRejected(String code, int status) {
            super("Infrai request rejected: " + code + " (HTTP " + status + ")");
        }
    }

    public static String idempotencyKey() {
        return UUID.randomUUID().toString();
    }
}
