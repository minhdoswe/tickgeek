package minhdoswe.tickgeek.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties("tickgeek.email.brevo")
public record BrevoProperties(
        String baseUrl,
        String apiKey,
        String senderEmail,
        String senderName,
        Duration connectTimeout,
        Duration readTimeout) {
}
