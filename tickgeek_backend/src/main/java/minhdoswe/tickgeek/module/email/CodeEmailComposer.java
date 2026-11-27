package minhdoswe.tickgeek.module.email;

import org.springframework.stereotype.Component;

import minhdoswe.tickgeek.config.AuthProperties;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CodeEmailComposer {

    static final String SUBJECT = "Your TickGeek sign-in code";
    static final String TEXT = "Your TickGeek sign-in code is %s. It is valid for %d minutes.";

    private final JsonMapper json;
    private final AuthProperties authProperties;

    public CodeEmailComposer(JsonMapper json, AuthProperties authProperties) {
        this.json = json;
        this.authProperties = authProperties;
    }

    public String toPayload(CodeEmailPayload payload) {
        return json.writeValueAsString(payload);
    }

    public CodeEmailPayload fromPayload(String payload) {
        return json.readValue(payload, CodeEmailPayload.class);
    }

    /** The address a stored payload is sent to (specs/002-sending-code/research.md R9). */
    public String recipientOf(String payload) {
        return fromPayload(payload).to();
    }

    /** The subject every sender uses for the code email. */
    public String subject() {
        return SUBJECT;
    }

    /**
     * The email body every sender uses: the code and how long it is valid, in English
     * (specs/002-sending-code/spec.md FR-011).
     */
    public String textOf(String payload) {
        return TEXT.formatted(fromPayload(payload).code(), authProperties.codeValidMinutes());
    }
}
