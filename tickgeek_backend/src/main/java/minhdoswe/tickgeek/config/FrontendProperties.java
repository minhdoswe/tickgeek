package minhdoswe.tickgeek.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tickgeek.frontend")
public record FrontendProperties(String secret) {

    public FrontendProperties {
        AuthProperties.requireStrongSecret("tickgeek.frontend.secret", secret);
    }
}
