package minhdoswe.tickgeek.module.email;

import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

@Component
public class CodeEmailComposer {

    private final JsonMapper json;

    public CodeEmailComposer(JsonMapper json) {
        this.json = json;
    }

    public String toPayload(CodeEmailPayload payload) {
        return json.writeValueAsString(payload);
    }
}
