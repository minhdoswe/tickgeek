package minhdoswe.tickgeek.module.email.emailsender.implement;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import minhdoswe.tickgeek.config.BrevoProperties;
import minhdoswe.tickgeek.module.email.CodeEmailComposer;
import minhdoswe.tickgeek.module.email.CodeEmailPayload;
import minhdoswe.tickgeek.module.email.emailsender.EmailSendException;
import minhdoswe.tickgeek.support.TestProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the Brevo sender against a local HTTP server that stands in for Brevo. */
class BrevoEmailSenderTest {

    private static final String API_KEY = "test-brevo-key";
    private static final String TO = "ana@x.com";
    private static final String CODE = "042317";
    private static final Duration SHORT_READ_TIMEOUT = Duration.ofMillis(200);

    private final JsonMapper json = JsonMapper.builder().build();
    private final CodeEmailComposer composer = new CodeEmailComposer(json, TestProperties.auth());
    private final String payload = composer.toPayload(new CodeEmailPayload(TO, CODE));
    private final AtomicReference<RecordedRequest> received = new AtomicReference<>();

    private HttpServer brevo;
    private volatile int status = 201;
    private volatile String responseBody = "{\"messageId\":\"<id@smtp-relay.mailin.fr>\"}";
    private volatile Duration delay = Duration.ZERO;

    record RecordedRequest(String method, String path, String apiKey, String body) {
    }

    @BeforeEach
    void startBrevo() throws IOException {
        brevo = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        brevo.createContext("/", this::answer);
        brevo.start();
    }

    @AfterEach
    void stopBrevo() {
        brevo.stop(0);
    }

    /**
     * Proves one send makes one Brevo call with the key, the sender, the recipient and the code
     * text, so Brevo can deliver the code email.
     * <p>
     * Cases: Brevo answers 201 → one POST to /v3/smtp/email with the api-key header and a body
     * holding sender, recipient, subject and text (specs/002-sending-code/spec.md FR-002, FR-003,
     * FR-011).
     */
    @Test
    void sendsOneCallToBrevo() {
        // Given: Brevo accepts emails
        BrevoEmailSender sender = senderWith(Duration.ofSeconds(3));

        // When: one code email is sent
        sender.send(payload, TO);

        // Then: Brevo got one POST to /v3/smtp/email with the key and the email
        RecordedRequest request = received.get();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v3/smtp/email");
        assertThat(request.apiKey()).isEqualTo(API_KEY);
        JsonNode body = json.readTree(request.body());
        assertThat(body.at("/sender/email").asString()).isEqualTo("no-reply@tickgeek.test");
        assertThat(body.at("/sender/name").asString()).isEqualTo("TickGeek");
        assertThat(body.at("/to/0/email").asString()).isEqualTo(TO);
        assertThat(body.at("/subject").asString()).isEqualTo(composer.subject());
        assertThat(body.at("/textContent").asString()).isEqualTo(composer.textOf(payload));
    }

    /**
     * Proves a refused email is a failed attempt whose error names the status and Brevo's code but
     * never the address or the code, because that error is stored with the job.
     * <p>
     * Cases: 400 with {"code":"invalid_parameter","message":"... ana@x.com ..."} → EmailSendException
     * "Brevo responded 400 invalid_parameter" (contracts/email-job.md; spec.md FR-008).
     */
    @Test
    void aRefusalFailsWithoutLeakingTheEmail() {
        // Given: Brevo refuses, repeating the address in its message
        status = 400;
        responseBody = "{\"code\":\"invalid_parameter\",\"message\":\"email " + TO + " is not valid\"}";
        BrevoEmailSender sender = senderWith(Duration.ofSeconds(3));

        // When / Then: the attempt fails with only the status and Brevo's code
        assertThatThrownBy(() -> sender.send(payload, TO))
                .isInstanceOf(EmailSendException.class)
                .hasMessage("Brevo responded 400 invalid_parameter")
                .hasNoCause();
    }

    /**
     * Proves a Brevo outage is a failed attempt, so the job retries it.
     * <p>
     * Cases: 500 with a non-JSON body → EmailSendException "Brevo responded 500"
     * (specs/002-sending-code/research.md R6).
     */
    @Test
    void aServerErrorFails() {
        // Given: Brevo has an outage
        status = 500;
        responseBody = "<html>oops</html>";
        BrevoEmailSender sender = senderWith(Duration.ofSeconds(3));

        // When / Then: the attempt fails with the status only
        assertThatThrownBy(() -> sender.send(payload, TO))
                .isInstanceOf(EmailSendException.class)
                .hasMessage("Brevo responded 500");
    }

    /**
     * Proves a Brevo that does not answer in time is a failed attempt, so one slow call cannot hold
     * the job.
     * <p>
     * Cases: Brevo answers after 1 s, read timeout 200 ms → EmailSendException naming the timeout,
     * without the address (specs/002-sending-code/spec.md FR-003, edge case "Brevo does not answer").
     */
    @Test
    void aSlowAnswerTimesOut() {
        // Given: Brevo answers after 1 s and the read timeout is 200 ms
        delay = Duration.ofSeconds(1);
        BrevoEmailSender sender = senderWith(SHORT_READ_TIMEOUT);

        // When / Then: the attempt fails as a timeout
        assertThatThrownBy(() -> sender.send(payload, TO))
                .isInstanceOf(EmailSendException.class)
                .hasMessage("Brevo call failed: SocketTimeoutException")
                .hasNoCause();
    }

    /**
     * Proves a missing API key stops the sender from being created, so a deploy without the key
     * fails at startup instead of on the first code email.
     * <p>
     * Cases: blank api-key → IllegalStateException naming tickgeek.email.brevo.api-key
     * (specs/002-sending-code/plan.md OQ2).
     */
    @Test
    void aMissingApiKeyIsRejected() {
        // Given: settings without an API key
        BrevoProperties noKey = new BrevoProperties(baseUrl(), " ", "no-reply@tickgeek.test", "TickGeek",
                Duration.ofSeconds(2), Duration.ofSeconds(3));

        // When / Then: the sender cannot be created
        assertThatThrownBy(() -> new BrevoEmailSender(RestClient.builder(), noKey, composer))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("tickgeek.email.brevo.api-key must be set");
    }

    private BrevoEmailSender senderWith(Duration readTimeout) {
        BrevoProperties properties = new BrevoProperties(baseUrl(), API_KEY, "no-reply@tickgeek.test", "TickGeek",
                Duration.ofSeconds(2), readTimeout);
        return new BrevoEmailSender(RestClient.builder(), properties, composer);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + brevo.getAddress().getPort() + "/v3";
    }

    private void answer(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        received.set(new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst(BrevoEmailSender.API_KEY_HEADER), body));
        pause();
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        } catch (IOException clientGone) {
            // The sender timed out and closed the connection; nothing left to answer.
        } finally {
            exchange.close();
        }
    }

    private void pause() {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }
}
