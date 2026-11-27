package minhdoswe.tickgeek.module.email;

import org.junit.jupiter.api.Test;

import minhdoswe.tickgeek.support.TestProperties;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class CodeEmailComposerTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final CodeEmailComposer composer = new CodeEmailComposer(json, TestProperties.auth());

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

    /**
     * Proves the job reads the recipient from the stored payload, so the email goes to the
     * address the code was requested for.
     * <p>
     * Cases: payload for "ana@x.com" → "ana@x.com" (specs/002-sending-code/research.md R9).
     */
    @Test
    void recipientIsReadFromThePayload() {
        // Given: a stored payload for ana@x.com
        String stored = composer.toPayload(new CodeEmailPayload("ana@x.com", "042317"));

        // When / Then: the recipient is ana@x.com
        assertThat(composer.recipientOf(stored)).isEqualTo("ana@x.com");
    }

    /**
     * Proves every sender gets the same email text: the code and how long it is valid, in English.
     * <p>
     * Cases: payload with code "042317" and a 10-minute code-ttl → text naming "042317" and
     * "10 minutes" (specs/002-sending-code/spec.md FR-011).
     */
    @Test
    void textNamesTheCodeAndItsValidity() {
        // Given: a stored payload with code 042317
        String stored = composer.toPayload(new CodeEmailPayload("ana@x.com", "042317"));

        // When / Then: the text names the code and the 10-minute validity
        assertThat(composer.textOf(stored))
                .isEqualTo("Your TickGeek sign-in code is 042317. It is valid for 10 minutes.");
    }
}
