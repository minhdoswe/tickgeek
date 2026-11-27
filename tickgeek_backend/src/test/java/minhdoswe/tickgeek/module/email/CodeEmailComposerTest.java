package minhdoswe.tickgeek.module.email;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class CodeEmailComposerTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final CodeEmailComposer composer = new CodeEmailComposer(json);

    /**
     * Proves the email payload saved in the outbox reads back unchanged, so the email sent later
     * carries exactly the address and code the request created.
     * <p>
     * Cases: payload → JSON → payload gives the same email and code
     * (specs/001-request-code/research.md R9).
     */
    @Test
    void payloadRoundTripsThroughJson() {
        // Given: a payload with an email and a code
        CodeEmailPayload payload = new CodeEmailPayload("ana@x.com", "042317");

        // When: it is turned into JSON
        String serialized = composer.toPayload(payload);

        // Then: reading the JSON back gives the same payload
        assertThat(json.readValue(serialized, CodeEmailPayload.class)).isEqualTo(payload);
    }
}
