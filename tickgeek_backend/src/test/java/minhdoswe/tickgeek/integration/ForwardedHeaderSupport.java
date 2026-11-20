package minhdoswe.tickgeek.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import minhdoswe.tickgeek.common.RequestSourceResolver;

/** Sends a real HTTP request through Tomcat, so its RemoteIpValve decides which IP to trust. */
final class ForwardedHeaderSupport {

    static final String SPOOFED_IP = "1.2.3.4";

    private ForwardedHeaderSupport() {
    }

    static int requestCodeWithForwardedFor(int port, String email) throws Exception {
        return send(request(port, email).header("X-Forwarded-For", SPOOFED_IP));
    }

    static int requestCodeAsFrontend(int port, String email, String clientIp, String secret) throws Exception {
        return send(request(port, email)
                .header(RequestSourceResolver.CLIENT_IP_HEADER, clientIp)
                .header(RequestSourceResolver.FRONTEND_SECRET_HEADER, secret));
    }

    private static HttpRequest.Builder request(int port, String email) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/code-requests"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"%s\"}".formatted(email)));
    }

    private static int send(HttpRequest.Builder request) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }
}
