package minhdoswe.tickgeek.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Sends a real HTTP request through Tomcat, so its RemoteIpValve decides which IP to trust. */
final class ForwardedHeaderSupport {

    static final String SPOOFED_IP = "1.2.3.4";

    private ForwardedHeaderSupport() {
    }

    static int requestCodeWithForwardedFor(int port, String email) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/code-requests"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", SPOOFED_IP)
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"%s\"}".formatted(email)))
                .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }
}
