package minhdoswe.tickgeek.module.email.emailsender.implement;

import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import minhdoswe.tickgeek.config.BrevoProperties;
import minhdoswe.tickgeek.module.email.CodeEmailComposer;
import minhdoswe.tickgeek.module.email.emailsender.EmailSendException;
import minhdoswe.tickgeek.module.email.emailsender.EmailSender;

/**
 * Sends the code email through Brevo's transactional email API, one call per {@code send}, with
 * the call's time limits set here (specs/002-sending-code/spec.md FR-002, FR-003). Runs in every
 * profile except {@code local}, and is created at startup despite lazy initialization, so a
 * missing API key or sender address stops the app instead of failing the first job (plan.md OQ2).
 * <p>
 * Failures become an {@link EmailSendException} holding only the HTTP status and Brevo's short
 * error code. The original exception is dropped on purpose: its message can hold Brevo's response
 * body, which may repeat the recipient address (contracts/email-job.md).
 */
@Component
@Profile("!local")
@Lazy(false)
public class BrevoEmailSender implements EmailSender {

    static final String PROVIDER = "brevo";
    static final String SEND_PATH = "/smtp/email";
    static final String API_KEY_HEADER = "api-key";

    /** Brevo error codes look like {@code invalid_parameter}; anything else is left out. */
    private static final Pattern ERROR_CODE = Pattern.compile("[a-z_]{1,64}");

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailSender.class);

    private final RestClient client;
    private final Contact sender;
    private final CodeEmailComposer composer;

    public BrevoEmailSender(RestClient.Builder builder, BrevoProperties properties, CodeEmailComposer composer) {
        this.client = builder.clone()
                .baseUrl(required("tickgeek.email.brevo.base-url", properties.baseUrl()))
                .defaultHeader(API_KEY_HEADER, required("tickgeek.email.brevo.api-key", properties.apiKey()))
                .requestFactory(timeLimited(properties))
                .build();
        this.sender = new Contact(properties.senderName(),
                required("tickgeek.email.brevo.sender-email", properties.senderEmail()));
        this.composer = composer;
    }

    @Override
    public void send(String payload, String to) {
        BrevoEmail email = new BrevoEmail(sender, List.of(new Recipient(to)), composer.subject(),
                composer.textOf(payload));
        try {
            BrevoAccepted accepted = client.post()
                    .uri(SEND_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(email)
                    .retrieve()
                    .body(BrevoAccepted.class);
            log.info("Code email accepted by provider {} with message id {}", PROVIDER,
                    accepted == null ? null : accepted.messageId());
        } catch (RestClientResponseException rejected) {
            throw new EmailSendException("Brevo responded " + rejected.getStatusCode().value() + errorCodeOf(rejected));
        } catch (RestClientException failed) {
            throw new EmailSendException("Brevo call failed: " + rootCauseName(failed));
        }
    }

    private static SimpleClientHttpRequestFactory timeLimited(BrevoProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    private static String errorCodeOf(RestClientResponseException rejected) {
        try {
            BrevoError error = rejected.getResponseBodyAs(BrevoError.class);
            boolean known = error != null && error.code() != null && ERROR_CODE.matcher(error.code()).matches();
            return known ? " " + error.code() : "";
        } catch (RuntimeException unreadable) {
            return "";
        }
    }

    private static String rootCauseName(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }

    private static String required(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }

    record Contact(String name, String email) {
    }

    record Recipient(String email) {
    }

    record BrevoEmail(Contact sender, List<Recipient> to, String subject, String textContent) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BrevoAccepted(String messageId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BrevoError(String code) {
    }
}
